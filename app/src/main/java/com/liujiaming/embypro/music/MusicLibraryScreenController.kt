package com.liujiaming.embypro

import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updatePadding

/**
 * Controller for managing the music library screen UI.
 * Handles state rendering, library selection, and navigation to music lists.
 * Can be used as a standalone activity or embedded with bottom navigation.
 */
class MusicLibraryScreenController(
    private val activity: AppCompatActivity,
    private val root: View,
    private val connection: ServerConnection,
    embeddedWithBottomNav: Boolean = false
) {
    private val scrollView: View = root.findViewById(R.id.musicLibraryScrollView)
    private val loadingContainer: View = root.findViewById(R.id.musicLibraryLoadingContainer)
    private val emptyContainer: View = root.findViewById(R.id.musicLibraryEmptyContainer)
    private val errorContainer: View = root.findViewById(R.id.musicLibraryErrorContainer)
    private val errorTextView: TextView = root.findViewById(R.id.musicLibraryErrorText)
    private val titleTextView: TextView = root.findViewById(R.id.musicLibraryTitleText)
    private val partitionTextView: TextView = root.findViewById(R.id.musicLibraryPartitionText)
    private val countTextView: TextView = root.findViewById(R.id.musicLibraryCountText)
    private val statusTextView: TextView = root.findViewById(R.id.musicLibraryStatusText)
    private val songsStatsText: TextView = root.findViewById(R.id.musicLibrarySongsStatsText)
    private val albumsStatsText: TextView = root.findViewById(R.id.musicLibraryAlbumsStatsText)
    private val artistsStatsText: TextView = root.findViewById(R.id.musicLibraryArtistsStatsText)
    private val partitionRow: View = root.findViewById(R.id.musicLibraryPartitionRow)

    private val stateListener = MusicLibraryStateListener { state ->
        renderState(state)
    }

    init {
        partitionRow.setDebouncedClickListener { showLibraryPicker() }
        root.findViewById<View>(R.id.musicLibrarySongsEntry).setDebouncedClickListener { openList(MusicBrowseType.SONGS) }
        root.findViewById<View>(R.id.musicLibraryFavoritesEntry).setDebouncedClickListener { openList(MusicBrowseType.FAVORITES) }
        root.findViewById<View>(R.id.musicLibraryAlbumsEntry).setDebouncedClickListener { openList(MusicBrowseType.ALBUMS) }
        root.findViewById<View>(R.id.musicLibraryArtistsEntry).setDebouncedClickListener { openList(MusicBrowseType.ARTISTS) }
        root.findViewById<View>(R.id.musicLibraryLocalEntry).setDebouncedClickListener { openList(MusicBrowseType.LOCAL) }
        root.findViewById<View>(R.id.musicLibraryFoldersEntry).setDebouncedClickListener { openList(MusicBrowseType.FOLDERS) }
        root.findViewById<View>(R.id.musicLibraryRetryButton).setDebouncedClickListener {
            MusicLibraryRepository.connect(
                activity,
                connection.baseUrl,
                connection.userId,
                connection.accessToken,
                forceRefresh = true
            )
        }

        if (embeddedWithBottomNav) {
            scrollView.updatePadding(
                bottom = scrollView.paddingBottom + activity.resources.getDimensionPixelSize(R.dimen.home_bottom_nav_clearance)
            )
        }
    }

    fun onStart() {
        MusicLibraryRepository.subscribe(stateListener)
        MusicLibraryRepository.connect(activity, connection.baseUrl, connection.userId, connection.accessToken)
    }

    fun onStop() {
        MusicLibraryRepository.unsubscribe(stateListener)
    }

    private fun renderState(state: MusicLibraryState) {
        if (state.errorMessage != null && state.musicLibraries.isEmpty()) {
            showError(state.errorMessage)
            return
        }

        if (state.isLoadingLibraries && state.musicLibraries.isEmpty()) {
            showLoading()
            return
        }

        if (state.isEmpty) {
            showEmpty()
            return
        }

        showContent()

        val currentLibrary = state.currentMusicLibrary
        val displayName = MusicLibraryRepository.displayName(currentLibrary)
            .ifBlank { activity.getString(R.string.music_library_name) }
        titleTextView.text = displayName
        partitionTextView.text = currentLibrary?.title?.ifBlank {
            activity.getString(R.string.music_library_partition_fallback)
        } ?: activity.getString(R.string.music_library_partition_fallback)

        if (state.isLoadingStats) {
            countTextView.text = activity.getString(R.string.loading)
            songsStatsText.text = activity.getString(R.string.loading)
            albumsStatsText.text = activity.getString(R.string.loading)
            artistsStatsText.text = activity.getString(R.string.loading)
            statusTextView.visibility = View.GONE
            return
        }

        val stats = state.libraryStats
        if (stats != null) {
            countTextView.text = stats.songsCount.toString()
            songsStatsText.text = activity.getString(R.string.music_library_count_with_unit, stats.songsCount)
            albumsStatsText.text = activity.getString(R.string.music_library_count_albums, stats.albumsCount)
            artistsStatsText.text = activity.getString(R.string.music_library_count_artists, stats.artistsCount)
            statusTextView.visibility = View.GONE
        } else {
            countTextView.text = "--"
            songsStatsText.text = "--"
            albumsStatsText.text = "--"
            artistsStatsText.text = "--"
            statusTextView.visibility = if (state.statsErrorMessage.isNullOrBlank()) View.GONE else View.VISIBLE
            statusTextView.text = state.statsErrorMessage
        }
    }

    private fun openList(browseType: MusicBrowseType) {
        AppNavigator.openMusicList(activity, connection, browseType)
    }

    private fun showLibraryPicker() {
        val state = MusicLibraryRepository.currentState()
        if (state.musicLibraries.isEmpty()) return

        val labels = state.musicLibraries.map { MusicLibraryRepository.displayName(it) }
        val selectedIndex = state.musicLibraries.indexOfFirst { it.id == state.currentLibraryId }.coerceAtLeast(0)

        activity.showMusicPartitionPickerDialog(labels, selectedIndex) { which ->
            MusicLibraryRepository.selectLibrary(state.musicLibraries[which].id)
        }
    }

    private fun showLoading() {
        scrollView.visibility = View.GONE
        loadingContainer.visibility = View.VISIBLE
        emptyContainer.visibility = View.GONE
        errorContainer.visibility = View.GONE
    }

    private fun showEmpty() {
        scrollView.visibility = View.GONE
        loadingContainer.visibility = View.GONE
        emptyContainer.visibility = View.VISIBLE
        errorContainer.visibility = View.GONE
    }

    private fun showError(message: String) {
        scrollView.visibility = View.GONE
        loadingContainer.visibility = View.GONE
        emptyContainer.visibility = View.GONE
        errorContainer.visibility = View.VISIBLE
        errorTextView.text = message
    }

    private fun showContent() {
        scrollView.visibility = View.VISIBLE
        loadingContainer.visibility = View.GONE
        emptyContainer.visibility = View.GONE
        errorContainer.visibility = View.GONE
    }
}
