package com.lagradost.cloudstream3

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.fragment.app.FragmentActivity
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.Event
import com.lagradost.nicehttp.Requests
import com.lagradost.nicehttp.ResponseParser
import java.io.File
import java.util.Collections
import kotlin.reflect.KClass

// Short name for requests client to make it nicer to use
private val jsonResponseParser = object : ResponseParser {
    override fun <T : Any> parse(text: String, kClass: KClass<T>): T {
        return parseJson(text, kClass)
    }

    override fun <T : Any> parseSafe(text: String, kClass: KClass<T>): T? {
        return try {
            parse(text, kClass)
        } catch (_: Exception) {
            null
        }
    }

    override fun writeValueAsString(obj: Any): String {
        return obj.toJson()
    }
}

/** The default networking helper. This helper performs SSL checks.
 * If you need to make requests to websites with invalid SSL certificates use insecureApp instead. */
var app = Requests(responseParser = jsonResponseParser).apply {
    defaultHeaders = mapOf("user-agent" to USER_AGENT)
}

/** Same as the default app networking helper, but this instance ignores SSL certificates.
 * This should NEVER be used for sensitive networking operations such as logins. Only use this when required. */
@UnsafeSSL
var insecureApp = Requests(responseParser = jsonResponseParser).apply {
    defaultHeaders = mapOf("user-agent" to USER_AGENT)
}

/**
 * Compatibility shadow of `com.lagradost.cloudstream3.MainActivity` + `Companion`
 * matching upstream CloudStream3 bytecode signatures so plugins referencing
 * `MainActivity::class.java`, `MainActivity.afterPluginsLoadedEvent`, `activityResultLauncher`,
 * `handleAppIntentUrl`, etc. link cleanly without NoClassDefFoundError/NoSuchFieldError.
 */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching {
            startActivity(
                Intent(this, com.xtsream.app.MainActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            )
        }
        finish()
    }

    companion object {
        @Volatile
        var activityResultLauncher: ActivityResultLauncher<Intent>? = null

        @Volatile
        var lastError: String? = null

        @Volatile
        var nextSearchQuery: String? = null

        @Volatile
        var filesToDelete: MutableSet<String> = Collections.synchronizedSet(HashSet())

        val afterPluginsLoadedEvent = Event<Boolean>()
        val mainPluginsLoadedEvent = Event<Boolean>()
        val afterRepositoryLoadedEvent = Event<Boolean>()
        val bookmarksUpdatedEvent = Event<Boolean>()
        val reloadHomeEvent = Event<Boolean>()
        val reloadLibraryEvent = Event<Boolean>()
        val reloadAccountEvent = Event<Boolean>()

        fun setLastError(context: Context) {
            runCatching {
                val errorFile = File(context.filesDir, "last_error")
                if (errorFile.exists() && errorFile.isFile) errorFile.delete()
            }
            lastError = null
        }

        fun deleteFileOnExit(file: File) {
            runCatching { if (file.exists()) file.delete() }
        }

        fun centerView(view: View) {
            runCatching { view.requestFocus() }
        }

        fun handleAppIntentUrl(
            activity: FragmentActivity,
            url: String,
            isWebview: Boolean = false,
            extraArgs: Bundle? = null,
        ): Boolean {
            return false
        }
    }
}
