package com.lagradost.cloudstream3.plugins

import android.app.Activity
import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.CommonActivity
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.extractorApis
import dalvik.system.PathClassLoader
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File
import java.util.concurrent.ConcurrentHashMap

const val PLUGINS_KEY = "PLUGINS_KEY"
const val PLUGINS_KEY_LOCAL = "PLUGINS_KEY_LOCAL"
const val PLUGIN_VERSION_NOT_SET = Int.MIN_VALUE

@Serializable
data class PluginData(
    @JsonProperty("internalName") @SerialName("internalName") val internalName: String,
    @JsonProperty("url") @SerialName("url") val url: String?,
    @JsonProperty("isOnline") @SerialName("isOnline") val isOnline: Boolean,
    @JsonProperty("filePath") @SerialName("filePath") val filePath: String,
    @JsonProperty("version") @SerialName("version") val version: Int,
    @JsonProperty("iconUrl") @SerialName("iconUrl") val iconUrl: String? = null,
)

/**
 * Hikari-grade CloudStream .cs3 extension loader:
 * - Non-fatal `setReadOnly()` for Android 14+ dex security
 * - Passes active Activity host context when available
 * - Snapshots APIHolder before/after load & repairs `sourcePlugin` stamps
 * - Falls back to reflection field scanning if a plugin stores MainAPI fields directly
 * - Injects `app` Activity field on MainAPI instances that expect it
 * - Records detailed root-cause diagnostics in `lastError`
 */
object PluginManager {
    private const val TAG = "PluginManager"
    private val dataLock = Mutex()
    private val loadLock = Mutex()
    val plugins = ConcurrentHashMap<String, BasePlugin>()
    val urlPlugins = ConcurrentHashMap<String, BasePlugin>()

    @Volatile
    var currentlyLoading: String? = null
        private set

    @Volatile
    var lastError: String? = null
        private set

    fun getPluginsOnline(): Array<PluginData> = getKey<Array<PluginData>>(PLUGINS_KEY) ?: emptyArray()
    fun getPluginsLocal(): Array<PluginData> = getKey<Array<PluginData>>(PLUGINS_KEY_LOCAL) ?: emptyArray()

    private suspend fun save(data: PluginData) = dataLock.withLock {
        val key = if (data.isOnline) PLUGINS_KEY else PLUGINS_KEY_LOCAL
        val current = if (data.isOnline) getPluginsOnline() else getPluginsLocal()
        setKey(key, (current.filter { it.filePath != data.filePath } + data).toTypedArray())
    }

    /** Keep the original filename mapping so already installed extensions survive this cleanup. */
    fun getPluginSanitizedFileName(name: String): String {
        val reserved = "|\\?*<\":>+[]/\'"
        var sanitized = name
        for (char in reserved) sanitized = sanitized.replace(char, ' ')
        sanitized = sanitized.replace(" ", "").replace("  ", " ").trim(' ')
        return "$sanitized.${name.hashCode()}"
    }

    fun getPluginPath(context: Context, internalName: String, repositoryUrl: String): File =
        File(
            context.filesDir,
            "${RepositoryManager.ONLINE_PLUGINS_FOLDER}/${getPluginSanitizedFileName(repositoryUrl)}/${getPluginSanitizedFileName(internalName)}.cs3"
        )

    suspend fun ___DO_NOT_CALL_FROM_A_PLUGIN_loadAllOnlinePlugins(context: Context) {
        for (data in getPluginsOnline()) {
            val file = File(data.filePath)
            if (file.isFile) loadPlugin(context, file, data)
        }
        MainActivity.afterPluginsLoadedEvent(false)
    }

    suspend fun ___DO_NOT_CALL_FROM_A_PLUGIN_loadAllLocalPlugins(context: Context, forceReload: Boolean) {
        val folder = File(context.getExternalFilesDir(null), "plugins").apply { mkdirs() }
        val records = getPluginsLocal().associateBy { it.filePath }
        val files = (records.keys.map(::File) + folder.listFiles().orEmpty())
            .distinctBy { it.absolutePath }
            .filter { it.isFile && it.extension in setOf("cs3", "zip") }
        for (file in files.sortedBy { it.name }) {
            if (forceReload) unloadPlugin(file.absolutePath)
            loadPlugin(
                context,
                file,
                records[file.absolutePath]
                    ?: PluginData(file.nameWithoutExtension, null, false, file.absolutePath, PLUGIN_VERSION_NOT_SET)
            )
        }
        MainActivity.afterPluginsLoadedEvent(true)
    }

