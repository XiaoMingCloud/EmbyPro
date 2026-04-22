package com.liujiaming.embypro

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Local disk cache for media content (JSON data and images).
 * Provides thread-safe read/write operations with automatic cache size management.
 * Cache is stored in the app's cache directory with a maximum size of 1GB.
 */
class LocalMediaCache(context: Context) {
    private val rootDir = File(context.cacheDir, "media_cache").apply { mkdirs() }
    private val jsonDir = File(rootDir, "json").apply { mkdirs() }
    private val imageDir = File(rootDir, "images").apply { mkdirs() }

    /**
     * Reads JSON from cache if it exists and is not expired.
     *
     * @param key Cache key (will be hashed)
     * @param maxAgeMs Maximum age in milliseconds (0 for no expiry check)
     * @return Cached JSON string or null if not found/expired
     */
    fun readJson(key: String, maxAgeMs: Long): String? = synchronized(lock) {
        val file = File(jsonDir, "${hashKey(key)}.json")
        if (!file.exists()) return null
        if (maxAgeMs > 0L && System.currentTimeMillis() - file.lastModified() > maxAgeMs) {
            return null
        }
        touch(file)
        file.readText(Charsets.UTF_8)
    }

    /**
     * Reads JSON from cache regardless of age.
     * Used as fallback when fresh cache is not available.
     */
    fun readJsonAnyAge(key: String): String? = synchronized(lock) {
        val file = File(jsonDir, "${hashKey(key)}.json")
        if (!file.exists()) return null
        touch(file)
        file.readText(Charsets.UTF_8)
    }

    /**
     * Writes JSON to cache and triggers size trimming if needed.
     */
    fun writeJson(key: String, value: String) = synchronized(lock) {
        val file = File(jsonDir, "${hashKey(key)}.json")
        file.writeText(value, Charsets.UTF_8)
        touch(file)
        trimToSize()
    }

    /**
     * Reads bitmap image from disk cache.
     */
    fun readBitmap(key: String): Bitmap? = synchronized(lock) {
        val file = File(imageDir, "${hashKey(key)}.img")
        if (!file.exists()) return null
        touch(file)
        BitmapFactory.decodeFile(file.absolutePath)
    }

    /**
     * Writes bitmap bytes to disk cache and triggers size trimming if needed.
     */
    fun writeBitmapBytes(key: String, bytes: ByteArray) = synchronized(lock) {
        val file = File(imageDir, "${hashKey(key)}.img")
        FileOutputStream(file).use { it.write(bytes) }
        touch(file)
        trimToSize()
    }

    /**
     * Trims cache size to maximum limit by deleting oldest files first.
     */
    private fun trimToSize() {
        val files = rootDir.walkTopDown()
            .filter { it.isFile }
            .toMutableList()
        var totalSize = files.sumOf { it.length() }
        if (totalSize <= MAX_CACHE_SIZE_BYTES) return

        files.sortBy { it.lastModified() }
        for (file in files) {
            if (totalSize <= MAX_CACHE_SIZE_BYTES) break
            val size = file.length()
            if (file.delete()) {
                totalSize -= size
            }
        }
    }

    /**
     * Updates file's last modified timestamp to current time.
     * Used for LRU tracking in cache trimming.
     */
    private fun touch(file: File) {
        file.setLastModified(System.currentTimeMillis())
    }

    /**
     * Creates MD5 hash of a cache key for safe file naming.
     */
    private fun hashKey(key: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(key.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val MAX_CACHE_SIZE_BYTES = 1024L * 1024L * 1024L
        private val lock = Any()
    }
}
