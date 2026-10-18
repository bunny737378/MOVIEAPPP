package com.xtsream.app.data

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.request.ImageRequest
import com.xtsream.app.DataFetch
import com.xtsream.app.manage.CacheManager
import com.xtsream.app.player.core.ManifestParser
import com.xtsream.app.player.core.ParsedTracks
import com.xtsream.app.player.core.StreamFetcher
import com.xtsream.app.player.core.StreamServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class MetaBasicData(val id: String, val type: String, val bg: String, val tags: List<String>, val plot: String)
data class MetaActorData(val name: String?, val role: String?, val img: String?)
data class FullMetaData(val basic: MetaBasicData, val actors: List<MetaActorData>, val recommend: List<MediaItem>)
data class SeasonPart(val name: String, val id: String)
data class SeasonGroup(val seasonNo: String, val name: String, val parts: List<SeasonPart>)
data class EpisodeItem(val epNo: String, val id: String, val title: String, val img: String?, val plot: String?, val tags: List<String>?)
data class FullSeasonInfo(val seasons: List<SeasonGroup>, val initialEpisodes: List<EpisodeItem>)

class MetaDataViewModel : ViewModel() {
    var metaData by mutableStateOf<FullMetaData?>(null)
    var seasonInfo by mutableStateOf<FullSeasonInfo?>(null)
    var currentEpisodes by mutableStateOf<List<EpisodeItem>?>(null)
    var dominantColor by mutableStateOf(Color(0xFF262830)) 
    var isLoadingMeta by mutableStateOf(false)
    var isEpLoading by mutableStateOf(false)
    var isSubEpLoading by mutableStateOf(false)
    var layoutMode by mutableIntStateOf(0) 

    var selectedEpisode by mutableStateOf<EpisodeItem?>(null)

    var streamServers by mutableStateOf<List<StreamServer>?>(null)
    var isFetchingStreams by mutableStateOf(false)
    
    var parsedManifest by mutableStateOf<ParsedTracks?>(null)
    var isParsingManifest by mutableStateOf(false)

    private val job = Job()
    var currentProviderId = ""

    override fun onCleared() { super.onCleared(); job.cancel() }

    fun fetchAllData(mediaItem: MediaItem, context: Context) {
        if (isLoadingMeta) return
        isLoadingMeta = true
        selectedEpisode = null 
        currentProviderId = mediaItem.providerId
        viewModelScope.launch(job) { extractColor(mediaItem.img, context) }

        val cached = CacheManager.getMetaData(mediaItem.id)
        if (cached != null) {
            metaData = cached
            val cs = CacheManager.getSeasonInfo(cached.basic.id)
            if (cs != null) { seasonInfo = cs; currentEpisodes = cs.initialEpisodes; isLoadingMeta = false; return }
            fetchSeasonInfo(cached.basic.type, cached.basic.id)
            isLoadingMeta = false; return
        }

        viewModelScope.launch(job + Dispatchers.IO) {
            try {
                val req = """{"request_type":"meta","type":"${mediaItem.type}","id":"${mediaItem.id}"}"""
                val r = DataFetch.fetch(currentProviderId, req)
                if (r.isEmpty()) { withContext(Dispatchers.Main) { isLoadingMeta = false }; return@launch }
                val p = parseMeta(r, currentProviderId)
                withContext(Dispatchers.Main) {
                    metaData = p
                    if (p != null) { 
                        CacheManager.saveMetaData(mediaItem.id, p)
                        fetchSeasonInfo(p.basic.type, p.basic.id) 
                    }
                    else isLoadingMeta = false
                }
            } catch (e: Exception) { withContext(Dispatchers.Main) { isLoadingMeta = false } }
        }
    }

