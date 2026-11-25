package com.lagradost.cloudstream3.utils.videoskip

import com.lagradost.cloudstream3.syncproviders.AuthAPI

class AnimeSkipAuth : AuthAPI() {
    override val name = "AnimeSkip"
    override val idPrefix = "animeskip"
    override val hasInApp = true
}