    private fun rootCause(t: Throwable): Throwable {
        var cur = t
        var guard = 0
        while (guard++ < 8) {
            val next = cur.cause ?: break
            if (next === cur) break
            cur = next
        }
        return cur
    }

    private fun scanInstanceForProviders(instance: BasePlugin, path: String): List<MainAPI> {
        val found = ArrayList<MainAPI>()
        runCatching {
            var c: Class<*>? = instance.javaClass
            while (c != null && c != Any::class.java) {
                for (f in c.declaredFields) {
                    runCatching {
                        f.isAccessible = true
                        when (val v = f.get(instance)) {
                            is MainAPI -> found.add(v)
                            is Collection<*> -> v.filterIsInstance<MainAPI>().forEach(found::add)
                            is Array<*> -> v.filterIsInstance<MainAPI>().forEach(found::add)
                        }
                    }
                }
                c = c.superclass
            }
        }
        if (found.isEmpty()) return emptyList()
        found.forEach { api ->
            runCatching { api.sourcePlugin = path }
            APIHolder.allProviders.withLock {
                if (APIHolder.allProviders.none { it === api }) {
                    APIHolder.allProviders.add(api)
                }
            }
            runCatching { APIHolder.addPluginMapping(api) }
        }
        return found.distinctBy { "${it.javaClass.name}#${it.name}" }
    }

    private fun injectActivityFieldIfPresent(apis: List<MainAPI>, activity: Activity) {
        apis.forEach { api ->
            runCatching {
                var done = false
                var c: Class<*>? = api.javaClass
                while (c != null && !done) {
                    runCatching {
                        c.getField("app").set(api, activity)
                        done = true
                    }
                    if (!done) {
                        runCatching {
                            c.getDeclaredField("app").apply { isAccessible = true }.set(api, activity)
                            done = true
                        }
                    }
                    c = c.superclass
                }
                if (!done) {
                    runCatching {
                        val holder = api.javaClass.getField("Companion").get(null)
                        holder?.javaClass?.getField("app")?.set(holder, activity)
                    }
                }
            }
        }
    }

