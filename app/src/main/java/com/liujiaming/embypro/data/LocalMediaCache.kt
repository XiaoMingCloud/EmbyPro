package com.liujiaming.embypro

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

class LocalMediaCache(context: Context) {
    private val rootDir = File(context.cacheDir, "media_cache").apply { mkdirs() }
    private val jsonDir = File(rootDir, "json").apply { mkdirs() }
    private val imageDir = File(rootDir, "images").apply { mkdirs() }

    fun readJson(key: String, maxAgeMs: Long): String? = synchronized(lock) {
        val file = File(jsonDir, "${hashKey(key)}.json")
        if (!file.exists()) return null
        if (maxAgeMs > 0L && System.currentTimeMillis() - file.lastModified() > maxAgeMs) {
            return null
        }
        touch(file)
        file.readText(Charsets.UTF_8)
    }

    fun readJsonAnyAge(key: String): String? = synchronized(lock) {
        val file = File(jsonDir, "${hashKey(key)}.json")
        if (!file.exists()) return null
        touch(file)
        file.readText(Charsets.UTF_8)
    }

    fun writeJson(key: String, value: String) = synchronized(lock) {
        val file = File(jsonDir, "${hashKey(key)}.json")
        file.writeText(value, Charsets.UTF_8)
        touch(file)
        trimToSize()
    }

    fun readBitmap(key: String): Bitmap? = synchronized(lock) {
        val file = File(imageDir, "${hashKey(key)}.img")
        if (!file.exists()) return null
        touch(file)
        BitmapFactory.decodeFile(file.absolutePath)
    }

    fun writeBitmapBytes(key: String, bytes: ByteArray) = synchronized(lock) {
        val file = File(imageDir, "${hashKey(key)}.img")
        FileOutputStream(file).use { it.write(bytes) }
        touch(file)
        trimToSize()
    }

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

    private fun touch(file: File) {
        file.setLastModified(System.currentTimeMillis())
    }

    private fun hashKey(key: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(key.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val MAX_CACHE_SIZE_BYTES = 1024L * 1024L * 1024L
        private val lock = Any()
    }
}
