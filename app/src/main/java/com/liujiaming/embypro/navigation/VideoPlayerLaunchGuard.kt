package com.liujiaming.embypro

/**
 * Prevents multiple PlayerActivity launches from being created concurrently.
 * This is used to guard against repeated taps while detail/playback data is still loading.
 */
object VideoPlayerLaunchGuard {
    private val lock = Any()
    private var launchInProgress = false

    fun tryAcquire(): Boolean {
        synchronized(lock) {
            if (launchInProgress) return false
            launchInProgress = true
            return true
        }
    }

    fun release() {
        synchronized(lock) {
            launchInProgress = false
        }
    }
}
