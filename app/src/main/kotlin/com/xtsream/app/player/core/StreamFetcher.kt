package com.xtsream.app.player.core

import com.xtsream.app.DataFetch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

data class ExtSubtitle(val label: String, val url: String)

data class StreamServer(
    val name: String,
    val type: String,
    val url: String,
    val headers: Map<String, String>,
    val qualities: List<String>,
    val audios: List<String>,
    val embSubs: List<String>,
    val extSubs: List<ExtSubtitle>
)

object StreamFetcher {
    suspend fun fetchServers(type: String, id: String, providerId: String): List<StreamServer> = withContext(Dispatchers.IO) {
        try {
            val req = JSONObject().apply {
                put("request_type", "stream")
                put("type", type)
                put("id", id)
            }.toString()

            val resp = DataFetch.fetch(providerId, req)
            val list = mutableListOf<StreamServer>()
            val cleanStr = resp.trim()

            if (cleanStr.isNotEmpty()) {
                val parsedObject = JSONTokener(cleanStr).nextValue()
                val rootArr = if (parsedObject is JSONArray) {
                    parsedObject
                } else if (parsedObject is JSONObject) {
                    parsedObject.optJSONArray("data") ?: parsedObject.optJSONArray("stream") ?: JSONArray()
                } else {
                    JSONArray()
                }

                for (i in 0 until rootArr.length()) {
                    try {
                        val obj = rootArr.getJSONObject(i)
                        val sName = obj.optString("nm", "Server ${i + 1}")
                        val sType = obj.optString("type", "").lowercase()
                        val url = obj.optString("url", "")
                        
                        val headersMap = mutableMapOf<String, String>()
                        val headersObj = obj.optJSONObject("headers")
                        if (headersObj != null) {
                            val keys = headersObj.keys()
                            while (keys.hasNext()) {
                                val k = keys.next()
                                headersMap[k] = headersObj.optString(k, "")
                            }
                        }

                        val qList = mutableListOf<String>()
                        val qArr = obj.optJSONArray("qualities")
                        if (qArr != null) {
                            for (q in 0 until qArr.length()) qList.add(qArr.getString(q))
                        }

                        val aList = mutableListOf<String>()
                        val aArr = obj.optJSONArray("audios")
                        if (aArr != null) {
                            for (a in 0 until aArr.length()) aList.add(aArr.getString(a))
                        }

                        val embSubs = mutableListOf<String>()
                        val extSubs = mutableListOf<ExtSubtitle>()
                        val subObj = obj.optJSONObject("subtitles")
                        if (subObj != null) {
                            val embArr = subObj.optJSONArray("embedded")
                            if (embArr != null) {
                                for (e in 0 until embArr.length()) embSubs.add(embArr.getString(e))
                            }
                            val extArr = subObj.optJSONArray("external")
                            if (extArr != null) {
                                for (e in 0 until extArr.length()) {
                                    val ex = extArr.optJSONObject(e)
                                    if (ex != null) {
                                        extSubs.add(ExtSubtitle(ex.optString("label", ""), ex.optString("url", "")))
                                    }
                                }
                            }
                        }

                        if (url.isNotBlank()) {
                            list.add(StreamServer(sName, sType, url, headersMap, qList, aList, embSubs, extSubs))
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }
}
