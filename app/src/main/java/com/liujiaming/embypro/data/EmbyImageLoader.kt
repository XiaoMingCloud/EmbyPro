package com.liujiaming.embypro

import android.content.ContentResolver
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
        val cached = memoryCache.get(url)
        if (cached != null) {
            imageView.clearColorFilter()
            imageView.background = null
            imageView.setImageBitmap(cached)
            onSuccess?.invoke(cached)
            return
        }

        val diskCache = LocalMediaCache(imageView.context.applicationContext)
        val diskBitmap = diskCache.readBitmap(url)
        if (diskBitmap != null) {
            memoryCache.put(url, diskBitmap)
            imageView.clearColorFilter()
            imageView.background = null
            imageView.setImageBitmap(diskBitmap)
            onSuccess?.invoke(diskBitmap)
            return
        }

        executor.execute {
            val imageBytes = try {
                val uri = Uri.parse(url)
                if (isLocalUri(uri)) {
                    imageView.context.contentResolver.openInputStream(uri)?.use { input ->
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
            val bitmap = imageBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }

            mainHandler.post {
                if (imageView.tag != url) return@post
                if (bitmap != null) {
                    memoryCache.put(url, bitmap)
                    val uri = Uri.parse(url)
                    if (!isLocalUri(uri)) {
                        imageBytes?.let { diskCache.writeBitmapBytes(url, it) }
                    }
                    imageView.clearColorFilter()
                    imageView.background = null
                    imageView.setImageBitmap(bitmap)
                    onSuccess?.invoke(bitmap)
                } else {
                    onFailure?.invoke()
                }
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
}