    private fun fetchSeasonInfo(type: String, id: String) {
        isEpLoading = true
        viewModelScope.launch(job + Dispatchers.IO) {
            try {
                val req = """{"request_type":"season_info","type":"$type","id":"$id"}"""
                val r = DataFetch.fetch(currentProviderId, req)
                if (r.isEmpty()) { withContext(Dispatchers.Main) { isEpLoading = false; isLoadingMeta = false }; return@launch }
                val p = parseSeason(r)
                withContext(Dispatchers.Main) {
                    if (p != null && (p.seasons.isNotEmpty() || p.initialEpisodes.isNotEmpty())) {
                        seasonInfo = p; currentEpisodes = p.initialEpisodes
                        CacheManager.saveSeasonInfo(id, p)
                    } else currentEpisodes = null
                }
            } catch (e: Exception) {} finally { withContext(Dispatchers.Main) { isEpLoading = false; isLoadingMeta = false } }
        }
    }

    fun fetchEpisodesForSeasonPart(partId: String, isSub: Boolean = false) {
        if (metaData == null) return
        if (isSub) isSubEpLoading = true else isEpLoading = true
        currentEpisodes = null
        viewModelScope.launch(job + Dispatchers.IO) {
            try {
                val req = """{"request_type":"season_ep_info","type":"${metaData!!.basic.type}","id":"$partId"}"""
                val r = DataFetch.fetch(currentProviderId, req)
                if (r.isNotEmpty()) {
                    val root = JSONObject(r)
                    val epsArr = root.optJSONArray("episodes") ?: root.optJSONArray("Episodes")
                    val eps = parseEp(epsArr)
                    withContext(Dispatchers.Main) { currentEpisodes = eps }
                }
            } catch (e: Exception) {} finally {
                withContext(Dispatchers.Main) { if (isSub) isSubEpLoading = false else isEpLoading = false }
            }
        }
    }

    fun fetchStreamsForDownload(epId: String) {
        isFetchingStreams = true
        streamServers = null
        parsedManifest = null
        viewModelScope.launch(job + Dispatchers.IO) {
            val servers = StreamFetcher.fetchServers(metaData?.basic?.type ?: "", epId, currentProviderId)
            withContext(Dispatchers.Main) {
                streamServers = servers
                isFetchingStreams = false
            }
        }
    }

    fun parseStreamManifest(server: StreamServer) {
        isParsingManifest = true
        parsedManifest = null
        viewModelScope.launch(job + Dispatchers.IO) {
            val tracks = ManifestParser.parse(server.url, server.headers)
            withContext(Dispatchers.Main) {
                parsedManifest = tracks
                isParsingManifest = false
            }
        }
    }

    private suspend fun extractColor(url: String, context: Context) {
        try {
            val req = ImageRequest.Builder(context).data(url).allowHardware(false).build()
            val bmp = (coil.Coil.imageLoader(context).execute(req).drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
            if (bmp != null) {
                val scaled = Bitmap.createScaledBitmap(bmp, 50, 50, false)
                val counts = mutableMapOf<Int, Int>()
                for (x in 0 until scaled.width step 2) for (y in 0 until scaled.height step 2) {
                    val p = scaled.getPixel(x, y)
                    val r = android.graphics.Color.red(p); val g = android.graphics.Color.green(p); val b = android.graphics.Color.blue(p)
                    val qr = (r / 16) * 16; val qg = (g / 16) * 16; val qb = (b / 16) * 16
                    if (qr < 20 && qg < 20 && qb < 20) continue
                    val rgb = android.graphics.Color.rgb(qr, qg, qb)
                    counts[rgb] = (counts[rgb] ?: 0) + 1
                }
                scaled.recycle()
                val rgb = counts.maxByOrNull { it.value }?.key ?: android.graphics.Color.rgb(38, 40, 48)
                withContext(Dispatchers.Main) { dominantColor = Color(android.graphics.Color.red(rgb), android.graphics.Color.green(rgb), android.graphics.Color.blue(rgb)) }
            }
        } catch (e: Exception) {}
    }

    private fun parseEp(arr: org.json.JSONArray?): List<EpisodeItem> {
        val list = mutableListOf<EpisodeItem>()
        if (arr == null) return list
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val tags = mutableListOf<String>()
            val tagsArr = o.optJSONArray("tags") ?: o.optJSONArray("Tags")
            tagsArr?.let { for (j in 0 until it.length()) tags.add(it.getString(j)) }
            
            val epId = o.optString("id").takeIf { it.isNotBlank() } ?: o.optString("Id", "")
            val img = o.optString("img").takeIf { it.isNotBlank() } ?: o.optString("Img", "")
            val title = o.optString("title").takeIf { it.isNotBlank() } ?: o.optString("titel").takeIf { it.isNotBlank() } ?: o.optString("Title", "")
            val plot = o.optString("plot").takeIf { it.isNotBlank() } ?: o.optString("Plot").takeIf { it.isNotBlank() } ?: o.optString("description", "")
            val epNo = o.optString("ep_no").takeIf { it.isNotBlank() } ?: o.optString("epNo").takeIf { it.isNotBlank() } ?: o.optString("episode", "")
            
            list.add(EpisodeItem(epNo, epId, title, img.takeIf { it.isNotBlank() }, plot.takeIf { it.isNotBlank() }, tags.takeIf { it.isNotEmpty() }))
        }
        return list
    }

