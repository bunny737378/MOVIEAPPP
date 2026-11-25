package com.xtsream.app.engine

import android.content.Context
import android.net.Uri
import android.util.Log
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.CloudStreamApp
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.UnsafeSSL
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.insecureApp
import com.lagradost.cloudstream3.network.clearSharedHttpCache
import com.lagradost.cloudstream3.network.initClient
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.PLUGIN_VERSION_NOT_SET
import com.lagradost.cloudstream3.plugins.PluginData
import com.lagradost.cloudstream3.plugins.PluginManager
import com.lagradost.cloudstream3.plugins.Repository
import com.lagradost.cloudstream3.plugins.RepositoryManager
import com.lagradost.cloudstream3.plugins.SitePlugin
import com.lagradost.cloudstream3.ui.settings.extensions.RepositoryData
import com.lagradost.cloudstream3.utils.AppUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

/**
 * Unique runtime representation of a loaded MainAPI.
 * Distinguishes duplicate provider names (e.g. AniDap from CNCVerse vs PhisherRepo vs Direct URL)
 * ONLY appending the source label when duplicate names actually exist.
 */
data class ProviderEntry(
    val key: String,
    val api: MainAPI,
    val sourceLabel: String,
    val displayName: String,
    val isDuplicateName: Boolean
)

sealed class AddSourceResult {
    data class RepositoryAdded(
        val repository: RepositoryData,
        val plugins: List<SitePlugin>,
        val addedChildRepos: Int = 0,
        val isMetaRepoSynced: Boolean = false
    ) : AddSourceResult()

    data class DirectPluginInstalled(
        val pluginName: String,
        val addedApis: List<String>,
        val addedRepos: Int,
        val wasUpdate: Boolean = false
    ) : AddSourceResult()
}

data class InstalledExtensionInfo(
    val pluginData: PluginData,
    val sourceLabel: String,
    val providedEntries: List<ProviderEntry>
)

object ExtensionController {
    private const val TAG = "ExtensionController"
    private const val PREF_ACTIVE_API_KEY = "active_provider_api_key_v2"
    private const val PREF_ACTIVE_API_LEGACY = "active_provider_api_name"
    private const val DIRECT_REPO_KEY = "direct_cs3_extensions"

    private val initMutex = Mutex()
    @Volatile
    private var isInitialized = false

    private val _isBooting = MutableStateFlow(true)
    val isBooting: StateFlow<Boolean> = _isBooting.asStateFlow()

    private val _repositories = MutableStateFlow<List<RepositoryData>>(emptyList())
    val repositories: StateFlow<List<RepositoryData>> = _repositories.asStateFlow()

    private val _repoPlugins = MutableStateFlow<Map<String, List<SitePlugin>>>(emptyMap())
    val repoPlugins: StateFlow<Map<String, List<SitePlugin>>> = _repoPlugins.asStateFlow()

    private val _loadingRepos = MutableStateFlow<Set<String>>(emptySet())
    val loadingRepos: StateFlow<Set<String>> = _loadingRepos.asStateFlow()

    private val _installedPlugins = MutableStateFlow<List<InstalledExtensionInfo>>(emptyList())
    val installedPlugins: StateFlow<List<InstalledExtensionInfo>> = _installedPlugins.asStateFlow()

    private val _loadedProviders = MutableStateFlow<List<ProviderEntry>>(emptyList())
    val loadedProviders: StateFlow<List<ProviderEntry>> = _loadedProviders.asStateFlow()

    private val _loadedApis = MutableStateFlow<List<MainAPI>>(emptyList())
    val loadedApis: StateFlow<List<MainAPI>> = _loadedApis.asStateFlow()

    private val _activeProvider = MutableStateFlow<ProviderEntry?>(null)
    val activeProvider: StateFlow<ProviderEntry?> = _activeProvider.asStateFlow()

    private val _activeApi = MutableStateFlow<MainAPI?>(null)
    val activeApi: StateFlow<MainAPI?> = _activeApi.asStateFlow()

    private val _installingKeys = MutableStateFlow<Set<String>>(emptySet())
    val installingKeys: StateFlow<Set<String>> = _installingKeys.asStateFlow()

    fun providerKeyOf(api: MainAPI): String = "${api.sourcePlugin ?: "builtin"}::${api.name}"

    suspend fun initialize(context: Context) = initMutex.withLock {
        if (isInitialized) {
            _isBooting.value = false
            return@withLock
        }
        _isBooting.value = true
        RepositoryManager.onRepositoriesChanged = {
            refreshRuntimeState()
        }
        withContext(Dispatchers.IO) {
            try {
                PluginManager.___DO_NOT_CALL_FROM_A_PLUGIN_loadAllOnlinePlugins(context.applicationContext)
                PluginManager.___DO_NOT_CALL_FROM_A_PLUGIN_loadAllLocalPlugins(context.applicationContext, false)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                Log.e(TAG, "Error loading saved extensions", t)
            }
            refreshRuntimeState()
            isInitialized = true
        }
        _isBooting.value = false
    }

