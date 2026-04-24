package com.liujiaming.embypro

import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Extension function to launch video player directly.
 * Fetches video details in background, then starts PlayerActivity.
 *
 * @param connection Server connection credentials
 * @param mediaRepository Media repository for fetching video details
 * @param itemId Video item ID to play
 * @param queue Video queue information
 * @param preferredStartPositionMs Preferred start position in milliseconds (defaults to saved position)
 */
fun AppCompatActivity.playVideoDirectly(
    connection: ServerConnection,
    mediaRepository: MediaRepository,
    itemId: String,
    queue: VideoQueue,
    preferredStartPositionMs: Long? = null
) {
    if (itemId.isBlank()) return
    if (!VideoPlayerLaunchGuard.tryAcquire()) return
    AppExecutors.io.execute {
        val result = mediaRepository.fetchVideoDetail(connection, itemId)
        runOnUiThread {
            result.onSuccess { detail ->
                runCatching {
                    startActivity(
                        AppNavigator.videoPlayerIntent(
                            context = this,
                            connection = connection,
                            detail = detail,
                            queue = queue,
                            itemId = itemId,
                            preferredStartPositionMs = preferredStartPositionMs
                                ?: detail.playbackPositionTicks / 10_000L
                        )
                    )
                }.onFailure {
                    VideoPlayerLaunchGuard.release()
                }
            }.onFailure { error ->
                VideoPlayerLaunchGuard.release()
                Toast.makeText(
                    this,
                    userFriendlyErrorMessage(error, R.string.player_error),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
