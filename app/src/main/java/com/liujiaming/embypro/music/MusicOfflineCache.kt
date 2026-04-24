package com.liujiaming.embypro

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * Persists played music tracks to local storage for offline playback.
 * Stores a lightweight metadata index plus the downloaded audio file path.
 */
class MusicOfflineCache(context: Context) {
    private val appContext = context.applicationContext
    private val rootDir = File(appContext.filesDir, "music_offline").apply { mkdirs() }
    private val audioDir = File(rootDir, "audio").apply { mkdirs() }
    private val indexFile = File(rootDir, "index.json")
    private val client = NetworkClientProvider.client

    fun buildLocalPage(
        connection: ServerConnection,
        query: String = ""
    ): Result<MusicListPageUiModel> = runCatching {
        val entries = synchronized(lock) {
            readEntriesLocked()
                .filter { it.baseUrl == connection.baseUrl && it.userId == connection.userId }
                .filter {
                    query.isBlank() || it.title.contains(query, ignoreCase = true) ||
                        it.subtitle.contains(query, ignoreCase = true)
                }
                .sortedByDescending { it.cachedAtMs }
        }

        MusicListPageUiModel(
            title = appContext.getString(R.string.music_library_entry_local),
            subtitle = appContext.getString(R.string.music_local_list_subtitle, entries.size),
            items = entries.map { entry ->
                MusicListEntryUiModel(
                    id = entry.itemId,
                    title = entry.title,
                    subtitle = entry.subtitle,
                    detail = entry.runtimeMs.takeIf { it > 0L }?.let(::formatDuration)
                        ?: appContext.getString(R.string.music_local_cached_detail),
                    imageUrl = entry.coverImageUrl,
                    kind = MusicEntryKind.SONG,
                    browseType = MusicBrowseType.LOCAL,
                    itemType = "Audio",
                    runtimeTicks = entry.runtimeMs * 10_000L
                )
            },
            totalCount = entries.size,
            isSongList = true,
            libraryId = entries.firstOrNull { it.libraryId.isNotBlank() }?.libraryId.orEmpty()
        )
    }

    fun getCachedPlayback(connection: ServerConnection, itemId: String): MusicPlaybackUiModel? = synchronized(lock) {
        val entry = readEntriesLocked()
            .firstOrNull { it.baseUrl == connection.baseUrl && it.userId == connection.userId && it.itemId == itemId }
            ?: return null
        val file = File(entry.localPath)
        if (!file.exists()) {
            removeLocked(connection, itemId)
            return null
        }
        MusicPlaybackUiModel(
            itemId = entry.itemId,
            title = entry.title,
            subtitle = entry.subtitle,
            coverImageUrl = entry.coverImageUrl,
            playbackUrl = Uri.fromFile(file).toString(),
            playbackPositionMs = entry.playbackPositionMs,
            isFavorite = entry.isFavorite,
            runtimeMs = entry.runtimeMs,
            isOfflineCached = true
        )
    }

    fun countCachedTracks(connection: ServerConnection): Int = synchronized(lock) {
        readEntriesLocked().count { it.baseUrl == connection.baseUrl && it.userId == connection.userId }
    }