    @OptIn(UnsafeSSL::class)
    fun reinitNetworkClients(context: Context) {
        val appCtx = context.applicationContext
        runCatching { app.initClient(appCtx) }
        runCatching { insecureApp.initClient(appCtx, ignoreSSL = true) }
    }

    private fun resolveSourceLabelForFile(
        filePath: String?,
        url: String?,
        repos: List<RepositoryData>
    ): String {
        if (filePath.isNullOrBlank()) return "Built-in"
        val parentFolderName = runCatching { File(filePath).parentFile?.name }.getOrNull().orEmpty()

        // Match against known repository directories
        val matchingRepo = repos.firstOrNull { repo ->
            PluginManager.getPluginSanitizedFileName(repo.url) == parentFolderName
        }
        if (matchingRepo != null) {
            return matchingRepo.name.trim().ifBlank { hostLabel(matchingRepo.url) }
        }

        // Direct .cs3 install
        if (parentFolderName == PluginManager.getPluginSanitizedFileName(DIRECT_REPO_KEY)) {
            val host = hostLabel(url)
            return if (host.isNotBlank()) "Direct · $host" else "Direct"
        }

        return if (parentFolderName.equals("plugins", ignoreCase = true)) "Local" else "Extension"
    }

    private fun hostLabel(url: String?): String {
        if (url.isNullOrBlank()) return ""
        val host = runCatching { Uri.parse(url).host }.getOrNull().orEmpty()
        return when {
            host.contains("githubusercontent", ignoreCase = true) || host.contains("github.com", ignoreCase = true) -> {
                // Extract GitHub repo owner/name for clear identification
                val segments = runCatching { Uri.parse(url).pathSegments }.getOrNull().orEmpty()
                if (segments.size >= 2) segments[0] else "GitHub"
            }
            host.isNotBlank() -> host.removePrefix("www.").substringBefore('.')
            else -> ""
        }
    }

    fun refreshRuntimeState() {
        val repos = RepositoryManager.getRepositories().toList()
        _repositories.value = repos

        val online = PluginManager.getPluginsOnline().toList()
        val local = PluginManager.getPluginsLocal().toList()
        val allPluginRecords = (online + local).distinctBy { it.filePath }
        val pluginByPath = allPluginRecords.associateBy { it.filePath }

        // Keep ALL distinct (sourcePlugin + api.name) providers — do NOT drop duplicate names from different repos!
        val allApis = APIHolder.allProviders.withLock {
            APIHolder.allProviders.toList()
        }.distinctBy { providerKeyOf(it) }

        val nameCounts = allApis.groupingBy { it.name.lowercase() }.eachCount()

        // Disambiguate ONLY when two or more providers share the exact same name
        val rawEntries = allApis.map { api ->
            val key = providerKeyOf(api)
            val pData = api.sourcePlugin?.let { pluginByPath[it] }
            val sourceLabel = resolveSourceLabelForFile(api.sourcePlugin, pData?.url, repos)
            val isDup = (nameCounts[api.name.lowercase()] ?: 0) > 1
            Triple(key, api, sourceLabel to isDup)
        }

        // Ensure even two direct installs from the same host (different URLs) get numbered distinctly
        val labelDuplicateTracker = mutableMapOf<String, Int>()
        val entries = rawEntries.map { (key, api, meta) ->
            val (baseSource, isDup) = meta
            val displayName = if (!isDup) {
                api.name
            } else {
                val comboKey = "${api.name.lowercase()}::${baseSource.lowercase()}"
                val seen = (labelDuplicateTracker[comboKey] ?: 0) + 1
                labelDuplicateTracker[comboKey] = seen
                val suffix = if (seen > 1) "$baseSource #$seen" else baseSource
                "${api.name} ($suffix)"
            }
            ProviderEntry(
                key = key,
                api = api,
                sourceLabel = baseSource,
                displayName = displayName,
                isDuplicateName = isDup
            )
        }.sortedBy { it.displayName.lowercase() }

        _loadedProviders.value = entries
        _loadedApis.value = entries.map { it.api }

        val installedList = allPluginRecords.map { pData ->
            val provided = entries.filter { it.api.sourcePlugin == pData.filePath }
            val sourceLabel = resolveSourceLabelForFile(pData.filePath, pData.url, repos)
            InstalledExtensionInfo(pData, sourceLabel, provided)
        }
        _installedPlugins.value = installedList

        val currentActiveKey = _activeProvider.value?.key
        val savedKey = CloudStreamApp.getKey<String>(PREF_ACTIVE_API_KEY)
        val savedLegacyName = CloudStreamApp.getKey<String>(PREF_ACTIVE_API_LEGACY)

        val resolved = when {
            currentActiveKey != null && entries.any { it.key == currentActiveKey } ->
                entries.first { it.key == currentActiveKey }
            !savedKey.isNullOrBlank() && entries.any { it.key == savedKey } ->
                entries.first { it.key == savedKey }
            !savedLegacyName.isNullOrBlank() && entries.any { it.api.name == savedLegacyName } ->
                entries.first { it.api.name == savedLegacyName }
            else -> entries.firstOrNull { it.api.hasMainPage } ?: entries.firstOrNull()
        }
        _activeProvider.value = resolved
        _activeApi.value = resolved?.api
    }

