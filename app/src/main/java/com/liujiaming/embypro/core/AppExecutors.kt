package com.liujiaming.embypro

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Central executor provider for background tasks across the application.
 * Provides a thread pool sized based on available CPU cores (minimum 4 threads).
 */
object AppExecutors {
    /**
     * Lazy-initialized I/O executor service for background operations.
     * Thread pool size is determined by available processors, with a minimum of 4 threads.
     */
    val io: ExecutorService by lazy {
        Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors().coerceAtLeast(4)
        )
    }

    fun shutdown() {
        if (io.isShutdown.not()) {
            io.shutdown()
        }
    }
}