    private fun parseSeason(json: String): FullSeasonInfo? = try {
        val r = JSONObject(json)
        val seasons = mutableListOf<SeasonGroup>()
        val seasonsArr = r.optJSONArray("seasons") ?: r.optJSONArray("Seasons")
        seasonsArr?.let { for (i in 0 until it.length()) {
            val s = it.getJSONObject(i)
            val parts = mutableListOf<SeasonPart>()
            val partsArr = s.optJSONArray("id") ?: s.optJSONArray("Id") ?: s.optJSONArray("parts") ?: s.optJSONArray("Parts")
            partsArr?.let { for (j in 0 until it.length()) {
                val p = it.getJSONObject(j)
                parts.add(SeasonPart(
                    p.optString("name", p.optString("Name", "")), 
                    p.optString("id", p.optString("Id", ""))
                ))
            }}
            if (parts.isNotEmpty()) seasons.add(SeasonGroup(
                s.optString("season_no", s.optString("seasonNo", "")), 
                s.optString("name", s.optString("Name", "")), 
                parts
            ))
        }}
        val epsArr = r.optJSONArray("episodes") ?: r.optJSONArray("Episodes")
        FullSeasonInfo(seasons, parseEp(epsArr))
    } catch (e: Exception) { null }

    private fun parseMeta(json: String, pid: String): FullMetaData? = try {
        val r = JSONObject(json)
        val b = r.optJSONObject("basic") ?: r.optJSONObject("Basic") ?: JSONObject()
        val tags = mutableListOf<String>()
        val tagsArr = b.optJSONArray("tags") ?: b.optJSONArray("Tags")
        tagsArr?.let { for (i in 0 until it.length()) tags.add(it.getString(i)) }
        
        val actors = mutableListOf<MetaActorData>()
        val actorsArr = r.optJSONArray("actors") ?: r.optJSONArray("Actors")
        actorsArr?.let { for (i in 0 until it.length()) {
            val a = it.getJSONObject(i)
            actors.add(MetaActorData(
                a.optString("name", a.optString("Name", "")).takeIf { it.isNotBlank() }, 
                a.optString("role", a.optString("Role", "")).takeIf { it.isNotBlank() }, 
                a.optString("img", a.optString("Img", "")).takeIf { it.isNotBlank() }
            ))
        }}
        
        val rec = mutableListOf<MediaItem>()
        val recArr = r.optJSONArray("recommend") ?: r.optJSONArray("Recommend")
        recArr?.let { for (i in 0 until it.length()) {
            val o = it.getJSONObject(i)
            rec.add(MediaItem(
                o.optString("id", o.optString("Id", "")), 
                o.optString("img", o.optString("Img", "")), 
                o.optString("titel", o.optString("title", o.optString("Title", ""))), 
                o.optString("type", o.optString("Type", "")), 
                pid
            ))
        }}
        
        FullMetaData(
            MetaBasicData(
                b.optString("id", b.optString("Id", "")), 
                b.optString("type", b.optString("Type", "")), 
                b.optString("bg", b.optString("Bg", b.optString("backdrop", ""))), 
                tags, 
                b.optString("plot", b.optString("Plot", b.optString("description", b.optString("desc", ""))))
            ), 
            actors, 
            rec
        )
    } catch (e: Exception) { null }
}