    fun selectProvider(entry: ProviderEntry) {
        _activeProvider.value = entry
        _activeApi.value = entry.api
        CloudStreamApp.setKey(PREF_ACTIVE_API_KEY, entry.key)
        CloudStreamApp.setKey(PREF_ACTIVE_API_LEGACY, entry.api.name)
    }

    fun selectProvider(api: MainAPI) {
        val key = providerKeyOf(api)
        val entry = _loadedProviders.value.firstOrNull { it.key == key }
            ?: _loadedProviders.value.firstOrNull { it.api === api }
            ?: _loadedProviders.value.firstOrNull { it.api.name == api.name }
        if (entry != null) {
            selectProvider(entry)
        } else {
            _activeApi.value = api
            CloudStreamApp.setKey(PREF_ACTIVE_API_KEY, key)
            CloudStreamApp.setKey(PREF_ACTIVE_API_LEGACY, api.name)
        }
    }

    fun getProviderByKey(key: String?): ProviderEntry? {
        if (key.isNullOrBlank()) return null
        return _loadedProviders.value.firstOrNull { it.key == key }
    }

    fun resolveProvider(apiKey: String?, apiName: String?): ProviderEntry? {
        if (!apiKey.isNullOrBlank()) {
            _loadedProviders.value.firstOrNull { it.key == apiKey }?.let { return it }
        }
        if (!apiName.isNullOrBlank()) {
            _loadedProviders.value.firstOrNull { it.api.name.equals(apiName, ignoreCase = true) }?.let { return it }
        }
        return _activeProvider.value
    }

    fun resolveApi(apiKey: String?, apiName: String?): MainAPI? {
        return resolveProvider(apiKey, apiName)?.api
            ?: apiName?.let { APIHolder.getApiFromNameNull(it) }
            ?: _activeApi.value
    }

    fun getApiByName(name: String): MainAPI? {
        return _loadedProviders.value.firstOrNull { it.api.name.equals(name, ignoreCase = true) }?.api
            ?: APIHolder.getApiFromNameNull(name)
    }

    private fun normalizePluginUrl(url: String?): String {
        if (url.isNullOrBlank()) return ""
        return normalizeGithubBlobUrl(url.trim()).trimEnd('/')
    }

    fun isPluginInstalled(repoUrl: String, sitePlugin: SitePlugin): Boolean {
        val ctx = CloudStreamApp.context ?: return false
        val targetPath = PluginManager.getPluginPath(ctx, sitePlugin.internalName, repoUrl).absolutePath
        val normTargetUrl = normalizePluginUrl(sitePlugin.url)
        return _installedPlugins.value.any { info ->
            info.pluginData.filePath == targetPath ||
                (normTargetUrl.isNotBlank() && normalizePluginUrl(info.pluginData.url) == normTargetUrl)
        }
    }

    fun installedVersion(repoUrl: String, sitePlugin: SitePlugin): Int? {
        val ctx = CloudStreamApp.context ?: return null
        val targetPath = PluginManager.getPluginPath(ctx, sitePlugin.internalName, repoUrl).absolutePath
        val normTargetUrl = normalizePluginUrl(sitePlugin.url)
        return _installedPlugins.value.firstOrNull { info ->
            info.pluginData.filePath == targetPath ||
                (normTargetUrl.isNotBlank() && normalizePluginUrl(info.pluginData.url) == normTargetUrl)
        }?.pluginData?.version
    }

