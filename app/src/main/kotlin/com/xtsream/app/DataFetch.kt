package com.xtsream.app

import android.content.Context
import app.cash.quickjs.QuickJs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

object DataFetch {

    private var jsBasePath = ""
    private val loadedProviders = mutableMapOf<String, ProviderData>()

    // 🔥 NAYA INIT FUNCTION 🔥
    fun init(context: Context) {
        jsBasePath = File(context.filesDir, "plugins").absolutePath + "/"
    }

    data class ProviderData(
        val name: String,
        val path: String,
        var config: String = "",
        var discovery: String = "",
        var meta: String = "",
        var seasons: String = "",
        var streams: String = "",
        var catalog: String = "",
        var loaded: Boolean = false
    )

    private fun loadProvider(name: String): Boolean {
        if (loadedProviders[name]?.loaded == true) return true
        if (jsBasePath.isEmpty()) return false
        
        val path = "$jsBasePath$name/"
        val dir = File(path)
        if (!dir.exists()) return false

        val data = ProviderData(name, path)

        fun readFile(fileName: String): String {
            val f = File(path + fileName)
            return if (f.exists()) f.readText() else ""
        }

        data.config = readFile("config.js")
        data.discovery = readFile("discovery.js")
        data.meta = readFile("meta.js")
        data.seasons = readFile("seasons.js")
        data.streams = readFile("streams.js")
        data.catalog = readFile("catalog.json")
        data.loaded = true
        loadedProviders[name] = data
        return true
    }

    suspend fun fetch(
        provider: String,
        requestJson: String
    ): String = withContext(Dispatchers.IO) {
        if (provider.isBlank() || jsBasePath.isEmpty()) return@withContext ""
        coroutineContext.ensureActive()
        
        if (!loadProvider(provider)) return@withContext ""

        val data = loadedProviders[provider] ?: return@withContext ""

        var qjs: QuickJs? = null
        try {
            coroutineContext.ensureActive()
            qjs = QuickJs.create()
            qjs.set("Network", NetworkBridge::class.java, NetworkBridgeImpl())
            qjs.set("Crypto", CryptoBridge::class.java, CryptoBridgeImpl())
            qjs.set("Base64", Base64Bridge::class.java, Base64BridgeImpl())

            val fullCode = """
                ${if (data.catalog.isNotEmpty()) "const CATALOG_DATA = ${data.catalog};" else ""}
                ${data.config}
                ${data.discovery}
                ${data.meta}
                ${data.seasons}
                ${data.streams}
            """.trimIndent()

            qjs.evaluate(fullCode)
            coroutineContext.ensureActive()

            val safe = requestJson.replace("\\", "\\\\").replace("'", "\\'")
            (qjs.evaluate("handleRequest('$safe');") as? String) ?: ""
        } catch (e: Exception) {
            ""
        } finally {
            qjs?.close()
        }
    }

    fun unloadProvider(provider: String) {
        loadedProviders.remove(provider)
    }

    fun unloadAll() {
        loadedProviders.clear()
    }
}