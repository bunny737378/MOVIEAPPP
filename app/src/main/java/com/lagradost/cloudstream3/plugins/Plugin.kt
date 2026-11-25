package com.lagradost.cloudstream3.plugins

import android.content.Context
import android.content.res.Resources
import android.util.Log
import androidx.compose.runtime.Stable
import com.lagradost.cloudstream3.actions.VideoClickAction
import com.lagradost.cloudstream3.actions.VideoClickActionHolder
import kotlin.Throws

/**
 * Annotated with `@Stable` so Compose emits the `$stable` static field that
 * newer CloudStream .cs3 plugins read during `<clinit>`.
 */
@Stable
abstract class Plugin : BasePlugin() {
    /**
     * Called when your Plugin is loaded
     * @param context Context
     */
    @Throws(Throwable::class)
    open fun load(context: Context) {
        // If not overridden by an extension then try the cross-platform load()
        load()
    }

    /**
     * Used to register VideoClickAction instances
     * @param element VideoClickAction you want to register
     */
    fun registerVideoClickAction(element: VideoClickAction) {
        Log.i(PLUGIN_TAG, "Adding ${element.name} VideoClickAction")
        element.sourcePlugin = this.filename
        VideoClickActionHolder.allVideoClickActions.add(element)
    }

    /**
     * This will contain your resources if you specified requiresResources in gradle
     */
    var resources: Resources? = null

    /**
     * This will add a button in the settings allowing you to add custom settings
     */
    var openSettings: ((context: Context) -> Unit)? = null
}
