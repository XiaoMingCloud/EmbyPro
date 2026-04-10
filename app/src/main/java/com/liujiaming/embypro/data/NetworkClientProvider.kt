package com.liujiaming.embypro

import okhttp3.OkHttpClient

object NetworkClientProvider {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder().build()
    }
}
