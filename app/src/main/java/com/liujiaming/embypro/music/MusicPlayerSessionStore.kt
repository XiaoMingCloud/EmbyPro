package com.liujiaming.embypro

import android.content.Context
import android.content.Intent

object MusicPlayerSessionStore {
    private var launchState: MusicPlayerLaunchState? = null

    fun record(
        connection: ServerConnection,
        libraryId: String?,
        queueTitle: String,
        queueIds: ArrayList<String>,
        queueTitles: ArrayList<String>,
        queueSubtitles: ArrayList<String>,
        queueImages: ArrayList<String>,
        queueIndex: Int
    ) {
        launchState = MusicPlayerLaunchState(
            connection = connection,
            libraryId = libraryId,
            queueTitle = queueTitle,
            queueIds = ArrayList(queueIds),
            queueTitles = ArrayList(queueTitles),
            queueSubtitles = ArrayList(queueSubtitles),
            queueImages = ArrayList(queueImages),
            queueIndex = queueIndex.coerceIn(0, (queueIds.lastIndex).coerceAtLeast(0))
        )
    }

    fun updateCurrentItem(itemId: String?) {
        if (itemId.isNullOrBlank()) return
        val state = launchState ?: return
        val index = state.queueIds.indexOf(itemId)
        if (index >= 0) {
            launchState = state.copy(queueIndex = index)
        }
    }

    fun currentConnection(): ServerConnection? = launchState?.connection

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
    }

    private data class MusicPlayerLaunchState(
        val connection: ServerConnection,
        val libraryId: String?,
        val queueTitle: String,
        val queueIds: ArrayList<String>,
        val queueTitles: ArrayList<String>,
        val queueSubtitles: ArrayList<String>,
        val queueImages: ArrayList<String>,
        val queueIndex: Int
    )
}
