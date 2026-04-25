package com.liujiaming.embypro

import android.content.Context
import android.content.Intent

/**
 * Singleton store for managing music player session state.
 * Records playback queue information and creates intents to launch the music player.
 */
object MusicPlayerSessionStore {
    private var launchState: MusicPlayerLaunchState? = null

    /**
     * Records the current music player launch state with queue information.
     *
     * @param connection Server connection credentials
     * @param libraryId Music library ID
     * @param queueTitle Display title for the queue
     * @param queueIds List of item IDs in the queue
     * @param queueTitles List of item titles in the queue
     * @param queueSubtitles List of item subtitles in the queue
     * @param queueImages List of item image URLs in the queue
     * @param queueIndex Current playing item index in the queue
     */
    fun record(
        connection: ServerConnection,
        libraryId: String?,
        queueTitle: String,
        queueIds: ArrayList<String>,
        queueTitles: ArrayList<String>,
        queueSubtitles: ArrayList<String>,
        queueImages: ArrayList<String>,
        queueIndex: Int,
        playbackMode: MusicPlaybackMode = MusicPlaybackMode.ORDER,
        playlistIds: ArrayList<String> = ArrayList(queueIds),
        playlistTitles: ArrayList<String> = ArrayList(queueTitles),
        playlistSubtitles: ArrayList<String> = ArrayList(queueSubtitles),
        playlistImages: ArrayList<String> = ArrayList(queueImages)
    ) {
        launchState = MusicPlayerLaunchState(
            connection = connection,
            libraryId = libraryId,
            queueTitle = queueTitle,
            queueIds = ArrayList(queueIds),
            queueTitles = ArrayList(queueTitles),
            queueSubtitles = ArrayList(queueSubtitles),
            queueImages = ArrayList(queueImages),
            queueIndex = queueIndex.coerceIn(0, (queueIds.lastIndex).coerceAtLeast(0)),
            playbackMode = playbackMode,
            playlistIds = ArrayList(playlistIds),
            playlistTitles = ArrayList(playlistTitles),
            playlistSubtitles = ArrayList(playlistSubtitles),
            playlistImages = ArrayList(playlistImages)
        )
    }

    /**
     * Updates the current playing item index in the queue.
     */
    fun updateCurrentItem(itemId: String?) {
        if (itemId.isNullOrBlank()) return
        val state = launchState ?: return
        val index = state.queueIds.indexOf(itemId)
        if (index >= 0) {
            launchState = state.copy(queueIndex = index)
        }
    }

    /**
     * Returns the current server connection from the launch state.
     */
    fun currentConnection(): ServerConnection? = launchState?.connection

    /**
     * Creates an intent to launch the music player with the recorded session state.
     * Returns null if no valid session exists.
     */
    fun createPlayerIntent(context: Context): Intent? {
        val state = launchState ?: return null
        if (state.queueIds.isEmpty()) return null
        return Intent(context, MusicPlayerActivity::class.java)
            .putServerConnection(state.connection)
            .putExtra(MusicPlayerActivity.EXTRA_LIBRARY_ID, state.libraryId)
            .putExtra(MusicPlayerActivity.EXTRA_QUEUE_TITLE, state.queueTitle)
            .putStringArrayListExtra(MusicPlayerActivity.EXTRA_QUEUE_IDS, ArrayList(state.queueIds))
            .putStringArrayListExtra(MusicPlayerActivity.EXTRA_QUEUE_TITLES, ArrayList(state.queueTitles))
            .putStringArrayListExtra(MusicPlayerActivity.EXTRA_QUEUE_SUBTITLES, ArrayList(state.queueSubtitles))
            .putStringArrayListExtra(MusicPlayerActivity.EXTRA_QUEUE_IMAGES, ArrayList(state.queueImages))
            .putExtra(MusicPlayerActivity.EXTRA_QUEUE_INDEX, state.queueIndex)
            .putExtra(MusicPlayerActivity.EXTRA_PLAYBACK_MODE, state.playbackMode.name)
            .putStringArrayListExtra(MusicPlayerActivity.EXTRA_PLAYLIST_IDS, ArrayList(state.playlistIds))
            .putStringArrayListExtra(MusicPlayerActivity.EXTRA_PLAYLIST_TITLES, ArrayList(state.playlistTitles))
            .putStringArrayListExtra(MusicPlayerActivity.EXTRA_PLAYLIST_SUBTITLES, ArrayList(state.playlistSubtitles))
            .putStringArrayListExtra(MusicPlayerActivity.EXTRA_PLAYLIST_IMAGES, ArrayList(state.playlistImages))
    }

    /**
     * Data class holding the complete music player launch state.
     */
    private data class MusicPlayerLaunchState(
        val connection: ServerConnection,
        val libraryId: String?,
        val queueTitle: String,
        val queueIds: ArrayList<String>,
        val queueTitles: ArrayList<String>,
        val queueSubtitles: ArrayList<String>,
        val queueImages: ArrayList<String>,
        val queueIndex: Int,
        val playbackMode: MusicPlaybackMode,
        val playlistIds: ArrayList<String>,
        val playlistTitles: ArrayList<String>,
        val playlistSubtitles: ArrayList<String>,
        val playlistImages: ArrayList<String>
    )
}