    /**
     * Universal CloudStream source resolver supporting ALL 3 provider/repository types:
     * 1. Type 1 — Meta-Repos containing nested repositories (e.g. `megarepo` / `MegaProvider`,
     *    `includedRepositories`, `repos-db.json`, or nested `repo.json` inside `pluginLists`)
     * 2. Type 2 — Standard Repositories containing providers (e.g. CNCVerse, PhisherRepo, Hexated, plugins.json)
     * 3. Type 3 — Direct `.cs3` extension URLs (with smart URL-based update vs separate-name coexistence!)
     */
    suspend fun addSourceFromInput(context: Context, rawInput: String): Result<AddSourceResult> =
        withContext(Dispatchers.IO) {
            val trimmed = rawInput.trim()
            if (trimmed.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Enter a repo shortname, repo URL, or .cs3 link"))
            }

            val normalizedInput = normalizeGithubBlobUrl(trimmed)

            val resolvedUrl = RepositoryManager.parseRepoUrl(normalizedInput)
                ?: return@withContext Result.failure(
                    IllegalArgumentException("Could not resolve \"$trimmed\". Check the shortname or URL.")
                )

            val finalUrl = normalizeGithubBlobUrl(resolvedUrl)

            if (looksLikeCs3Url(finalUrl)) {
                return@withContext installDirectCs3Url(context, finalUrl)
            }

            fetchAndRegisterSource(context, finalUrl)
        }

    private fun normalizeGithubBlobUrl(url: String): String {
        val ghBlob = Regex("^https?://github\\.com/([^/]+)/([^/]+)/blob/(.+)$")
        val match = ghBlob.matchEntire(url) ?: return url
        val (user, repo, path) = match.destructured
        return "https://raw.githubusercontent.com/$user/$repo/$path"
    }

    private fun looksLikeCs3Url(url: String): Boolean {
        val cleanPath = runCatching { Uri.parse(url).path }.getOrNull() ?: url.substringBefore('?')
        return cleanPath.endsWith(".cs3", ignoreCase = true) || cleanPath.endsWith(".zip", ignoreCase = true)
    }

