package com.lagradost.cloudstream3.utils

import android.app.Activity
import android.content.Context
import android.content.res.Resources
import android.view.View
import androidx.fragment.app.Fragment
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.APIHolder.unixTime
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.ui.WatchType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object DataStoreHelper {
    const val currentAccountKey = "current_account"
    const val accountsKey = "accounts"

    var currentAccount: String = "0"
    var currentHomePage: String? = null

    @Serializable
    data class Account(
        @JsonProperty("keyIndex") @SerialName("keyIndex") val keyIndex: Int,
        @JsonProperty("name") @SerialName("name") val name: String,
        @JsonProperty("customImage") @SerialName("customImage") val customImage: String? = null,
        @JsonProperty("defaultImageIndex") @SerialName("defaultImageIndex") val defaultImageIndex: Int = 0,
        @JsonProperty("lockPin") @SerialName("lockPin") val lockPin: String? = null,
    )

    @Serializable
    data class PosDur(
        @JsonProperty("position") @SerialName("position") val position: Long,
        @JsonProperty("duration") @SerialName("duration") val duration: Long,
    )

    @Serializable
    data class ResumeWatchingResult(
        @JsonProperty("name") @SerialName("name") override val name: String,
        @JsonProperty("url") @SerialName("url") override val url: String,
        @JsonProperty("apiName") @SerialName("apiName") override val apiName: String,
        @JsonProperty("type") @SerialName("type") override var type: TvType? = null,
        @JsonProperty("posterUrl") @SerialName("posterUrl") override var posterUrl: String? = null,
        @JsonProperty("watchPos") @SerialName("watchPos") val watchPos: PosDur? = null,
        @JsonProperty("id") @SerialName("id") override var id: Int? = null,
        @JsonProperty("parentId") @SerialName("parentId") val parentId: Int? = null,
        @JsonProperty("episode") @SerialName("episode") val episode: Int? = null,
        @JsonProperty("season") @SerialName("season") val season: Int? = null,
        @JsonProperty("isFromDownload") @SerialName("isFromDownload") val isFromDownload: Boolean = false,
        @JsonProperty("quality") @SerialName("quality") override var quality: SearchQuality? = null,
        @JsonProperty("posterHeaders") @SerialName("posterHeaders") override var posterHeaders: Map<String, String>? = null,
        @JsonProperty("score") @SerialName("score") override var score: Score? = null,
    ) : SearchResponse

    @Serializable
    data class BookmarkedData(
        @JsonProperty("id") @SerialName("id") override var id: Int? = null,
        @JsonProperty("bookmarkedTime") @SerialName("bookmarkedTime") val bookmarkedTime: Long = unixTime,
        @JsonProperty("latestUpdatedTime") @SerialName("latestUpdatedTime") val latestUpdatedTime: Long = unixTime,
        @JsonProperty("name") @SerialName("name") override val name: String,
        @JsonProperty("url") @SerialName("url") override val url: String,
        @JsonProperty("apiName") @SerialName("apiName") override val apiName: String,
        @JsonProperty("type") @SerialName("type") override var type: TvType? = null,
        @JsonProperty("posterUrl") @SerialName("posterUrl") override var posterUrl: String? = null,
        @JsonProperty("year") @SerialName("year") val year: Int? = null,
        @JsonProperty("quality") @SerialName("quality") override var quality: SearchQuality? = null,
        @JsonProperty("posterHeaders") @SerialName("posterHeaders") override var posterHeaders: Map<String, String>? = null,
        @JsonProperty("score") @SerialName("score") override var score: Score? = null,
    ) : SearchResponse

    fun getAllResumeStateIds(): List<Int>? = emptyList()
    fun getLastWatched(id: Int?): ResumeWatchingResult? = null
    fun getAllWatchStateIds(): List<Int>? = emptyList()
    fun getResultWatchState(id: Int): WatchType = WatchType.NONE
    fun getBookmarkedData(id: Int?): BookmarkedData? = null
    fun getViewPos(id: Int?): PosDur? = null
    fun setViewPos(id: Int?, pos: Long, dur: Long) {}
    fun getAccounts(context: Context): Array<Account> =
        runCatching { getKey<Array<Account>>(accountsKey) }.getOrNull() ?: arrayOf(Account(0, "Default"))
    fun setAccount(account: Account) {
        currentAccount = account.keyIndex.toString()
        runCatching { setKey(currentAccountKey, currentAccount) }
    }
}

object UIHelper {
    val Int.toPx: Int get() = (this * Resources.getSystem().displayMetrics.density).toInt()
    val Int.toDp: Int get() = (this / Resources.getSystem().displayMetrics.density).toInt()

    fun Activity?.hideSystemUI() {}
    fun Activity?.showSystemUI() {}
    fun Activity?.hideKeyboard(view: View? = null) {}
    fun Fragment?.hideKeyboard() {}
    fun Context?.checkWrite(): Boolean = true
    fun Activity?.requestRW() {}
    fun Activity?.changeStatusBarState(hide: Boolean): Int = 0
    fun View?.dismissSafe(activity: Activity? = null) {}
    fun View?.fixPaddingStatusbar() {}
}

object AppContextUtils {
    fun Context?.isNetworkAvailable(): Boolean = true
    fun Context?.isConnectedTochromecast(): Boolean = false
    fun Context?.isUsingMobileData(): Boolean = false
    fun Context?.updateLocale(languageCode: String?) {}
    fun Context?.loadResult(url: String, apiName: String, startAction: Int = 0, startValue: Int = 0) {}
    fun Activity?.loadResult(url: String, apiName: String, startAction: Int = 0, startValue: Int = 0) {}
    fun getApiDubstatusSettings(): HashSet<com.lagradost.cloudstream3.DubStatus> =
        hashSetOf(com.lagradost.cloudstream3.DubStatus.Subbed, com.lagradost.cloudstream3.DubStatus.Dubbed)
}
