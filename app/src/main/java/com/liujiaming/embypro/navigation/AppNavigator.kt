package com.liujiaming.embypro

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity

/**
 * Data class representing a video playback queue.
 * Contains item IDs, titles, and the current playing index.
 */
data class VideoQueue(
    val itemIds: ArrayList<String>,
    val itemTitles: ArrayList<String>,
    val currentIndex: Int
)

/**
 * Navigator object for handling app-wide navigation between activities.
 * Provides helper methods for opening different screens with proper intent extras.
 */
object AppNavigator {
    /**
     * Opens the home screen activity.
     */
    fun openHome(activity: AppCompatActivity) {
        activity.startActivity(Intent(activity, HomeTabsActivity::class.java))
    }

    /**
     * Opens the search activity with server connection.
     */
    fun openSearch(activity: AppCompatActivity, connection: ServerConnection) {
        activity.startActivity(
            Intent(activity, SearchActivity::class.java)
                .putServerConnection(connection)
        )
    }

    /**
     * Opens the playback history activity with server connection.
     */
    fun openPlaybackHistory(activity: AppCompatActivity, connection: ServerConnection) {
        activity.startActivity(
            Intent(activity, PlaybackHistoryActivity::class.java)
                .putServerConnection(connection)
        )
    }

    /**
     * Opens the favorite items activity with server connection.
     */
    fun openFavoriteItems(activity: AppCompatActivity, connection: ServerConnection) {
        activity.startActivity(
            Intent(activity, FavoriteItemsActivity::class.java)
                .putServerConnection(connection)
        )
    }

    /**
     * Opens the settings activity with server connection.
     */
    fun openSettings(activity: AppCompatActivity, connection: ServerConnection) {
        activity.startActivity(
            Intent(activity, SettingsActivity::class.java)
                .putServerConnection(connection)
        )
    }

    /**
     * Opens the home settings activity with server connection.
     */
    fun openHomeSettings(activity: AppCompatActivity, connection: ServerConnection) {
        activity.startActivity(
            Intent(activity, HomeSettingsActivity::class.java)
                .putServerConnection(connection)
        )
    }

    /**
     * Opens the music settings activity with server connection.
     */
    fun openMusicSettings(activity: AppCompatActivity, connection: ServerConnection) {
        activity.startActivity(
            Intent(activity, MusicSettingsActivity::class.java)
                .putServerConnection(connection)
        )
    }

    /**
     * Opens the music library activity with server connection.
     */
    fun openMusicLibrary(activity: AppCompatActivity, connection: ServerConnection) {
        activity.startActivity(
            Intent(activity, MusicLibraryActivity::class.java)
                .putServerConnection(connection)
        )
    }

    /**
     * Opens the music list activity with browse type and optional container info.
     */
    fun openMusicList(
        activity: AppCompatActivity,
        connection: ServerConnection,
        browseType: MusicBrowseType,
        libraryId: String? = null,
        containerId: String? = null,
        containerTitle: String? = null
    ) {
        activity.startActivity(
            Intent(activity, MusicListActivity::class.java)
                .putExtra(MusicListActivity.EXTRA_BROWSE_TYPE, browseType.name)
                .putExtra(MusicListActivity.EXTRA_LIBRARY_ID, libraryId)
                .putExtra(MusicListActivity.EXTRA_CONTAINER_ID, containerId)
                .putExtra(MusicListActivity.EXTRA_CONTAINER_TITLE, containerTitle)
                .putServerConnection(connection)
        )
    }

    /**
     * Opens the music player activity with queue information.
     */
    fun openMusicPlayer(
        activity: AppCompatActivity,
        connection: ServerConnection,
        libraryId: String?,
        queueTitle: String,
        queueIds: ArrayList<String>,
        queueTitles: ArrayList<String>,
        queueSubtitles: ArrayList<String>,
        queueImages: ArrayList<String>,
        queueIndex: Int,
        shuffleModeEnabled: Boolean = false
    ) {
        activity.startActivity(
            Intent(activity, MusicPlayerActivity::class.java)
                .putServerConnection(connection)
                .putExtra(MusicPlayerActivity.EXTRA_LIBRARY_ID, libraryId)
                .putExtra(MusicPlayerActivity.EXTRA_QUEUE_TITLE, queueTitle)
                .putStringArrayListExtra(MusicPlayerActivity.EXTRA_QUEUE_IDS, queueIds)
                .putStringArrayListExtra(MusicPlayerActivity.EXTRA_QUEUE_TITLES, queueTitles)
                .putStringArrayListExtra(MusicPlayerActivity.EXTRA_QUEUE_SUBTITLES, queueSubtitles)
                .putStringArrayListExtra(MusicPlayerActivity.EXTRA_QUEUE_IMAGES, queueImages)
                .putExtra(MusicPlayerActivity.EXTRA_QUEUE_INDEX, queueIndex)
                .putExtra(MusicPlayerActivity.EXTRA_SHUFFLE_MODE, shuffleModeEnabled)
        )
    }