    /**
     * Installs or updates a direct `.cs3` extension URL:
     * - If an already-installed extension (from a repo OR previous direct URL) has the EXACT SAME URL,
     *   update that existing extension in-place (no duplicate created!).
     * - If the URL is different (even if the plugin name is identical to an installed one),
     *   install it to a URL-unique file path so both coexist cleanly!
     */
    private suspend fun installDirectCs3Url(context: Context, cs3Url: String): Result<AddSourceResult> =
        withContext(Dispatchers.IO) {
            try {
                val reposBefore = RepositoryManager.getRepositories().map { it.url }.toSet()
                val providerKeysBefore = _loadedProviders.value.map { it.key }.toSet()
                val normalizedUrl = normalizePluginUrl(cs3Url)

                // Check if any already-installed plugin came from this EXACT URL
                val existingByUrl = (PluginManager.getPluginsOnline().toList() + PluginManager.getPluginsLocal().toList())
                    .firstOrNull { normalizePluginUrl(it.url) == normalizedUrl }

                val rawName = runCatching { Uri.parse(cs3Url).lastPathSegment }.getOrNull()
                    ?.substringBefore('?')
                    ?.removeSuffix(".cs3")
                    ?.removeSuffix(".zip")
                    ?.takeIf { it.isNotBlank() }
                    ?: "Extension_${cs3Url.hashCode().toUInt()}"

                val wasUpdate: Boolean
                val targetFile: File
                if (existingByUrl != null) {
                    // Same URL -> update existing repo/direct plugin in-place!
                    wasUpdate = true
                    targetFile = File(existingByUrl.filePath)
                } else {
                    // Different URL -> guarantee unique filename so same-name providers never overwrite each other!
                    wasUpdate = false
                    val uniqueInternal = "${rawName}_${normalizedUrl.hashCode().toUInt()}"
                    targetFile = PluginManager.getPluginPath(context.applicationContext, uniqueInternal, DIRECT_REPO_KEY)
                }

                PluginManager.unloadPlugin(targetFile.absolutePath)

                val downloaded = RepositoryManager.downloadPluginToFile(
                    context = context.applicationContext,
                    pluginUrl = cs3Url,
                    file = targetFile,
                    expectedFileHash = null
                ) ?: return@withContext Result.failure(IllegalStateException("Failed to download .cs3 from $cs3Url"))

                val manifest = readCs3Manifest(downloaded)
                    ?: run {
                        downloaded.delete()
                        return@withContext Result.failure(
                            IllegalStateException("File is not a valid CloudStream .cs3 extension (missing manifest.json)")
                        )
                    }

                val pluginName = manifest.name?.takeIf { it.isNotBlank() } ?: rawName
                val pluginVersion = manifest.version ?: PLUGIN_VERSION_NOT_SET

                val loaded = PluginManager.loadPlugin(
                    context = context.applicationContext,
                    file = downloaded,
                    data = PluginData(
                        internalName = existingByUrl?.internalName ?: pluginName,
                        url = cs3Url,
                        isOnline = true,
                        filePath = downloaded.absolutePath,
                        version = pluginVersion
                    )
                )

                if (!loaded) {
                    return@withContext Result.failure(IllegalStateException("Downloaded \"$pluginName\" but failed to load .cs3"))
                }

                // Wait for async meta-repo plugins (e.g. MegaProvider.cs3) to populate child repos
                waitForAsyncRepoSync(reposBefore.size)
                refreshRuntimeState()

                val reposAfter = RepositoryManager.getRepositories().map { it.url }.toSet()
                val newlyAddedRepos = (reposAfter - reposBefore).size
                val newlyAddedEntries = _loadedProviders.value.filter { it.key !in providerKeysBefore }

                if (_activeProvider.value == null && newlyAddedEntries.isNotEmpty()) {
                    selectProvider(newlyAddedEntries.first())
                } else if (newlyAddedEntries.isNotEmpty() && _loadedProviders.value.size == newlyAddedEntries.size) {
                    selectProvider(newlyAddedEntries.first())
                }

                Result.success(
                    AddSourceResult.DirectPluginInstalled(
                        pluginName = pluginName,
                        addedApis = newlyAddedEntries.map { it.displayName },
                        addedRepos = newlyAddedRepos,
                        wasUpdate = wasUpdate
                    )
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                Log.e(TAG, "Direct .cs3 install failed for $cs3Url", t)
                Result.failure(t)
            }
        }

    /**
     * Gives async meta-repo plugins like MegaProvider a clean window to register child repositories
     * without blocking regular provider installs unnecessarily.
     */
    private suspend fun waitForAsyncRepoSync(initialRepoCount: Int) {
        delay(500)
        var lastCount = RepositoryManager.getRepositories().size
        if (lastCount == initialRepoCount) {
            // Check one more time for slower network responses on meta-plugins
            delay(1200)
            lastCount = RepositoryManager.getRepositories().size
        }
        if (lastCount > initialRepoCount) {
            // Child repos started streaming in — wait up to ~5s until they settle
            repeat(8) {
                delay(650)
                val nowCount = RepositoryManager.getRepositories().size
                if (nowCount == lastCount) return
                lastCount = nowCount
            }
        }
    }

    private fun readCs3Manifest(file: File): BasePlugin.Manifest? {
        return runCatching {
            ZipFile(file).use { zip ->
                val entry = zip.getEntry("manifest.json") ?: return@use null
                zip.getInputStream(entry).bufferedReader().use { reader ->
                    AppUtils.parseJson<BasePlugin.Manifest>(reader.readText())
                }
            }
        }.getOrNull()
    }

    private fun isMetaRepoLoaderPlugin(plugin: SitePlugin): Boolean {
        val desc = plugin.description.orEmpty()
        val name = "${plugin.name} ${plugin.internalName}"
        return desc.contains("adds all repositories", ignoreCase = true) ||
            desc.contains("add all repositories", ignoreCase = true) ||
            name.contains("MegaProvider", ignoreCase = true) ||
            name.contains("MegaRepo", ignoreCase = true)
    }

    private suspend fun fetchAndRegisterSource(context: Context, repoUrl: String): Result<AddSourceResult> =
        withContext(Dispatchers.IO) {
            _loadingRepos.update { it + repoUrl }
            try {
                val fetchUrl = RepositoryManager.convertRawGitUrl(repoUrl)
                val response = app.get(
                    url = fetchUrl,
                    cacheTime = 5,
                    cacheUnit = TimeUnit.MINUTES
                )

                val finalResolvedUrl = response.okhttpResponse.request.url.toString()
                val contentType = response.headers["Content-Type"].orEmpty()

                val bytes = response.okhttpResponse.body.bytes()
                val isZipMagic = bytes.size >= 4 &&
                    bytes[0] == 0x50.toByte() &&
                    bytes[1] == 0x4B.toByte() &&
                    bytes[2] == 0x03.toByte() &&
                    bytes[3] == 0x04.toByte()

                if (isZipMagic || looksLikeCs3Url(finalResolvedUrl) || contentType.contains("zip", ignoreCase = true)) {
                    _loadingRepos.update { it - repoUrl }
                    return@withContext installDirectCs3Url(context, finalResolvedUrl)
                }

                val rawText = bytes.toString(Charsets.UTF_8).trim()

                // Case A: JSON Array -> could be direct plugins.json OR a repos-db.json (Type 1 meta-repo list!)
                if (rawText.startsWith("[")) {
                    // Check if it's a repos-db.json array (entries have "url" and NO "internalName"/"apiVersion")
                    val isReposDb = runCatching {
                        val arr = JSONArray(rawText)
                        if (arr.length() > 0) {
                            val first = arr.optJSONObject(0)
                            first != null && first.has("url") && !first.has("internalName") && !first.has("apiVersion")
                        } else false
                    }.getOrDefault(false)

                    if (isReposDb) {
                        val addedRepos = importReposDbArray(rawText)
                        refreshRuntimeState()
                        val placeholder = RepositoryData(
                            iconUrl = null,
                            name = "Mega Repository Index",
                            url = repoUrl,
                            description = "Imported $addedRepos repositories"
                        )
                        return@withContext Result.success(
                            AddSourceResult.RepositoryAdded(
                                repository = placeholder,
                                plugins = emptyList(),
                                addedChildRepos = addedRepos,
                                isMetaRepoSynced = true
                            )
                        )
                    }

                    val directPlugins = runCatching {
                        AppUtils.parseJson<Array<SitePlugin>>(rawText).toList()
                    }.getOrNull() ?: return@withContext Result.failure(
                        IllegalStateException("Invalid plugins list JSON at $repoUrl")
                    )

                    val hostName = runCatching { Uri.parse(repoUrl).host }.getOrNull()
                        ?.removePrefix("raw.")
                        ?: "Plugins List"
                    val repoData = RepositoryData(
                        iconUrl = directPlugins.firstOrNull { !it.iconUrl.isNullOrBlank() }?.iconUrl,
                        name = hostName,
                        url = repoUrl,
                        description = "Direct plugins list (${directPlugins.size} extensions)"
                    )
                    RepositoryManager.addRepository(repoData)
                    val unique = directPlugins.distinctBy { "${it.internalName}_${it.url}" }
                    _repoPlugins.update { it + (repoUrl to unique) }
                    refreshRuntimeState()
                    return@withContext Result.success(AddSourceResult.RepositoryAdded(repoData, unique, 0))
                }

                // Case B: Standard or Meta CloudStream repo.json (JSON Object)
                val parsedRepo = runCatching {
                    AppUtils.parseJson<Repository>(rawText)
                }.getOrNull() ?: return@withContext Result.failure(
                    IllegalStateException("Unsupported link format. Provide a CloudStream repo shortname, repo.json, plugins.json, or .cs3 URL.")
                )

                val repoData = RepositoryData(
                    iconUrl = parsedRepo.iconUrl,
                    name = parsedRepo.name.ifBlank { repoUrl.substringAfterLast('/') },
                    url = repoUrl,
                    description = parsedRepo.description
                )
                RepositoryManager.addRepository(repoData)

                val reposBefore = RepositoryManager.getRepositories().map { it.url }.toSet()
                var addedChildren = 0

                // Type 1A: includedRepositories inside repo.json
                runCatching {
                    val jsonObj = JSONObject(rawText)
                    val included = jsonObj.optJSONArray("includedRepositories")
                    if (included != null) {
                        val urls = (0 until included.length())
                            .mapNotNull { included.optString(it, "").trim().takeIf { u -> u.isNotBlank() } }
                        addedChildren += importChildRepoUrls(urls)
                    }
                }

                // Type 1B: nested repo.json entries inside pluginLists + standard plugins
                val (plugins, nestedChildRepos) = fetchPluginsAndNestedRepos(repoData, parsedRepo)
                addedChildren += nestedChildRepos
                _repoPlugins.update { it + (repoUrl to plugins) }

                // Type 1C: Meta-repo containing a repo-loader plugin (e.g. megarepo -> MegaProvider.cs3)!
                // Automatically install & execute the loader plugin so CNCVerse, PhisherRepo, etc. appear right away!
                var metaSynced = addedChildren > 0
                val metaLoader = if (plugins.size <= 2) plugins.firstOrNull { isMetaRepoLoaderPlugin(it) } else null
                if (metaLoader != null) {
                    val installRes = installPlugin(context, repoUrl, metaLoader)
                    if (installRes.isSuccess) {
                        metaSynced = true
                    }
                }

                refreshRuntimeState()
                val reposAfter = RepositoryManager.getRepositories().map { it.url }.toSet()
                val totalChildAdded = maxOf(addedChildren, (reposAfter - reposBefore).size)

                Result.success(
                    AddSourceResult.RepositoryAdded(
                        repository = repoData,
                        plugins = plugins,
                        addedChildRepos = totalChildAdded,
                        isMetaRepoSynced = metaSynced
                    )
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                Log.e(TAG, "Failed adding source $repoUrl", t)
                Result.failure(t)
            } finally {
                _loadingRepos.update { it - repoUrl }
            }
        }

    private suspend fun importReposDbArray(rawJsonArray: String): Int {
        val urls = runCatching {
            val arr = JSONArray(rawJsonArray)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val u = obj.optString("url", "").trim()
                    if (u.isNotBlank()) add(u)
                }
            }
        }.getOrDefault(emptyList())
        return importChildRepoUrls(urls)
    }

