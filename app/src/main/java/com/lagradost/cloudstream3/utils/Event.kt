package com.lagradost.cloudstream3.utils

import java.util.concurrent.CopyOnWriteArraySet

/**
 * Upstream CloudStream3 Event observer class used by plugins subscribing to MainActivity lifecycle events.
 */
class Event<T> {
    private val observers = CopyOnWriteArraySet<(T) -> Unit>()

    operator fun plusAssign(observer: (T) -> Unit) {
        observers.add(observer)
    }

    operator fun minusAssign(observer: (T) -> Unit) {
        observers.remove(observer)
    }

    operator fun invoke(value: T) {
        for (observer in observers) {
            runCatching { observer(value) }
        }
    }
}
