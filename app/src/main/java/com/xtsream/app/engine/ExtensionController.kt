package com.xtsream.app.engine

import android.content.Context
import android.util.Log
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.CloudStreamApp
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.network.initClient
import com.lagradost.cloudstream3.plugins.PLUGIN_VERSION_NOT_SET
import com.lagradost.cloudstream3.plugins.PluginData
import com.lagradost.cloudstream3.plugins.PluginManager
import com.lagradost.cloudstream3.plugins.Repository
import com.lagradost.cloudstream3.plugins.RepositoryManager
import com.lagradost.cloudstream3.plugins.SitePlugin
import com.lagradost.cloudstream3.ui.settings.extensions.RepositoryData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class RepoFetchResult(
    val repository: RepositoryData,
    val plugins: List<SitePlugin>,
    val addedChildRepos: Int = 0
)

data class InstalledExtensionInfo(
    val pluginData: PluginData,
    val providedApis: List<String>
)

object ExtensionController {
    private const val TAG = "ExtensionController"
    private const val PREF_ACTIVE_API = "active_provider_api_name"

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

    private val _loadedApis = MutableStateFlow<List<MainAPI>>(emptyList())
    val loadedApis: StateFlow<List<MainAPI>> = _loadedApis.asStateFlow()

    private val _activeApi = MutableStateFlow<MainAPI?>(null)
    val activeApi: StateFlow<MainAPI?> = _activeApi.asStateFlow()

    private val _installingKeys = MutableStateFlow<Set<String>>(emptySet())
    val installingKeys: StateFlow<Set<String>> = _installingKeys.asStateFlow()

    suspend fun initialize(context: Context) = initMutex.withLock {
        if (isInitialized) {
            _isBooting.value = false
            return@withLock
        }
        _isBooting.value = true
        withContext(Dispatchers.IO) {
            try {
                app.initClient(context.applicationContext)
                PluginManager.___DO_NOT_CALL_FROM_A_PLUGIN_loadAllOnlinePlugins(context.applicationContext)
                PluginManager.___DO_NOT_CALL_FROM_A_PLUGIN_loadAllLocalPlugins(context.applicationContext, false)
            } catch (t: Throwable) {
                Log.e(TAG, "Error loading saved extensions", t)
            }
            refreshRuntimeState()
            isInitialized = true
        }
        _isBooting.value = false
    }

    fun refreshRuntimeState() {
        val repos = RepositoryManager.getRepositories().toList()
        _repositories.value = repos

        val allApis = APIHolder.allProviders.withLock {
            APIHolder.allProviders.toList()
        }.distinctBy { it.name }.sortedBy { it.name.lowercase() }
        _loadedApis.value = allApis

        val online = PluginManager.getPluginsOnline().toList()
        val local = PluginManager.getPluginsLocal().toList()
        val installedList = (online + local).distinctBy { it.filePath }.map { pData ->
            val provided = allApis.filter { it.sourcePlugin == pData.filePath }.map { it.name }
            InstalledExtensionInfo(pData, provided)
        }
        _installedPlugins.value = installedList

        val currentActive = _activeApi.value
        val savedName = CloudStreamApp.getKey<String>(PREF_ACTIVE_API)
        val resolvedActive = when {
            currentActive != null && allApis.any { it.name == currentActive.name } ->
                allApis.first { it.name == currentActive.name }
            !savedName.isNullOrBlank() && allApis.any { it.name == savedName } ->
                allApis.first { it.name == savedName }
            else -> allApis.firstOrNull { it.hasMainPage } ?: allApis.firstOrNull()
        }
        _activeApi.value = resolvedActive
    }

    fun selectProvider(api: MainAPI) {
        _activeApi.value = api
        CloudStreamApp.setKey(PREF_ACTIVE_API, api.name)
    }

    fun getApiByName(name: String): MainAPI? {
        return _loadedApis.value.firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: APIHolder.getApiFromNameNull(name)
    }

