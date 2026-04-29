package com.liujiaming.embypro

/**
 * Keeps lightweight playback address metadata separate from the media segment cache.
 * The underlying PlayerCache still owns the disk-backed Media3 cache.
 */
object VideoPlaybackAddressCache {
    data class Entry(
        val itemId: String,
        val playbackUrl: String,
        val playbackPositionMs: Long,
        val title: String,
        val mediaSourceId: String,
        val playSessionId: String
    )

    fun save(entry: Entry) {
        PlayerCache.savePrefetchedPlayback(
            PlayerCache.PrefetchedPlayback(
                itemId = entry.itemId,
                playbackUrl = entry.playbackUrl,
                playbackPositionMs = entry.playbackPositionMs,
                title = entry.title,
                mediaSourceId = entry.mediaSourceId,
                playSessionId = entry.playSessionId
            )
        )
    }

    fun take(itemId: String): Entry? {
        return PlayerCache.takePrefetchedPlayback(itemId)?.let { playback ->
            Entry(
                itemId = playback.itemId,
                playbackUrl = playback.playbackUrl,
                playbackPositionMs = playback.playbackPositionMs,
                title = playback.title,
                mediaSourceId = playback.mediaSourceId,
                playSessionId = playback.playSessionId
            )
        }
    }
}
