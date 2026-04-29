package com.liujiaming.embypro

import androidx.media3.exoplayer.ExoPlayer

/**
 * Owns the small ExoPlayer pool used by the vertical video player:
 * one current player, one prepared/preparing player, and one spare player.
 */
class VideoPlayerPool(
    private val createPlayer: () -> ExoPlayer,
    private val configurePlayer: (ExoPlayer) -> Unit
) {
    var currentPlayer: ExoPlayer? = null
        private set

    private var sparePlayer: ExoPlayer? = null

    fun createCurrentPlayer(): ExoPlayer {
        return newPlayer().also { player ->
            currentPlayer = player
            ensureSparePlayer()
        }
    }

    fun ensureSparePlayer(): ExoPlayer {
        return sparePlayer ?: newPlayer().also { player ->
            sparePlayer = player
        }
    }

    fun takeSparePlayer(): ExoPlayer {
        return ensureSparePlayer().also {
            sparePlayer = null
        }
    }

    fun promoteToCurrent(nextPlayer: ExoPlayer): ExoPlayer? {
        val oldPlayer = currentPlayer
        currentPlayer = nextPlayer
        return oldPlayer
    }

    fun clearCurrent() {
        currentPlayer = null
    }

    fun recycleToSpare(recycledPlayer: ExoPlayer?, protectedPlayers: Set<ExoPlayer?> = emptySet()) {
        val player = recycledPlayer ?: return
        if (player === currentPlayer || protectedPlayers.any { it === player }) return

        player.playWhenReady = false
        player.pause()
        player.stop()
        player.clearMediaItems()

        if (sparePlayer == null) {
            sparePlayer = player
        } else if (sparePlayer !== player) {
            player.safeRelease()
        }
    }

    fun releaseSpare() {
        sparePlayer?.safeRelease()
        sparePlayer = null
    }

    fun releaseAll(extraPlayers: Collection<ExoPlayer?> = emptyList()) {
        (listOf(currentPlayer, sparePlayer) + extraPlayers)
            .distinct()
            .forEach { it?.safeRelease() }
        currentPlayer = null
        sparePlayer = null
    }

    private fun newPlayer(): ExoPlayer {
        return createPlayer().also(configurePlayer)
    }
}

fun ExoPlayer.safeRelease() {
    runCatching {
        playWhenReady = false
        stop()
        clearMediaItems()
        release()
    }
}
