package com.xtsream.app.stremio

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.xtsream.app.engine.PluginIconCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistent storage and lifecycle manager for user-added Stremio addons.
 */
object StremioRepository {
    private const val TAG = "StremioRepository"
    private const val PREF_NAME = "stremio_addons_store"
    private const val KEY_ADDONS = "addons_v1"

    private var prefs: SharedPreferences? = null

    private val _addons = MutableStateFlow<List<StremioAddonRecord>>(emptyList())
    val addons: StateFlow<List<StremioAddonRecord>> = _addons.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        _addons.value = loadFromDisk()
    }

    suspend fun addAddon(context: Context, rawUrl: String): Result<StremioAddonRecord> = withContext(Dispatchers.IO) {
        init(context)
        val fetched = StremioClient.fetchManifest(rawUrl)
        val record = fetched.getOrElse { return@withContext Result.failure(it) }

        // Cache addon logo locally
        PluginIconCache.cacheIcon(context, record.manifest.logo)

        val updated = (_addons.value.filterNot {
            it.manifestUrl.equals(record.manifestUrl, ignoreCase = true) ||
                it.manifest.id.equals(record.manifest.id, ignoreCase = true)
        } + record)
        _addons.value = updated
        saveToDisk(updated)
        Result.success(record)
    }

    suspend fun refreshAddon(context: Context, manifestUrl: String): Result<StremioAddonRecord> =
        addAddon(context, manifestUrl)

    fun toggleEnabled(context: Context, manifestUrl: String): Boolean {
        init(context)
        var newState = true
        val updated = _addons.value.map { rec ->
            if (rec.manifestUrl == manifestUrl) {
                newState = !rec.enabled
                rec.copy(enabled = newState)
            } else rec
        }
        _addons.value = updated
        saveToDisk(updated)
        return newState
    }

    fun removeAddon(context: Context, manifestUrl: String) {
        init(context)
        val updated = _addons.value.filterNot { it.manifestUrl == manifestUrl }
        _addons.value = updated
        saveToDisk(updated)
    }

    fun getEnabledAddons(): List<StremioAddonRecord> = _addons.value.filter { it.enabled }

    private fun saveToDisk(list: List<StremioAddonRecord>) {
        val p = prefs ?: return
        try {
            val arr = JSONArray()
            list.forEach { rec ->
                arr.put(serializeRecord(rec))
            }
            p.edit().putString(KEY_ADDONS, arr.toString()).apply()
        } catch (t: Throwable) {
            Log.w(TAG, "Failed saving Stremio addons", t)
        }
    }

    private fun loadFromDisk(): List<StremioAddonRecord> {
        val raw = prefs?.getString(KEY_ADDONS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    deserializeRecord(obj)?.let { add(it) }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun serializeRecord(rec: StremioAddonRecord): JSONObject {
        val m = rec.manifest
        val mObj = JSONObject().apply {
            put("id", m.id)
            put("name", m.name)
            put("version", m.version)
            put("description", m.description)
            put("logo", m.logo ?: JSONObject.NULL)
            put("background", m.background ?: JSONObject.NULL)
            put("types", JSONArray(m.types))
            if (m.idPrefixes != null) put("idPrefixes", JSONArray(m.idPrefixes))
            put("resources", JSONArray().apply {
                m.resources.forEach { r ->
                    put(JSONObject().apply {
                        put("name", r.name)
                        put("types", JSONArray(r.types))
                        if (r.idPrefixes != null) put("idPrefixes", JSONArray(r.idPrefixes))
                    })
                }
            })
            put("catalogs", serializeCatalogs(m.catalogs))
            put("addonCatalogs", serializeCatalogs(m.addonCatalogs))
            put("behaviorHints", JSONObject().apply {
                put("configurable", m.configurable)
                put("configurationRequired", m.configurationRequired)
            })
        }
        return JSONObject().apply {
            put("manifestUrl", rec.manifestUrl)
            put("baseUrl", rec.baseUrl)
            put("enabled", rec.enabled)
            put("addedAt", rec.addedAt)
            put("manifest", mObj)
        }
    }

    private fun serializeCatalogs(catalogs: List<StremioCatalogDef>): JSONArray = JSONArray().apply {
        catalogs.forEach { cat ->
            put(JSONObject().apply {
                put("type", cat.type)
                put("id", cat.id)
                put("name", cat.name)
                put("extra", JSONArray().apply {
                    cat.extra.forEach { ex ->
                        put(JSONObject().apply {
                            put("name", ex.name)
                            put("isRequired", ex.isRequired)
                            put("options", JSONArray(ex.options))
                            put("optionsLimit", ex.optionsLimit)
                        })
                    }
                })
            })
        }
    }

    private fun deserializeRecord(obj: JSONObject): StremioAddonRecord? {
        val manifestUrl = obj.optString("manifestUrl", "").trim()
        val baseUrl = obj.optString("baseUrl", "").trim()
        val manifestObj = obj.optJSONObject("manifest") ?: return null
        val manifest = StremioClient.parseManifest(manifestObj) ?: return null
        if (manifestUrl.isBlank() || baseUrl.isBlank()) return null
        return StremioAddonRecord(
            manifestUrl = manifestUrl,
            baseUrl = baseUrl,
            manifest = manifest,
            enabled = obj.optBoolean("enabled", true),
            addedAt = obj.optLong("addedAt", System.currentTimeMillis())
        )
    }
}
