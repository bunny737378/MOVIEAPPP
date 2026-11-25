package com.lagradost.cloudstream3.actions

import android.content.Context
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Lightweight compatibility stub for CloudStream plugins that call `registerVideoClickAction`.
 */
abstract class VideoClickAction {
    abstract val name: String
    open val oneSource: Boolean = false
    var sourcePlugin: String? = null
    open fun shouldShow(context: Context?, video: Any?): Boolean = false
    open suspend fun runAction(context: Context?, video: Any?, result: Any?, source: Any?) = Unit
}

object VideoClickActionHolder {
    val allVideoClickActions = CopyOnWriteArrayList<VideoClickAction>()
}
