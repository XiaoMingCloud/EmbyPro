package com.liujiaming.embypro

import okhttp3.OkHttpClient

/**
 * Singleton provider for the OkHttp client used across the application.
 * Lazy-initialized and shared for connection pooling and efficient resource usage.
 */
object NetworkClientProvider {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder().build()
    }
}
