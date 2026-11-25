package com.lagradost.cloudstream3.syncproviders.providers

import com.lagradost.cloudstream3.syncproviders.SubtitleAPI
import com.lagradost.cloudstream3.syncproviders.SyncAPI
import com.lagradost.cloudstream3.syncproviders.SyncIdName

class MALApi : SyncAPI() {
    override var name = "MAL"
    override val idPrefix = "mal"
    override val mainUrl = "https://myanimelist.net"
    override val syncIdName = SyncIdName.MyAnimeList
    override val hasOAuth2 = true
    override val redirectUrlIdentifier = "mallogin"
}

class KitsuApi : SyncAPI() {
    override var name = "Kitsu"
    override val idPrefix = "kitsu"
    override val mainUrl = "https://kitsu.io"
    override val syncIdName = SyncIdName.Kitsu
}

class SimklApi : SyncAPI() {
    override var name = "Simkl"
    override val idPrefix = "simkl"
    override val mainUrl = "https://simkl.com"
    override val syncIdName = SyncIdName.Simkl
    override val hasOAuth2 = true
    override val redirectUrlIdentifier = "simkllogin"
}

class LocalList : SyncAPI() {
    override var name = "Local"
    override val idPrefix = "local"
    override val syncIdName = SyncIdName.LocalList
    override val requiresLogin = false
}

class OpenSubtitlesApi : SubtitleAPI() {
    override val name = "OpenSubtitles"
    override val idPrefix = "opensubtitles"
    override val requiresLogin = false
}

class Addic7ed : SubtitleAPI() {
    override val name = "Addic7ed"
    override val idPrefix = "addic7ed"
    override val requiresLogin = false
}

class SubDlApi : SubtitleAPI() {
    override val name = "SubDL"
    override val idPrefix = "subdl"
    override val requiresLogin = false
}

class SubSourceApi : SubtitleAPI() {
    override val name = "SubSource"
    override val idPrefix = "subsource"
    override val requiresLogin = false
}