    fun isPluginInstalled(repoUrl: String, sitePlugin: SitePlugin): Boolean {
        val ctx = CloudStreamApp.context ?: return false
        val targetPath = PluginManager.getPluginPath(ctx, sitePlugin.internalName, repoUrl).absolutePath
        return _installedPlugins.value.any {
            it.pluginData.filePath == targetPath ||
                (it.pluginData.internalName.equals(sitePlugin.internalName, ignoreCase = true) &&
                    it.pluginData.url == sitePlugin.url)
        }
    }

    fun installedVersion(repoUrl: String, sitePlugin: SitePlugin): Int? {
        val ctx = CloudStreamApp.context ?: return null
        val targetPath = PluginManager.getPluginPath(ctx, sitePlugin.internalName, repoUrl).absolutePath
        return _installedPlugins.value.firstOrNull {
            it.pluginData.filePath == targetPath || it.pluginData.url == sitePlugin.url
        }?.pluginData?.version
    }

    /**
     * Resolves a CloudStream repository shortname (e.g. "megarepo", "hexated", "phisherrepo", "!code")
     * or direct URL / cloudstreamrepo:// link, supports both standard plugin repos and multi-repo lists.
     */
    suspend fun addRepositoryFromInput(rawInput: String): Result<RepoFetchResult> = withContext(Dispatchers.IO) {
        val trimmed = rawInput.trim()
        if (trimmed.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Enter a repository shortname or URL"))
        }

        val resolvedUrl = RepositoryManager.parseRepoUrl(trimmed)
            ?: return@withContext Result.failure(
                IllegalArgumentException("Could not resolve \"$trimmed\". Check the shortname or URL.")
            )

        val result = fetchAndRegisterRepository(resolvedUrl)
        refreshRuntimeState()
        result
    }

    private suspend fun fetchAndRegisterRepository(repoUrl: String): Result<RepoFetchResult> = withContext(Dispatchers.IO) {
        _loadingRepos.update { it + repoUrl }
        try {
            val rawResponse = app.get(
                url = RepositoryManager.convertRawGitUrl(repoUrl),
                cacheTime = 5,
                cacheUnit = TimeUnit.MINUTES
            ).text

            val parsedRepo = runCatching {
                com.lagradost.cloudstream3.utils.AppUtils.parseJson<Repository>(rawResponse)
            }.getOrNull() ?: return@withContext Result.failure(
                IllegalStateException("Invalid CloudStream repository manifest at $repoUrl")
            )

            val repoData = RepositoryData(
                iconUrl = parsedRepo.iconUrl,
                name = parsedRepo.name.ifBlank { repoUrl.substringAfterLast('/') },
                url = repoUrl,
                description = parsedRepo.description
            )
            RepositoryManager.addRepository(repoData)

            // Support optional includedRepositories array in meta-repositories
            var addedChildren = 0
            runCatching {
                val jsonObj = JSONObject(rawResponse)
                val included = jsonObj.optJSONArray("includedRepositories")
                if (included != null) {
                    for (i in 0 until included.length()) {
                        val childUrl = included.optString(i, "").trim()
                        if (childUrl.isNotBlank()) {
                            val resolvedChild = RepositoryManager.parseRepoUrl(childUrl) ?: childUrl
                            val childRepo = RepositoryManager.parseRepository(resolvedChild)
                            if (childRepo != null) {
                                RepositoryManager.addRepository(
                                    RepositoryData(
                                        iconUrl = childRepo.iconUrl,
                                        name = childRepo.name,
                                        url = resolvedChild,
                                        description = childRepo.description
                                    )
                                )
                                addedChildren++
                            }
                        }
                    }
                }
            }

            val plugins = fetchPluginsForRepo(repoData, parsedRepo)
            _repoPlugins.update { it + (repoUrl to plugins) }
            Result.success(RepoFetchResult(repoData, plugins, addedChildren))
        } catch (t: Throwable) {
            Log.e(TAG, "Failed adding repository $repoUrl", t)
            Result.failure(t)
        } finally {
            _loadingRepos.update { it - repoUrl }
        }
    }

