package com.liujiaming.embypro

import android.app.Application

class EmbyProApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MusicMiniPlayerOverlay.install(this)
    }
}
