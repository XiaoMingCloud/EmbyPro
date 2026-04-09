package com.liujiaming.embypro

import android.content.Context
import android.content.SharedPreferences
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File
import java.util.concurrent.Executors

@androidx.annotation.OptIn(UnstableApi::class)
object PlayerCache {
    @Volatile
    private var cache: SimpleCache? = null
    private val prefetchExecutor = Executors.newSingleThreadExecutor()

    fun get(context: Context): SimpleCache {
        return cache ?: synchronized(this) {
            cache ?: buildCache(context.applicationContext).also { cache = it }
        }
    }

    fun markPlayed(context: Context, itemId: String) {
        prefs(context).edit().remove(itemId).apply()
    }

    fun cleanupExpiredPrefetch(context: Context, protectedItemIds: Set<String> = emptySet()) {
        val now = System.currentTimeMillis()
        val prefs = prefs(context)
        val editor = prefs.edit()
        prefs.all.forEach { (itemId, value) ->
            if (itemId in protectedItemIds) return@forEach
            val payload = value as? String ?: return@forEach
            val parts = payload.split("|")
            if (parts.size != 2) {
                editor.remove(itemId)
                return@forEach
            }
            val url = parts[0]
            val timestamp = parts[1].toLongOrNull() ?: 0L
            if (now - timestamp >= PREFETCH_EXPIRE_MS) {
                runCatching { get(context).removeResource(url) }
                editor.remove(itemId)
            }
        }
        editor.apply()
    }

    fun prefetchVideos(context: Context, accessToken: String, videos: List<Pair<String, String>>) {
        if (videos.isEmpty()) return
        val appContext = context.applicationContext
        prefetchExecutor.execute {
            videos.forEach { (itemId, url) ->
                if (url.isBlank()) return@forEach
                runCatching {
                    val upstreamFactory = DefaultHttpDataSource.Factory().apply {
                        if (accessToken.isNotBlank()) {
                            setDefaultRequestProperties(mapOf("X-Emby-Token" to accessToken))
                        }
                    }
                    val cacheDataSource = CacheDataSource.Factory()
                        .setCache(get(appContext))
                        .setUpstreamDataSourceFactory(upstreamFactory)
                        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                        .createDataSourceForDownloading()
                    val dataSpec = DataSpec.Builder()
                        .setUri(url)
                        .setPosition(0)
                        .setLength(PREFETCH_BYTES)
                        .setKey(url)
                        .build()
                    CacheWriter(cacheDataSource, dataSpec, null, null).cache()
                    prefs(appContext).edit()
                        .putString(itemId, "$url|${System.currentTimeMillis()}")
                        .apply()
                }
            }
        }
    }

    private fun buildCache(context: Context): SimpleCache {
        val cacheDir = File(context.cacheDir, "video_cache")
        return SimpleCache(
            cacheDir,
            LeastRecentlyUsedCacheEvictor(150L * 1024L * 1024L),
            StandaloneDatabaseProvider(context)
        )
    }

    private fun prefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private const val PREFS_NAME = "player_prefetch_cache"
    private const val PREFETCH_BYTES = 24L * 1024L * 1024L
    private const val PREFETCH_EXPIRE_MS = 20L * 60L * 1000L
}
