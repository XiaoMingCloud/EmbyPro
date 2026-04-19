package com.liujiaming.embypro

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity

data class VideoQueue(
    val itemIds: ArrayList<String>,
    val itemTitles: ArrayList<String>,
    val currentIndex: Int
)

object AppNavigator {
    fun openHome(activity: AppCompatActivity) {
        activity.startActivity(Intent(activity, HomeTabsActivity::class.java))
    }

    fun openSearch(activity: AppCompatActivity, connection: ServerConnection) {
        activity.startActivity(
            Intent(activity, SearchActivity::class.java)
                .putServerConnection(connection)
        )
    }

    fun openPlaybackHistory(activity: AppCompatActivity, connection: ServerConnection) {
        activity.startActivity(
            Intent(activity, PlaybackHistoryActivity::class.java)
                .putServerConnection(connection)
        )
    }

    fun openFavoriteItems(activity: AppCompatActivity, connection: ServerConnection) {
        activity.startActivity(
            Intent(activity, FavoriteItemsActivity::class.java)
                .putServerConnection(connection)
        )
    }

    fun openSettings(activity: AppCompatActivity, connection: ServerConnection) {
        activity.startActivity(
            Intent(activity, SettingsActivity::class.java)
                .putServerConnection(connection)
        )
    }

    fun openHomeSettings(activity: AppCompatActivity, connection: ServerConnection) {
        activity.startActivity(
            Intent(activity, HomeSettingsActivity::class.java)
                .putServerConnection(connection)
        )
    }

    fun openMusicSettings(activity: AppCompatActivity, connection: ServerConnection) {
        activity.startActivity(
            Intent(activity, MusicSettingsActivity::class.java)
                .putServerConnection(connection)
        )
    }

    fun openMusicLibrary(activity: AppCompatActivity, connection: ServerConnection) {
        activity.startActivity(
            Intent(activity, MusicLibraryActivity::class.java)
                .putServerConnection(connection)
        )
    }

    fun openMusicList(
        activity: AppCompatActivity,
        connection: ServerConnection,
        browseType: MusicBrowseType,
        containerId: String? = null,
        containerTitle: String? = null
    ) {
        activity.startActivity(
            Intent(activity, MusicListActivity::class.java)
                .putExtra(MusicListActivity.EXTRA_BROWSE_TYPE, browseType.name)
                .putExtra(MusicListActivity.EXTRA_CONTAINER_ID, containerId)
                .putExtra(MusicListActivity.EXTRA_CONTAINER_TITLE, containerTitle)
                .putServerConnection(connection)
        )
    }

    fun openMusicPlayer(
        activity: AppCompatActivity,
        connection: ServerConnection,
        libraryId: String?,
        queueTitle: String,
        queueIds: ArrayList<String>,
        queueTitles: ArrayList<String>,
        queueSubtitles: ArrayList<String>,
        queueImages: ArrayList<String>,
        queueIndex: Int
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
        )
    }

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
            .putExtra(PlayerActivity.EXTRA_START_POSITION_MS, preferredStartPositionMs)
            .putStringArrayListExtra(PlayerActivity.EXTRA_PLAYLIST_ITEM_IDS, queue.itemIds)
            .putStringArrayListExtra(PlayerActivity.EXTRA_PLAYLIST_ITEM_TITLES, queue.itemTitles)
            .putExtra(PlayerActivity.EXTRA_PLAYLIST_INDEX, queue.currentIndex)
    }

    fun buildPosterVideoQueue(items: List<MediaPosterUiModel>, selectedId: String): VideoQueue {
        val playableItems = items.filter { !it.isFolder && it.itemType != "BoxSet" && it.itemType != "Folder" }
        return VideoQueue(
            itemIds = ArrayList(playableItems.map { it.id }),
            itemTitles = ArrayList(playableItems.map { it.title }),
            currentIndex = playableItems.indexOfFirst { it.id == selectedId }
        )
    }

    fun buildHistoryVideoQueue(items: List<PlaybackHistoryItemUiModel>, selectedId: String): VideoQueue {
        return VideoQueue(
            itemIds = ArrayList(items.map { it.itemId }),
            itemTitles = ArrayList(items.map { it.title }),
            currentIndex = items.indexOfFirst { it.itemId == selectedId }
        )
    }
}