    /**
     * Opens the library items activity for a specific library.
     */
    fun openLibrary(
        activity: AppCompatActivity,
        connection: ServerConnection,
        libraryId: String,
        libraryName: String
    ) {
        activity.startActivity(
            Intent(activity, LibraryItemsActivity::class.java)
                .putExtra(LibraryItemsActivity.EXTRA_LIBRARY_ID, libraryId)
                .putExtra(LibraryItemsActivity.EXTRA_LIBRARY_NAME, libraryName)
                .putServerConnection(connection)
        )
    }

    /**
     * Opens either a library or video detail based on item type.
     */
    fun openPosterItem(
        activity: AppCompatActivity,
        connection: ServerConnection,
        item: MediaPosterUiModel,
        sourceItems: List<MediaPosterUiModel>
    ) {
        if (item.id.isBlank()) return
        if (item.isFolder || item.itemType == "BoxSet" || item.itemType == "Folder") {
            openLibrary(activity, connection, item.id, item.title)
            return
        }
        openVideoDetail(activity, connection, item.id, buildPosterVideoQueue(sourceItems, item.id))
    }

    /**
     * Opens the video detail activity with queue information.
     */
    fun openVideoDetail(
        activity: AppCompatActivity,
        connection: ServerConnection,
        itemId: String,
        queue: VideoQueue
    ) {
        activity.startActivity(
            Intent(activity, VideoDetailActivity::class.java)
                .putExtra(VideoDetailActivity.EXTRA_ITEM_ID, itemId)
                .putServerConnection(connection)
                .putStringArrayListExtra(VideoDetailActivity.EXTRA_PLAYLIST_ITEM_IDS, queue.itemIds)
                .putStringArrayListExtra(VideoDetailActivity.EXTRA_PLAYLIST_ITEM_TITLES, queue.itemTitles)
                .putExtra(VideoDetailActivity.EXTRA_PLAYLIST_INDEX, queue.currentIndex)
        )
    }

    /**
     * Creates an intent for launching the video player with playback details.
     */
    fun videoPlayerIntent(
        context: Context,
        connection: ServerConnection,
        detail: VideoDetailUiModel,
        queue: VideoQueue,
        itemId: String = detail.itemId,
        preferredStartPositionMs: Long = detail.playbackPositionTicks / 10_000L
    ): Intent {
        return Intent(context, PlayerActivity::class.java)
            .putExtra(PlayerActivity.EXTRA_PLAYBACK_URL, detail.playbackUrl)
            .putExtra(PlayerActivity.EXTRA_ACCESS_TOKEN, connection.accessToken)
            .putExtra(PlayerActivity.EXTRA_TITLE, detail.title)
            .putExtra(PlayerActivity.EXTRA_COVER_IMAGE_URL, detail.heroImageUrl)
            .putServerConnection(connection)
            .putExtra(PlayerActivity.EXTRA_ITEM_ID, itemId)
            .putExtra(PlayerActivity.EXTRA_MEDIA_SOURCE_ID, detail.mediaSourceId)
            .putExtra(PlayerActivity.EXTRA_PLAY_SESSION_ID, detail.playSessionId)
            .putExtra(PlayerActivity.EXTRA_START_POSITION_MS, preferredStartPositionMs)
            .putStringArrayListExtra(PlayerActivity.EXTRA_PLAYLIST_ITEM_IDS, queue.itemIds)
            .putStringArrayListExtra(PlayerActivity.EXTRA_PLAYLIST_ITEM_TITLES, queue.itemTitles)
            .putExtra(PlayerActivity.EXTRA_PLAYLIST_INDEX, queue.currentIndex)
    }

    /**
     * Builds a video queue from a list of media poster items.
     * Filters out folders and non-playable items.
     */
    fun buildPosterVideoQueue(items: List<MediaPosterUiModel>, selectedId: String): VideoQueue {
        val playableItems = items.filter { !it.isFolder && it.itemType != "BoxSet" && it.itemType != "Folder" }
        return VideoQueue(
            itemIds = ArrayList(playableItems.map { it.id }),
            itemTitles = ArrayList(playableItems.map { it.title }),
            currentIndex = playableItems.indexOfFirst { it.id == selectedId }
        )
    }

    /**
     * Builds a video queue from playback history items.
     */
    fun buildHistoryVideoQueue(items: List<PlaybackHistoryItemUiModel>, selectedId: String): VideoQueue {
        return VideoQueue(
            itemIds = ArrayList(items.map { it.itemId }),
            itemTitles = ArrayList(items.map { it.title }),
            currentIndex = items.indexOfFirst { it.itemId == selectedId }
        )
    }
}
