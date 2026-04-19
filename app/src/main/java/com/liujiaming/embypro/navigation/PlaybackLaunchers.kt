package com.liujiaming.embypro

import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

fun AppCompatActivity.playVideoDirectly(
    connection: ServerConnection,
    mediaRepository: MediaRepository,
    itemId: String,
    queue: VideoQueue,
    preferredStartPositionMs: Long? = null
) {
    if (itemId.isBlank()) return
    AppExecutors.io.execute {
        val result = mediaRepository.fetchVideoDetail(connection, itemId)
        runOnUiThread {
            result.onSuccess { detail ->
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
            }.onFailure { error ->
                Toast.makeText(
                    this,
                    error.message ?: getString(R.string.player_error),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