    fun cachePlayback(connection: ServerConnection, libraryId: String?, playback: MusicPlaybackUiModel) {
        if (playback.itemId.isBlank() || playback.playbackUrl.isBlank() || playback.isOfflineCached) return
        val entryKey = buildEntryKey(connection, playback.itemId)
        synchronized(lock) {
            if (!inFlightDownloads.add(entryKey)) return
        }
        downloadExecutor.execute {
            try {
                val targetFile = File(audioDir, "${hashKey(entryKey)}.audio")
                if (!targetFile.exists() || targetFile.length() <= 0L) {
                    val tempFile = File(audioDir, "${hashKey(entryKey)}.tmp")
                    val request = okhttp3.Request.Builder()
                        .url(playback.playbackUrl)
                        .header("X-Emby-Token", connection.accessToken)
                        .get()
                        .build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@use
                        response.body?.byteStream()?.use { input ->
                            FileOutputStream(tempFile).use { output ->
                                input.copyTo(output)
                            }
                        } ?: return@use
                    }
                    if (tempFile.exists() && tempFile.length() > 0L) {
                        if (targetFile.exists()) {
                            targetFile.delete()
                        }
                        tempFile.renameTo(targetFile)
                    } else {
                        tempFile.delete()
                    }
                }
                if (!targetFile.exists() || targetFile.length() <= 0L) return@execute

                synchronized(lock) {
                    val entries = readEntriesLocked().toMutableList()
                    entries.removeAll {
                        it.baseUrl == connection.baseUrl &&
                            it.userId == connection.userId &&
                            it.itemId == playback.itemId
                    }
                    entries.add(
                        CachedMusicEntry(
                            baseUrl = connection.baseUrl,
                            userId = connection.userId,
                            libraryId = libraryId.orEmpty(),
                            itemId = playback.itemId,
                            title = playback.title,
                            subtitle = playback.subtitle,
                            coverImageUrl = playback.coverImageUrl,
                            localPath = targetFile.absolutePath,
                            playbackPositionMs = playback.playbackPositionMs,
                            isFavorite = playback.isFavorite,
                            runtimeMs = playback.runtimeMs,
                            cachedAtMs = System.currentTimeMillis()
                        )
                    )
                    trimToSizeLocked(entries)
                    writeEntriesLocked(entries)
                }
            } catch (_: Exception) {
                // Offline cache should never interrupt foreground playback.
            } finally {
                synchronized(lock) {
                    inFlightDownloads.remove(entryKey)
                }
            }
        }
    }

    fun updatePlaybackProgress(connection: ServerConnection, itemId: String, playbackPositionMs: Long) = synchronized(lock) {
        val entries = readEntriesLocked().toMutableList()
        val index = entries.indexOfFirst {
            it.baseUrl == connection.baseUrl && it.userId == connection.userId && it.itemId == itemId
        }
        if (index < 0) return
        val current = entries[index]
        entries[index] = current.copy(playbackPositionMs = playbackPositionMs.coerceAtLeast(0L))
        writeEntriesLocked(entries)
    }

    fun updateFavoriteState(connection: ServerConnection, itemId: String, favorite: Boolean) = synchronized(lock) {
        val entries = readEntriesLocked().toMutableList()
        val index = entries.indexOfFirst {
            it.baseUrl == connection.baseUrl && it.userId == connection.userId && it.itemId == itemId
        }
        if (index < 0) return
        entries[index] = entries[index].copy(isFavorite = favorite)
        writeEntriesLocked(entries)
    }

    fun remove(connection: ServerConnection, itemId: String) = synchronized(lock) {
        removeLocked(connection, itemId)
    }

    private fun removeLocked(connection: ServerConnection, itemId: String) {
        val entries = readEntriesLocked().toMutableList()
        val iterator = entries.iterator()
        var changed = false
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.baseUrl == connection.baseUrl && entry.userId == connection.userId && entry.itemId == itemId) {
                File(entry.localPath).delete()
                iterator.remove()
                changed = true
            }
        }
        if (changed) {
            writeEntriesLocked(entries)
        }
    }

    private fun readEntriesLocked(): List<CachedMusicEntry> {
        if (!indexFile.exists()) return emptyList()
        val json = runCatching { JSONArray(indexFile.readText(Charsets.UTF_8)) }.getOrElse { JSONArray() }
        val entries = mutableListOf<CachedMusicEntry>()
        for (index in 0 until json.length()) {
            val item = json.optJSONObject(index) ?: continue
            val entry = CachedMusicEntry(
                baseUrl = item.optString("baseUrl"),
                userId = item.optString("userId"),
                libraryId = item.optString("libraryId"),
                itemId = item.optString("itemId"),
                title = item.optString("title"),
                subtitle = item.optString("subtitle"),
                coverImageUrl = item.optString("coverImageUrl").takeIf { it.isNotBlank() },
                localPath = item.optString("localPath"),
                playbackPositionMs = item.optLong("playbackPositionMs"),
                isFavorite = item.optBoolean("isFavorite"),
                runtimeMs = item.optLong("runtimeMs"),
                cachedAtMs = item.optLong("cachedAtMs")
            )
            if (entry.itemId.isBlank() || entry.localPath.isBlank()) continue
            if (!File(entry.localPath).exists()) continue
            entries.add(entry)
        }
        return entries
    }

    private fun writeEntriesLocked(entries: List<CachedMusicEntry>) {
        val json = JSONArray()
        entries.forEach { entry ->
            json.put(
                JSONObject()
                    .put("baseUrl", entry.baseUrl)
                    .put("userId", entry.userId)
                    .put("libraryId", entry.libraryId)
                    .put("itemId", entry.itemId)
                    .put("title", entry.title)
                    .put("subtitle", entry.subtitle)
                    .put("coverImageUrl", entry.coverImageUrl)
                    .put("localPath", entry.localPath)
                    .put("playbackPositionMs", entry.playbackPositionMs)
                    .put("isFavorite", entry.isFavorite)
                    .put("runtimeMs", entry.runtimeMs)
                    .put("cachedAtMs", entry.cachedAtMs)
            )
        }
        indexFile.writeText(json.toString(), Charsets.UTF_8)
    }

    private fun trimToSizeLocked(entries: MutableList<CachedMusicEntry>) {
        var totalSize = entries.sumOf { File(it.localPath).takeIf(File::exists)?.length() ?: 0L }
        if (totalSize <= MAX_CACHE_SIZE_BYTES) return
        entries.sortBy { it.cachedAtMs }
        val iterator = entries.iterator()
        while (iterator.hasNext() && totalSize > MAX_CACHE_SIZE_BYTES) {
            val entry = iterator.next()
            val file = File(entry.localPath)
            val fileSize = file.takeIf(File::exists)?.length() ?: 0L
            file.delete()
            iterator.remove()
            totalSize -= fileSize
        }
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun buildEntryKey(connection: ServerConnection, itemId: String): String {
        return "${connection.baseUrl}::${connection.userId}::$itemId"
    }

    private fun hashKey(value: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private data class CachedMusicEntry(
        val baseUrl: String,
        val userId: String,
        val libraryId: String,
        val itemId: String,
        val title: String,
        val subtitle: String,
        val coverImageUrl: String?,
        val localPath: String,
        val playbackPositionMs: Long,
        val isFavorite: Boolean,
        val runtimeMs: Long,
        val cachedAtMs: Long
    )

    companion object {
        private const val MAX_CACHE_SIZE_BYTES = 1024L * 1024L * 1024L
        private val lock = Any()
        private val inFlightDownloads = mutableSetOf<String>()
        private val downloadExecutor = Executors.newSingleThreadExecutor()
    }
}
