package com.lagradost.cloudstream3.syncproviders

import androidx.annotation.WorkerThread
import com.lagradost.cloudstream3.APIHolder.unixTime
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.openBrowser
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.safe
import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities.SubtitleEntity
import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities.SubtitleSearch
import com.lagradost.cloudstream3.subtitles.SubtitleResource
import com.lagradost.cloudstream3.syncproviders.AccountManager.Companion.NONE_ID
import com.lagradost.cloudstream3.syncproviders.providers.Addic7ed
import com.lagradost.cloudstream3.syncproviders.providers.AniListApi
import com.lagradost.cloudstream3.syncproviders.providers.KitsuApi
import com.lagradost.cloudstream3.syncproviders.providers.LocalList
import com.lagradost.cloudstream3.syncproviders.providers.MALApi
import com.lagradost.cloudstream3.syncproviders.providers.OpenSubtitlesApi
import com.lagradost.cloudstream3.syncproviders.providers.SimklApi
import com.lagradost.cloudstream3.syncproviders.providers.SubDlApi
import com.lagradost.cloudstream3.syncproviders.providers.SubSourceApi
import com.lagradost.cloudstream3.utils.Coroutines.atomicListOf
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.videoskip.AnimeSkipAuth
import java.util.concurrent.TimeUnit

class PlainAuthRepo(api: AuthAPI) : AuthRepo(api)

abstract class AuthRepo(open val api: AuthAPI) {
    fun isValidRedirectUrl(url: String) = safe { api.isValidRedirectUrl(url) } ?: false

    val idPrefix get() = api.idPrefix
    val name get() = api.name
    val icon get() = api.icon
    val requiresLogin get() = api.requiresLogin
    val createAccountUrl get() = api.createAccountUrl
    val hasOAuth2 get() = api.hasOAuth2
    val hasPin get() = api.hasPin
    val hasInApp get() = api.hasInApp
    val inAppLoginRequirement get() = api.inAppLoginRequirement
    val isAvailable get() = !api.requiresLogin || authUser() != null

    companion object {
        private val oauthPayload: MutableMap<String, String?> = mutableMapOf()
    }

    protected suspend fun freshAuth(): AuthData? {
        val data = authData() ?: return null
        if (data.token.isAccessTokenExpired()) {
            val newToken = api.refreshToken(data.token) ?: return null
            val newAuth = AuthData(user = data.user, token = newToken)
            refreshUser(newAuth)
            return newAuth
        }
        return data
    }

    fun openOAuth2Page(): Boolean {
        val page = api.loginRequest() ?: return false
        synchronized(oauthPayload) {
            oauthPayload[api.idPrefix] = page.payload
        }
        openBrowser(page.url)
        return true
    }

    fun openOAuth2PageWithToast() {
        try {
            if (!openOAuth2Page()) {
                showToast("Failed to authenticate ${api.name}")
            }
        } catch (t: Throwable) {
            logError(t)
            showToast(t.message ?: "Failed to authenticate ${api.name}")
        }
    }

    suspend fun logout(from: AuthUser) {
        val currentAccounts = AccountManager.accounts(idPrefix)
        val (newAccounts, oldAccounts) = currentAccounts.partition { it.user.id != from.id }
        if (newAccounts.size < currentAccounts.size) {
            AccountManager.updateAccounts(idPrefix, newAccounts.toTypedArray())
            AccountManager.updateAccountsId(idPrefix, 0)
        }
        for (oldAccount in oldAccounts) {
            runCatching { api.invalidateToken(oldAccount.token) }
        }
    }

    fun refreshUser(newAuth: AuthData) {
        val currentAccounts = AccountManager.accounts(idPrefix)
        val newAccounts = currentAccounts.map {
            if (it.user.id == newAuth.user.id) newAuth else it
        }.toTypedArray()
        AccountManager.updateAccounts(idPrefix, newAccounts)
    }

    fun authData(): AuthData? = synchronized(AccountManager.cachedAccountIds) {
        AccountManager.cachedAccountIds[idPrefix]?.let { id ->
            AccountManager.cachedAccounts[idPrefix]?.firstOrNull { data -> data.user.id == id }
        }
    }

    fun authToken(): AuthToken? = authData()?.token
    fun authUser(): AuthUser? = authData()?.user

    val accounts
        get() = synchronized(AccountManager.cachedAccounts) {
            AccountManager.cachedAccounts[idPrefix] ?: emptyArray()
        }

    var accountId
        get() = synchronized(AccountManager.cachedAccountIds) {
            AccountManager.cachedAccountIds[idPrefix] ?: NONE_ID
        }
        set(value) {
            AccountManager.updateAccountsId(idPrefix, value)
        }

    suspend fun pinRequest() = api.pinRequest()

    private suspend fun setupLogin(token: AuthToken): Boolean {
        val user = api.user(token) ?: return false
        val newAccount = AuthData(token = token, user = user)
        val currentAccounts = AccountManager.accounts(idPrefix)
        if (currentAccounts.any { it.user.id == newAccount.user.id }) {
            throw ErrorLoadingException("Already logged into this account")
        }
        AccountManager.updateAccounts(idPrefix, currentAccounts + newAccount)
        AccountManager.updateAccountsId(idPrefix, user.id)
        if (this is SyncRepo) requireLibraryRefresh = true
        return true
    }

