package com.liujiaming.embypro

import android.app.Application

/**
 * Application class for EmbyPro.
 * Initializes global components like the music mini player overlay on app startup.
 */
class EmbyProApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Install the global music mini player overlay
        MusicMiniPlayerOverlay.install(this)
    }

    override fun onTerminate() {
        super.onTerminate()
        AppExecutors.shutdown()
        CoverColorExtractor.shutdown()
        EmbyImageLoader.shutdown()
        PlayerCache.shutdown()
    }
}