    suspend fun loadRepositoryPlugins(repoData: RepositoryData, force: Boolean = false): Result<List<SitePlugin>> =
        withContext(Dispatchers.IO) {
            if (!force && _repoPlugins.value.containsKey(repoData.url)) {
                return@withContext Result.success(_repoPlugins.value[repoData.url].orEmpty())
            }
            _loadingRepos.update { it + repoData.url }
            try {
                val parsedRepo = RepositoryManager.parseRepository(repoData.url)
                    ?: return@withContext Result.failure(IllegalStateException("Unable to load repository ${repoData.name}"))
                val updatedMeta = repoData.copy(
                    iconUrl = parsedRepo.iconUrl ?: repoData.iconUrl,
                    name = parsedRepo.name.ifBlank { repoData.name },
                    description = parsedRepo.description ?: repoData.description
                )
                RepositoryManager.addRepository(updatedMeta)

                val plugins = fetchPluginsForRepo(updatedMeta, parsedRepo)
                _repoPlugins.update { it + (repoData.url to plugins) }
                refreshRuntimeState()
                Result.success(plugins)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed refreshing repository ${repoData.url}", t)
                Result.failure(t)
            } finally {
                _loadingRepos.update { it - repoData.url }
            }
        }

    private suspend fun fetchPluginsForRepo(repoData: RepositoryData, repo: Repository): List<SitePlugin> {
        val list = mutableListOf<SitePlugin>()
        for (pluginListUrl in repo.pluginLists.take(15)) {
            try {
                val respText = app.get(
                    url = RepositoryManager.convertRawGitUrl(pluginListUrl),
                    cacheTime = 5,
                    cacheUnit = TimeUnit.MINUTES
                ).text.trim()

                // Handle nested repository references inside pluginLists as well as standard SitePlugin arrays
                if (respText.startsWith("[")) {
                    val parsed = com.lagradost.cloudstream3.utils.AppUtils.parseJson<Array<SitePlugin>>(respText)
                    list.addAll(parsed)
                } else if (respText.startsWith("{")) {
                    val nestedRepo = runCatching {
                        com.lagradost.cloudstream3.utils.AppUtils.parseJson<Repository>(respText)
                    }.getOrNull()
                    if (nestedRepo != null && nestedRepo.pluginLists.isNotEmpty()) {
                        RepositoryManager.addRepository(
                            RepositoryData(
                                iconUrl = nestedRepo.iconUrl,
                                name = nestedRepo.name,
                                url = pluginListUrl,
                                description = nestedRepo.description
                            )
                        )
                        for (subUrl in nestedRepo.pluginLists.take(5)) {
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
            } catch (t: Throwable) {
                Log.w(TAG, "Failed parsing pluginList $pluginListUrl for ${repoData.name}", t)
            }
        }
        return list.distinctBy { "${it.internalName}_${it.url}" }
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
            val apisBefore = _loadedApis.value.map { it.name }.toSet()

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

            refreshRuntimeState()

            val reposAfter = RepositoryManager.getRepositories().map { it.url }.toSet()
            val newlyAddedRepos = (reposAfter - reposBefore).size
            val newlyAddedApis = _loadedApis.value.filter { it.name !in apisBefore }

            // Auto-select newly installed provider if user had none active yet
            if (_activeApi.value == null && newlyAddedApis.isNotEmpty()) {
                selectProvider(newlyAddedApis.first())
            } else if (newlyAddedApis.isNotEmpty() && _loadedApis.value.size == newlyAddedApis.size) {
                selectProvider(newlyAddedApis.first())
            }

            val message = when {
                newlyAddedRepos > 0 -> "${plugin.name} installed & added $newlyAddedRepos repositories!"
                newlyAddedApis.isNotEmpty() -> "Installed ${plugin.name} (${newlyAddedApis.joinToString { it.name }})"
                else -> "Installed ${plugin.name}"
            }
            Result.success(message)
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
        } catch (t: Throwable) {
            Log.e(TAG, "Remove repo failed for ${repoData.name}", t)
            Result.failure(t)
        }
    }

    fun clearHttpCache(context: Context): Long {
        val httpCacheDir = File(context.cacheDir, "http_cache")
        val freed = if (httpCacheDir.exists()) {
            httpCacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        } else 0L
        runCatching { httpCacheDir.deleteRecursively() }
        return freed
    }
}