    internal suspend fun loadPlugin(context: Context, file: File, data: PluginData): Boolean = loadLock.withLock {
        val path = file.absolutePath
        lastError = null
        if (plugins.containsKey(path)) return@withLock true
        currentlyLoading = file.nameWithoutExtension
        try {
            // Android 14+ requires dex files to be read-only; log if setReadOnly returns false rather than aborting.
            runCatching {
                if (!file.setReadOnly()) {
                    Log.w(TAG, "setReadOnly returned false for ${file.name} (may already be read-only)")
                }
            }

            val loader = PathClassLoader(path, context.classLoader)
            val manifest = loader.getResourceAsStream("manifest.json")?.bufferedReader()?.use {
                parseJson<BasePlugin.Manifest>(it.readText())
            } ?: error("Extension manifest.json is missing")

            val pluginClassName = requireNotNull(manifest.pluginClassName) { "Plugin class name is missing" }
            val pluginClass = loader.loadClass(pluginClassName)
            val ctor = pluginClass.getDeclaredConstructor().apply { isAccessible = true }
            val instance = ctor.newInstance() as BasePlugin
            instance.filename = path

            val hostContext: Context = CommonActivity.activity?.takeIf { !it.isFinishing && !it.isDestroyed }
                ?: context

            if (manifest.requiresResources && instance is Plugin) {
                runCatching {
                    val assets = AssetManager::class.java.getDeclaredConstructor().newInstance()
                    AssetManager::class.java.getMethod("addAssetPath", String::class.java).invoke(assets, path)
                    @Suppress("DEPRECATION")
                    instance.resources = Resources(
                        assets,
                        hostContext.resources.displayMetrics,
                        hostContext.resources.configuration
                    )
                }.onFailure {
                    Log.w(TAG, "Failed attaching plugin resources for ${file.name}", it)
                }
            }

            val beforeApis = APIHolder.allProviders.withLock { APIHolder.allProviders.toSet() }

            // Drop any stale registrations from an older install at the exact same path
            APIHolder.allProviders.withLock { APIHolder.allProviders.removeAll { it.sourcePlugin == path } }
            extractorApis.withLock { extractorApis.removeAll { it.sourcePlugin == path } }

            // Register first so unload can roll back partial registration on failure.
            plugins[path] = instance
            urlPlugins[data.url ?: path] = instance

            try {
                if (instance is Plugin) instance.load(hostContext) else instance.load()
            } catch (t: Throwable) {
                // Some plugins expect applicationContext instead of Activity or vice-versa; retry once if different
                if (instance is Plugin && hostContext !== context.applicationContext) {
                    Log.w(TAG, "Plugin load(activity) failed for ${file.name}, retrying with applicationContext", t)
                    instance.load(context.applicationContext)
                } else {
                    throw t
                }
            }

            // Repair sourcePlugin stamps on any newly registered providers & scan instance fields as fallback
            val freshApis = APIHolder.allProviders.withLock {
                val byPath = APIHolder.allProviders.filter { it.sourcePlugin == path }
                if (byPath.isNotEmpty()) byPath
                else APIHolder.allProviders.filter { it !in beforeApis }.onEach {
                    runCatching { it.sourcePlugin = path }
                }
            }.ifEmpty {
                scanInstanceForProviders(instance, path)
            }

            if (hostContext is Activity && freshApis.isNotEmpty()) {
                injectActivityFieldIfPresent(freshApis, hostContext)
            }

            APIHolder.initAll()
            save(data.copy(version = manifest.version ?: PLUGIN_VERSION_NOT_SET))
            true
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            unloadPlugin(path)
            throw cancelled
        } catch (error: Throwable) {
            unloadPlugin(path)
            val root = rootCause(error)
            val detail = buildString {
                append(error.javaClass.simpleName)
                if (!error.message.isNullOrBlank()) append(": ${error.message}")
                if (root !== error) {
                    append(" (${root.javaClass.simpleName}: ${root.message})")
                }
            }
            lastError = detail
            Log.e(TAG, "Unable to load ${file.name}: $detail", error)
            false
        } finally {
            currentlyLoading = null
        }
    }

    fun unloadPlugin(absolutePath: String) {
        val instance = plugins.remove(absolutePath) ?: return
        try {
            instance.beforeUnload()
        } catch (error: Throwable) {
            Log.w(TAG, "beforeUnload failed", error)
        }
        APIHolder.apis.filter { it.sourcePlugin == absolutePath }.forEach(APIHolder::removePluginMapping)
        APIHolder.allProviders.withLock { APIHolder.allProviders.removeAll { it.sourcePlugin == absolutePath } }
        extractorApis.withLock { extractorApis.removeAll { it.sourcePlugin == absolutePath } }
        urlPlugins.entries.removeAll { it.value === instance }
    }

    suspend fun deletePlugin(file: File): Boolean = loadLock.withLock {
        unloadPlugin(file.absolutePath)
        if (file.exists() && !file.delete()) return@withLock false
        dataLock.withLock {
            setKey(PLUGINS_KEY, getPluginsOnline().filter { it.filePath != file.absolutePath }.toTypedArray())
            setKey(PLUGINS_KEY_LOCAL, getPluginsLocal().filter { it.filePath != file.absolutePath }.toTypedArray())
        }
        true
    }

    suspend fun deleteRepositoryData(repositoryPath: String) {
        val folder = File(repositoryPath)
        val records = getPluginsOnline().filter { File(it.filePath).parentFile == folder }
        for (record in records) check(deletePlugin(File(record.filePath))) { "Cannot delete ${record.internalName}" }
        check(!folder.exists() || folder.deleteRecursively()) { "Cannot delete repository directory" }
    }
}
