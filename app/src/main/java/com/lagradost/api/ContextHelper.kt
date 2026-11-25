package com.lagradost.api

import android.content.Context
import java.lang.ref.WeakReference

private var contextRef: WeakReference<Context>? = null

fun getContext(): Any? = contextRef?.get()

fun setContext(context: Any?) {
    contextRef = if (context is Context) {
        WeakReference(context)
    } else {
        null
    }
}
