package com.liujiaming.embypro

import android.net.Uri
import android.os.Handler
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import java.util.concurrent.ExecutorService
import kotlin.math.abs

data class VideoPreloadedPlayback(
    val player: ExoPlayer,
    val playlistIndex: Int,
    val itemId: String,
    val playbackUrl: String,
    val playbackPositionMs: Long,
    val title: String,
    val mediaSourceId: String,
    val playSessionId: String,
    val coverImageUrl: String?,
    val isFavorite: Boolean
) {
    fun isPreparedForReuse(): Boolean {
        return player.playbackState != Player.STATE_IDLE
    }
}

/**
 * Prepares the next video on a pooled ExoPlayer without starting playback.
 */
class VideoPreloadManager(
    private val mediaRepository: MediaRepository,
    private val connection: ServerConnection,
    private val playerPool: VideoPlayerPool,
    private val networkExecutor: ExecutorService,
    private val mainHandler: Handler,
    private val playbackParametersProvider: () -> PlaybackParameters,
    private val isStillActive: () -> Boolean,
    private val isExpectedNext: (targetIndex: Int, itemId: String) -> Boolean
) {
    var nextPreload: VideoPreloadedPlayback? = null
        private set

    fun prepareNext(
        currentIndex: Int,
        itemIds: List<String>,
        itemTitles: List<String>
    ) {
        if (itemIds.isEmpty() || currentIndex !in itemIds.indices) return
        releaseDistant(currentIndex)

        val targetIndex = currentIndex + 1
        if (targetIndex !in itemIds.indices) {
            recyclePreload()
            return
        }

        val targetItemId = itemIds[targetIndex]
        if (nextPreload?.playlistIndex == targetIndex && nextPreload?.itemId == targetItemId) return

        recyclePreload()
        val pooledPlayer = playerPool.takeSparePlayer()
        playerPool.ensureSparePlayer()

        networkExecutor.execute {
            val detail = mediaRepository.fetchVideoDetail(connection, targetItemId).getOrNull()
            val url = detail?.playbackUrl.orEmpty()
            if (url.isBlank()) {
                mainHandler.post { playerPool.recycleToSpare(pooledPlayer) }
                return@execute
            }

            val preload = VideoPreloadedPlayback(
                player = pooledPlayer,
                playlistIndex = targetIndex,
                itemId = targetItemId,
                playbackUrl = url,
                playbackPositionMs = detail?.playbackPositionTicks?.div(10_000L) ?: 0L,
                title = detail?.title.orEmpty().ifBlank {
                    itemTitles.getOrNull(targetIndex).orEmpty()
                },
                mediaSourceId = detail?.mediaSourceId.orEmpty(),
                playSessionId = detail?.playSessionId.orEmpty(),
                coverImageUrl = detail?.heroImageUrl,
                isFavorite = detail?.isFavorite ?: false
            )

            mainHandler.post {
                if (!isStillActive() || !isExpectedNext(targetIndex, targetItemId)) {
                    playerPool.recycleToSpare(pooledPlayer)
                    return@post
                }

                pooledPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(url)))
                pooledPlayer.prepare()
                pooledPlayer.seekTo(preload.playbackPositionMs.coerceAtLeast(0L))
                pooledPlayer.playbackParameters = playbackParametersProvider()
                pooledPlayer.pause()
                pooledPlayer.playWhenReady = false
                nextPreload = preload

                VideoPlaybackAddressCache.save(
                    VideoPlaybackAddressCache.Entry(
                        itemId = preload.itemId,
                        playbackUrl = preload.playbackUrl,
                        playbackPositionMs = preload.playbackPositionMs,
                        title = preload.title,
                        mediaSourceId = preload.mediaSourceId,
                        playSessionId = preload.playSessionId
                    )
                )
            }
        }
    }

    fun takeIfMatches(targetIndex: Int, itemId: String): VideoPreloadedPlayback? {
        val preload = nextPreload ?: return null
        if (preload.playlistIndex != targetIndex || preload.itemId != itemId || !preload.isPreparedForReuse()) {
            return null
        }
        nextPreload = null
        return preload
    }

    fun peekIfMatches(targetIndex: Int, itemId: String): VideoPreloadedPlayback? {
        val preload = nextPreload ?: return null
        if (preload.playlistIndex != targetIndex || preload.itemId != itemId || !preload.isPreparedForReuse()) {
            return null
        }
        return preload
    }

    fun recyclePreload() {
        val preloadPlayer = nextPreload?.player
        nextPreload = null
        playerPool.recycleToSpare(preloadPlayer)
    }

    fun releaseDistant(currentIndex: Int) {
        val preload = nextPreload ?: return
        if (abs(preload.playlistIndex - currentIndex) <= MAX_POOLED_INDEX_DISTANCE) return
        nextPreload = null
        preload.player.safeRelease()
    }

    fun clear() {
        val preloadPlayer = nextPreload?.player
        nextPreload = null
        preloadPlayer?.safeRelease()
    }

    companion object {
        private const val MAX_POOLED_INDEX_DISTANCE = 2
    }
}
