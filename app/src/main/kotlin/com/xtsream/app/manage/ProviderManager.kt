package com.xtsream.app.manage

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.URL

data class PluginConfig(val id: String, val name: String, var icon: String, var isInstalled: Boolean = false)

object ProviderManager {
    var plugins by mutableStateOf<List<PluginConfig>>(emptyList())
    var currentProvider by mutableStateOf<PluginConfig?>(null)
    var isListLoaded by mutableStateOf(false)

    fun init(context: Context) {
        loadFromCache(context)
        refreshInstalled(context)
    }

    private fun loadFromCache(context: Context) {
        val cacheFile = File(context.filesDir, "cached_pro.json")
        if (cacheFile.exists()) {
            try {
                val jsonStr = cacheFile.readText()
                parsePlugins(jsonStr)
                isListLoaded = true
            } catch (e: Exception) {}
        }
    }

    private fun parsePlugins(jsonStr: String) {
        val jsonArray = JSONArray(jsonStr)
        val list = mutableListOf<PluginConfig>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            list.add(PluginConfig(obj.getString("id"), obj.getString("name"), obj.optString("icon", "")))
        }
        plugins = list
    }

    private fun refreshInstalled(context: Context) {
        val pluginDir = File(context.filesDir, "plugins")
        if (!pluginDir.exists()) pluginDir.mkdirs()
        
        val installedIds = pluginDir.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()
        
        val updatedList = plugins.map { it.copy(isInstalled = installedIds.contains(it.id)) }
        plugins = updatedList

        if (currentProvider == null) {
            currentProvider = updatedList.firstOrNull { it.isInstalled }
        } else {
            currentProvider = updatedList.find { it.id == currentProvider?.id } ?: updatedList.firstOrNull { it.isInstalled }
        }
    }

    suspend fun fetchPluginsList(context: Context) = withContext(Dispatchers.IO) {
        try {
            val jsonStr = URL("https://raw.githubusercontent.com/bunny737378/provider/main/pro.json").readText()
            val jsonArray = JSONArray(jsonStr)
            
            val iconsDir = File(context.filesDir, "icons")
            if (!iconsDir.exists()) iconsDir.mkdirs()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val iconUrl = obj.optString("icon", "")
                if (iconUrl.startsWith("http")) {
                    try {
                        val iconFile = File(iconsDir, "${obj.getString("id")}.png")
                        if (!iconFile.exists()) {
                            iconFile.writeBytes(URL(iconUrl).readBytes())
                        }
                        obj.put("icon", iconFile.absolutePath)
                    } catch (e: Exception) {}
                }
            }
            
            val finalJson = jsonArray.toString()
            File(context.filesDir, "cached_pro.json").writeText(finalJson)
            
            withContext(Dispatchers.Main) {
                parsePlugins(finalJson)
                refreshInstalled(context)
                isListLoaded = true
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { isListLoaded = true }
        }
    }

    suspend fun autoInstallDefault(context: Context, onProgress: (Int) -> Unit): Boolean {
        if (plugins.isEmpty()) return false
        var target = plugins.find { it.id == "mxplayer" }
        if (target == null) target = plugins.first()
        if (target.isInstalled) return true
        return installOrUpdatePlugin(context, target, onProgress)
    }

    suspend fun installOrUpdatePlugin(context: Context, plugin: PluginConfig, onProgress: (Int) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val files = listOf("catalog.json", "config.js", "meta.js", "discovery.js", "seasons.js", "streams.js")
        val pluginDir = File(context.filesDir, "plugins/${plugin.id}")
        if (!pluginDir.exists()) pluginDir.mkdirs()

        var downloaded = 0
        for (file in files) {
            try {
                val url = "https://raw.githubusercontent.com/bunny737378/provider/main/${plugin.id}/$file"
                val content = URL(url).readText()
                File(pluginDir, file).writeText(content)
            } catch (e: Exception) {}
            downloaded++
            withContext(Dispatchers.Main) { onProgress((downloaded * 100) / files.size) }
        }
        
        withContext(Dispatchers.Main) {
            refreshInstalled(context)
            if (currentProvider == null) currentProvider = plugins.find { it.id == plugin.id }
        }
        true
    }

    fun uninstallPlugin(context: Context, plugin: PluginConfig) {
        val pluginDir = File(context.filesDir, "plugins/${plugin.id}")
        if (pluginDir.exists()) pluginDir.deleteRecursively()
        
        com.xtsream.app.DataFetch.unloadProvider(plugin.id)
        com.xtsream.app.manage.CacheManager.clearSession(context)
        
        refreshInstalled(context)
    }

    fun switchProvider(provider: PluginConfig, onSwitched: () -> Unit) {
        if (currentProvider?.id == provider.id || !provider.isInstalled) return
        currentProvider = provider
        onSwitched()
    }
}