    private suspend fun importChildRepoUrls(rawUrls: List<String>): Int {
        if (rawUrls.isEmpty()) return 0
        var added = 0
        for (chunk in rawUrls.distinct().chunked(6)) {
            val count = coroutineScope {
                chunk.map { childUrl ->
                    async(Dispatchers.IO) {
                        try {
                            val resolvedChild = RepositoryManager.parseRepoUrl(normalizeGithubBlobUrl(childUrl))
                                ?: normalizeGithubBlobUrl(childUrl)
                            val childRepo = RepositoryManager.parseRepository(resolvedChild) ?: return@async 0
                            RepositoryManager.addRepository(
                                RepositoryData(
                                    iconUrl = childRepo.iconUrl,
                                    name = childRepo.name.ifBlank { hostLabel(resolvedChild) },
                                    url = resolvedChild,
                                    description = childRepo.description
                                )
                            )
                            1
                        } catch (ce: CancellationException) {
                            throw ce
                        } catch (_: Throwable) {
                            0
                        }
                    }
                }.awaitAll().sum()
            }
            added += count
        }
        return added
    }

    suspend fun loadRepositoryPlugins(repoData: RepositoryData, force: Boolean = false): Result<List<SitePlugin>> =
        withContext(Dispatchers.IO) {
            if (!force && _repoPlugins.value.containsKey(repoData.url)) {
                return@withContext Result.success(_repoPlugins.value[repoData.url].orEmpty())
            }
            _loadingRepos.update { it + repoData.url }
            try {
                val rawText = app.get(
                    url = RepositoryManager.convertRawGitUrl(repoData.url),
                    cacheTime = if (force) 0 else 5,
                    cacheUnit = TimeUnit.MINUTES
                ).text.trim()

                if (rawText.startsWith("[")) {
                    val plugins = AppUtils.parseJson<Array<SitePlugin>>(rawText)
                        .distinctBy { "${it.internalName}_${it.url}" }
                    _repoPlugins.update { it + (repoData.url to plugins) }
                    refreshRuntimeState()
                    return@withContext Result.success(plugins)
                }

                val parsedRepo = runCatching { AppUtils.parseJson<Repository>(rawText) }.getOrNull()
                    ?: return@withContext Result.failure(IllegalStateException("Unable to load repository ${repoData.name}"))

                val updatedMeta = repoData.copy(
                    iconUrl = parsedRepo.iconUrl ?: repoData.iconUrl,
                    name = parsedRepo.name.ifBlank { repoData.name },
                    description = parsedRepo.description ?: repoData.description
                )
                RepositoryManager.addRepository(updatedMeta)

                val (plugins, _) = fetchPluginsAndNestedRepos(updatedMeta, parsedRepo)
                _repoPlugins.update { it + (repoData.url to plugins) }
                refreshRuntimeState()
                Result.success(plugins)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                Log.e(TAG, "Failed refreshing repository ${repoData.url}", t)
                Result.failure(t)
            } finally {
                _loadingRepos.update { it - repoData.url }
            }
        }