    suspend fun login(form: AuthLoginResponse): Boolean = setupLogin(api.login(form) ?: return false)
    suspend fun login(payload: AuthPinData): Boolean = setupLogin(api.login(payload) ?: return false)
    suspend fun login(redirectUrl: String): Boolean =
        setupLogin(api.login(redirectUrl, synchronized(oauthPayload) { oauthPayload[api.idPrefix] }) ?: return false)
}

class SyncRepo(override val api: SyncAPI) : AuthRepo(api) {
    val syncIdName = api.syncIdName
    var requireLibraryRefresh: Boolean
        get() = api.requireLibraryRefresh
        set(value) {
            api.requireLibraryRefresh = value
        }

    suspend fun updateStatus(id: String, newStatus: SyncAPI.AbstractSyncStatus): Result<Boolean> =
        runCatching {
            val status = api.updateStatus(freshAuth() ?: return@runCatching false, id, newStatus)
            requireLibraryRefresh = true
            status
        }

    suspend fun status(id: String): Result<SyncAPI.AbstractSyncStatus?> = runCatching {
        api.status(freshAuth(), id)
    }

    suspend fun load(id: String): Result<SyncAPI.SyncResult?> = runCatching {
        api.load(freshAuth(), id)
    }

    suspend fun library(): Result<SyncAPI.LibraryMetadata?> = runCatching {
        api.library(freshAuth())
    }
}

abstract class SubtitleAPI : AuthAPI() {
    @WorkerThread
    open suspend fun search(auth: AuthData?, query: SubtitleSearch): List<SubtitleEntity>? = null

    @WorkerThread
    open suspend fun load(auth: AuthData?, subtitle: SubtitleEntity): String? = null

    @WorkerThread
    open suspend fun SubtitleResource.getResources(auth: AuthData?, subtitle: SubtitleEntity) {
        this.addUrl(load(auth, subtitle))
    }

    @WorkerThread
    suspend fun resource(auth: AuthData?, subtitle: SubtitleEntity): SubtitleResource {
        return SubtitleResource().apply { this.getResources(auth, subtitle) }
    }
}

class SubtitleRepo(override val api: SubtitleAPI) : AuthRepo(api) {
    companion object {
        data class SavedSearchResponse(
            val unixTime: Long,
            val response: List<SubtitleEntity>,
            val query: SubtitleSearch,
            val idPrefix: String,
        )

        data class SavedResourceResponse(
            val unixTime: Long,
            val response: SubtitleResource,
            val query: SubtitleEntity,
        )

        private val searchCache = atomicListOf<SavedSearchResponse>()
        private var searchCacheIndex: Int = 0
        private val resourceCache = atomicListOf<SavedResourceResponse>()
        private var resourceCacheIndex: Int = 0
        const val CACHE_SIZE = 20
    }

    @WorkerThread
    suspend fun resource(data: SubtitleEntity): Result<SubtitleResource> = runCatching {
        val cached = resourceCache.withLock {
            resourceCache.firstOrNull { it.query == data && (unixTime - it.unixTime) < 60 * 20 }?.response
        }
        if (cached != null) return@runCatching cached
        val returnValue = api.resource(freshAuth(), data)
        resourceCache.withLock {
            val add = SavedResourceResponse(unixTime, returnValue, data)
            if (resourceCache.size > CACHE_SIZE) {
                resourceCache[resourceCacheIndex] = add
                resourceCacheIndex = (resourceCacheIndex + 1) % CACHE_SIZE
            } else {
                resourceCache.add(add)
            }
        }
        returnValue
    }

    @WorkerThread
    suspend fun search(query: SubtitleSearch): Result<List<SubtitleEntity>> = runCatching {
        val cached = searchCache.withLock {
            searchCache.firstOrNull {
                it.idPrefix == idPrefix && it.query == query && (unixTime - it.unixTime) < 60 * 120
            }?.response
        }
        if (cached != null) return@runCatching cached
        val returnValue = api.search(freshAuth(), query) ?: emptyList()
        if (returnValue.isNotEmpty()) {
            val add = SavedSearchResponse(unixTime, returnValue, query, idPrefix)
            searchCache.withLock {
                if (searchCache.size > CACHE_SIZE) {
                    searchCache[searchCacheIndex] = add
                    searchCacheIndex = (searchCacheIndex + 1) % CACHE_SIZE
                } else {
                    searchCache.add(add)
                }
            }
        }
        returnValue
    }
}

abstract class BackupAPI : AuthAPI() {
    open val filename: String = "cloudstream-backup.json"
    open suspend fun downloadFile(auth: AuthData?): String? = null
    open suspend fun uploadFile(auth: AuthData?, data: String): String? = null
}

