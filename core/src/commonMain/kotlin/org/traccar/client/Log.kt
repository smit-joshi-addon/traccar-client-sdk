package org.traccar.client

import kotlin.concurrent.Volatile
import kotlin.time.Clock

internal fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

internal object Log {

    @Volatile
    var store: LogStore? = null

    @Volatile
    var retention: Int = 5000

    fun log(message: String) {
        store?.let {
            it.insert(message)
            it.trim(retention)
        }
    }
}
