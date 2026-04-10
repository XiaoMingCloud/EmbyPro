package com.liujiaming.embypro

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

object AppExecutors {
    val io: ExecutorService by lazy {
        Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors().coerceAtLeast(4)
        )
    }
}