    private suspend fun fetchPluginsAndNestedRepos(
        repoData: RepositoryData,
        repo: Repository
    ): Pair<List<SitePlugin>, Int> {
        val list = mutableListOf<SitePlugin>()
        var nestedReposAdded = 0
        for (pluginListUrl in repo.pluginLists.take(25)) {
            try {
                val respText = app.get(
                    url = RepositoryManager.convertRawGitUrl(pluginListUrl),
                    cacheTime = 5,
                    cacheUnit = TimeUnit.MINUTES
                ).text.trim()

                if (respText.startsWith("[")) {
                    val parsed = AppUtils.parseJson<Array<SitePlugin>>(respText)
                    list.addAll(parsed)
                } else if (respText.startsWith("{")) {
                    val nestedRepo = runCatching { AppUtils.parseJson<Repository>(respText) }.getOrNull()
                    if (nestedRepo != null && nestedRepo.pluginLists.isNotEmpty()) {
                        RepositoryManager.addRepository(
                            RepositoryData(
                                iconUrl = nestedRepo.iconUrl,
                                name = nestedRepo.name.ifBlank { hostLabel(pluginListUrl) },
                                url = pluginListUrl,
                                description = nestedRepo.description
                            )
                        )
                        nestedReposAdded++
                        for (subUrl in nestedRepo.pluginLists.take(8)) {
                            runCatching {
                                val subPlugins = app.get(
                                    url = RepositoryManager.convertRawGitUrl(subUrl),
                                    cacheTime = 5,
                                    cacheUnit = TimeUnit.MINUTES
                                ).parsed<Array<SitePlugin>>()
                                list.addAll(subPlugins)
                            }
                        }
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                Log.w(TAG, "Failed parsing pluginList $pluginListUrl for ${repoData.name}", t)
            }
        }
        return list.distinctBy { "${it.internalName}_${it.url}" } to nestedReposAdded
    }

    suspend fun installPlugin(
        context: Context,
        repoUrl: String,
        plugin: SitePlugin
    ): Result<String> = withContext(Dispatchers.IO) {
        val installKey = "${repoUrl}::${plugin.internalName}"
        _installingKeys.update { it + installKey }
        try {
            val reposBefore = RepositoryManager.getRepositories().map { it.url }.toSet()
            val providerKeysBefore = _loadedProviders.value.map { it.key }.toSet()

            // If this exact URL was already installed via direct link, migrate/update it cleanly without duplicating
            val normUrl = normalizePluginUrl(plugin.url)
            val existingSameUrlDirect = PluginManager.getPluginsOnline().firstOrNull {
                normalizePluginUrl(it.url) == normUrl &&
                    File(it.filePath).parentFile?.name == PluginManager.getPluginSanitizedFileName(DIRECT_REPO_KEY)
            }
            if (existingSameUrlDirect != null) {
                PluginManager.deletePlugin(File(existingSameUrlDirect.filePath))
            }

            val targetFile = PluginManager.getPluginPath(context.applicationContext, plugin.internalName, repoUrl)
            PluginManager.unloadPlugin(targetFile.absolutePath)

            val downloaded = RepositoryManager.downloadPluginToFile(
                context = context.applicationContext,
                pluginUrl = plugin.url,
                file = targetFile,
                expectedFileHash = plugin.fileHash
            ) ?: return@withContext Result.failure(IllegalStateException("Download failed for ${plugin.name}"))

            val loaded = PluginManager.loadPlugin(
                context = context.applicationContext,
                file = downloaded,
                data = PluginData(
                    internalName = plugin.internalName,
                    url = plugin.url,
                    isOnline = true,
                    filePath = downloaded.absolutePath,
                    version = if (plugin.version > 0) plugin.version else PLUGIN_VERSION_NOT_SET
                )
            )

            if (!loaded) {
                return@withContext Result.failure(IllegalStateException("Failed to load ${plugin.name} (.cs3)"))
            }

            if (isMetaRepoLoaderPlugin(plugin)) {
                waitForAsyncRepoSync(reposBefore.size)
            } else {
                delay(250)
            }
            refreshRuntimeState()

            val reposAfter = RepositoryManager.getRepositories().map { it.url }.toSet()
            val newlyAddedRepos = (reposAfter - reposBefore).size
            val newlyAddedEntries = _loadedProviders.value.filter { it.key !in providerKeysBefore }

            if (_activeProvider.value == null && newlyAddedEntries.isNotEmpty()) {
                selectProvider(newlyAddedEntries.first())
            } else if (newlyAddedEntries.isNotEmpty() && _loadedProviders.value.size == newlyAddedEntries.size) {
                selectProvider(newlyAddedEntries.first())
            }

            val message = when {
                newlyAddedRepos > 0 -> "${plugin.name} synced $newlyAddedRepos repositories"
                newlyAddedEntries.isNotEmpty() -> "Installed ${newlyAddedEntries.joinToString { it.displayName }}"
                else -> "Installed ${plugin.name}"
            }
            Result.success(message)
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Log.e(TAG, "Install failed for ${plugin.name}", t)
            Result.failure(t)
        } finally {
            _installingKeys.update { it - installKey }
        }
    }

    suspend fun uninstallPlugin(filePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            PluginManager.deletePlugin(File(filePath))
            refreshRuntimeState()
            Result.success(Unit)
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Log.e(TAG, "Uninstall failed for $filePath", t)
            Result.failure(t)
        }
    }

    suspend fun uninstallPlugin(context: Context, repoUrl: String, plugin: SitePlugin): Result<Unit> {
        val file = PluginManager.getPluginPath(context.applicationContext, plugin.internalName, repoUrl)
        return uninstallPlugin(file.absolutePath)
    }

    suspend fun removeRepository(context: Context, repoData: RepositoryData): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            RepositoryManager.removeRepository(context.applicationContext, repoData)
            _repoPlugins.update { it - repoData.url }
            refreshRuntimeState()
            Result.success(Unit)
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Log.e(TAG, "Remove repo failed for ${repoData.name}", t)
            Result.failure(t)
        }
    }

    fun clearHttpCache(context: Context): Long {
        val evicted = clearSharedHttpCache()
        if (evicted > 0L) return evicted
        val httpCacheDir = File(context.cacheDir, "http_cache")
        return if (httpCacheDir.exists()) {
            httpCacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        } else 0L
    }
}
