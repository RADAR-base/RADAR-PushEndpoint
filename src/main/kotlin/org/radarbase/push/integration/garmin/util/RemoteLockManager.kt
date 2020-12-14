package org.radarbase.push.integration.garmin.util

import java.io.Closeable

interface RemoteLockManager {
    fun acquireLock(name: String): RemoteLock?
    fun <T> tryRunLocked(name: String, action: () -> T): T? = acquireLock(name)?.use {
        action()
    }

    interface RemoteLock: Closeable
}