abstract class AccountManager {
    companion object {
        const val NONE_ID: Int = -1
        val malApi = MALApi()
        val kitsuApi = KitsuApi()
        val aniListApi = AniListApi()
        val simklApi = SimklApi()
        val localListApi = LocalList()
        val openSubtitlesApi = OpenSubtitlesApi()
        val addic7ed = Addic7ed()
        val subDlApi = SubDlApi()
        val subSourceApi = SubSourceApi()
        val animeSkipApi = AnimeSkipAuth()

        var cachedAccounts: MutableMap<String, Array<AuthData>>
        var cachedAccountIds: MutableMap<String, Int>

        const val ACCOUNT_TOKEN = "auth_tokens"
        const val ACCOUNT_IDS = "auth_ids"

        fun accounts(prefix: String): Array<AuthData> {
            require(prefix != "NONE")
            return runCatching {
                getKey<Array<AuthData>>(ACCOUNT_TOKEN, "${prefix}/${DataStoreHelper.currentAccount}")
            }.getOrNull() ?: emptyArray()
        }

        fun updateAccounts(prefix: String, array: Array<AuthData>) {
            require(prefix != "NONE")
            runCatching {
                setKey(ACCOUNT_TOKEN, "${prefix}/${DataStoreHelper.currentAccount}", array)
            }
            synchronized(cachedAccounts) {
                cachedAccounts[prefix] = array
            }
        }

        fun updateAccountsId(prefix: String, id: Int) {
            require(prefix != "NONE")
            runCatching {
                setKey(ACCOUNT_IDS, "${prefix}/${DataStoreHelper.currentAccount}", id)
            }
            synchronized(cachedAccountIds) {
                cachedAccountIds[prefix] = id
            }
        }

        val allApis: Array<AuthRepo> = arrayOf(
            SyncRepo(malApi),
            SyncRepo(kitsuApi),
            SyncRepo(aniListApi),
            SyncRepo(simklApi),
            SyncRepo(localListApi),
            SubtitleRepo(openSubtitlesApi),
            SubtitleRepo(addic7ed),
            SubtitleRepo(subDlApi),
            PlainAuthRepo(animeSkipApi),
            SubtitleRepo(subSourceApi)
        )

        fun updateAccountIds() {
            val ids = mutableMapOf<String, Int>()
            for (api in allApis) {
                ids[api.idPrefix] = runCatching {
                    getKey<Int>(ACCOUNT_IDS, "${api.idPrefix}/${DataStoreHelper.currentAccount}", NONE_ID)
                }.getOrNull() ?: NONE_ID
            }
            synchronized(cachedAccountIds) {
                cachedAccountIds = ids
            }
        }

        init {
            val data = mutableMapOf<String, Array<AuthData>>()
            val ids = mutableMapOf<String, Int>()
            for (api in allApis) {
                data[api.idPrefix] = accounts(api.idPrefix)
                ids[api.idPrefix] = runCatching {
                    getKey<Int>(ACCOUNT_IDS, "${api.idPrefix}/${DataStoreHelper.currentAccount}", NONE_ID)
                }.getOrNull() ?: NONE_ID
            }
            cachedAccounts = data
            cachedAccountIds = ids
        }

        fun initMainAPI() {
            LoadResponse.malIdPrefix = malApi.idPrefix
            LoadResponse.kitsuIdPrefix = kitsuApi.idPrefix
            LoadResponse.aniListIdPrefix = aniListApi.idPrefix
            LoadResponse.simklIdPrefix = simklApi.idPrefix
        }

        val subtitleProviders: Array<SubtitleRepo> = arrayOf(
            SubtitleRepo(openSubtitlesApi),
            SubtitleRepo(addic7ed),
            SubtitleRepo(subDlApi),
            SubtitleRepo(subSourceApi)
        )

        val syncApis: Array<SyncRepo> = arrayOf(
            SyncRepo(malApi),
            SyncRepo(kitsuApi),
            SyncRepo(aniListApi),
            SyncRepo(simklApi),
            SyncRepo(localListApi)
        )

        const val APP_STRING = "cloudstreamapp"
        const val APP_STRING_REPO = "cloudstreamrepo"
        const val APP_STRING_PLAYER = "cloudstreamplayer"
        const val APP_STRING_SEARCH = "cloudstreamsearch"
        const val APP_STRING_RESUME_WATCHING = "cloudstreamcontinuewatching"
        const val APP_STRING_SHARE = "csshare"

        fun secondsToReadable(seconds: Int, completedValue: String): String {
            var secondsLong = seconds.toLong()
            val days = TimeUnit.SECONDS.toDays(secondsLong)
            secondsLong -= TimeUnit.DAYS.toSeconds(days)
            val hours = TimeUnit.SECONDS.toHours(secondsLong)
            secondsLong -= TimeUnit.HOURS.toSeconds(hours)
            val minutes = TimeUnit.SECONDS.toMinutes(secondsLong)
            if (minutes < 0) return completedValue
            return "${if (days != 0L) "${days}d " else ""}${if (hours != 0L) "${hours}h " else ""}${minutes}m"
        }
    }
}
