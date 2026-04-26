package com.liujiaming.embypro

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import okhttp3.Request
import java.util.concurrent.Executors

/**
 * Image loading utility with two-level caching (memory and disk).
 * Loads images from network or local URIs and displays them in ImageViews.
 * Uses LRU cache for memory and LocalMediaCache for disk storage.
 */
object EmbyImageLoader {
    private val client = NetworkClientProvider.client
    private val memoryCache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }
    private val executor = Executors.newFixedThreadPool(4)
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Loads an image from URL or local URI into an ImageView.
     * Checks memory cache first, then disk cache, then downloads if needed.
     *
     * @param imageView The target ImageView to display the image
     * @param url Image URL or local URI
     * @token Authentication token for Emby API requests
     * @param onFailure Callback invoked when image loading fails
     * @param onSuccess Callback invoked with the loaded bitmap on success
     */
    fun load(
        imageView: ImageView,
        url: String?,
        token: String?,
        onFailure: (() -> Unit)? = null,
        onSuccess: ((Bitmap) -> Unit)? = null
    ) {
        if (url.isNullOrBlank()) {
            onFailure?.invoke()
            return
        }

        imageView.tag = url
        loadBitmap(imageView.context.applicationContext, url, token) { bitmap ->
            if (imageView.tag != url) return@loadBitmap
            if (bitmap != null) {
                imageView.clearColorFilter()
                imageView.background = null
                imageView.setImageBitmap(bitmap)
                onSuccess?.invoke(bitmap)
            } else {
                onFailure?.invoke()
            }
        }
    }

    fun loadBitmap(
        context: Context,
        url: String?,
        token: String?,
        onResult: (Bitmap?) -> Unit
    ) {
        if (url.isNullOrBlank()) {
            onResult(null)
            return
        }

        val cached = memoryCache.get(url)
        if (cached != null) {
            onResult(cached)
            return
        }

        val appContext = context.applicationContext
        val diskCache = LocalMediaCache(appContext)
        val diskBitmap = diskCache.readBitmap(url)
        if (diskBitmap != null) {
            memoryCache.put(url, diskBitmap)
            onResult(diskBitmap)
            return
        }

        executor.execute {
            val imageBytes = loadImageBytes(appContext, url, token)
            val bitmap = imageBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }

            mainHandler.post {
                if (bitmap != null) {
                    memoryCache.put(url, bitmap)
                    val uri = Uri.parse(url)
                    if (!isLocalUri(uri)) {
                        diskCache.writeBitmapBytes(url, imageBytes)
                    }
                }
                onResult(bitmap)
            }
        }
    }

    /**
     * Checks if a URI is a local resource (content, file, or android.resource scheme).
     */
    private fun isLocalUri(uri: Uri): Boolean {
        return when (uri.scheme) {
            ContentResolver.SCHEME_CONTENT,
            ContentResolver.SCHEME_FILE,
            ContentResolver.SCHEME_ANDROID_RESOURCE -> true
            else -> false
        }
    }

    private fun loadImageBytes(context: Context, url: String, token: String?): ByteArray? {
        return try {
            val uri = Uri.parse(url)
            if (isLocalUri(uri)) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    input.readBytes()
                }
            } else {
                val request = Request.Builder()
                    .url(url)
                    .apply {
                        if (!token.isNullOrBlank()) {
                            header("X-Emby-Token", token)
                        }
                    }
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    response.body?.bytes()
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}
