package com.liujiaming.embypro

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MusicLibraryActivity : AppCompatActivity() {
    private val sessionStore by lazy { ServerSessionStore(this) }

    private lateinit var scrollView: View
    private lateinit var loadingContainer: View
    private lateinit var emptyContainer: View
    private lateinit var errorContainer: View
    private lateinit var errorTextView: TextView
    private lateinit var titleTextView: TextView
    private lateinit var partitionTextView: TextView
    private lateinit var countTextView: TextView
    private lateinit var statusTextView: TextView
    private lateinit var songsStatsText: TextView
    private lateinit var playlistsStatsText: TextView
    private lateinit var albumsStatsText: TextView
    private lateinit var artistsStatsText: TextView
    private lateinit var partitionRow: View

    private lateinit var baseUrl: String
    private lateinit var userId: String
    private lateinit var accessToken: String

    private val stateListener = MusicLibraryStateListener { state ->
        renderState(state)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdgeHelper.enable(this, lightSystemBars = false)
        setContentView(R.layout.activity_music_library)
        supportActionBar?.hide()

        resolveSessionParams()

        scrollView = findViewById(R.id.musicLibraryScrollView)
        loadingContainer = findViewById(R.id.musicLibraryLoadingContainer)
        emptyContainer = findViewById(R.id.musicLibraryEmptyContainer)
        errorContainer = findViewById(R.id.musicLibraryErrorContainer)
        errorTextView = findViewById(R.id.musicLibraryErrorText)
        titleTextView = findViewById(R.id.musicLibraryTitleText)
        partitionTextView = findViewById(R.id.musicLibraryPartitionText)
        countTextView = findViewById(R.id.musicLibraryCountText)
        statusTextView = findViewById(R.id.musicLibraryStatusText)
        songsStatsText = findViewById(R.id.musicLibrarySongsStatsText)
        playlistsStatsText = findViewById(R.id.musicLibraryPlaylistsStatsText)
        albumsStatsText = findViewById(R.id.musicLibraryAlbumsStatsText)
        artistsStatsText = findViewById(R.id.musicLibraryArtistsStatsText)
        partitionRow = findViewById(R.id.musicLibraryPartitionRow)

        partitionRow.setDebouncedClickListener { showLibraryPicker() }
        findViewById<View>(R.id.musicLibrarySongsEntry).setDebouncedClickListener { openList(MusicBrowseType.SONGS) }
        findViewById<View>(R.id.musicLibraryFavoritesEntry).setDebouncedClickListener { openList(MusicBrowseType.FAVORITES) }
        findViewById<View>(R.id.musicLibraryPlaylistsEntry).setDebouncedClickListener { openList(MusicBrowseType.PLAYLISTS) }
        findViewById<View>(R.id.musicLibraryAlbumsEntry).setDebouncedClickListener { openList(MusicBrowseType.ALBUMS) }
        findViewById<View>(R.id.musicLibraryArtistsEntry).setDebouncedClickListener { openList(MusicBrowseType.ARTISTS) }
        findViewById<View>(R.id.musicLibraryFoldersEntry).setDebouncedClickListener { openList(MusicBrowseType.FOLDERS) }
        findViewById<View>(R.id.musicLibraryRetryButton).setDebouncedClickListener {
            MusicLibraryRepository.connect(this, baseUrl, userId, accessToken, forceRefresh = true)
        }

        EdgeToEdgeHelper.applyInsets(scrollView, applyTop = true, applyBottom = true)
        EdgeToEdgeHelper.applyInsets(findViewById(R.id.musicLibraryErrorContainer), applyTop = true, applyBottom = true)
    }

    override fun onStart() {
        super.onStart()
        MusicLibraryRepository.subscribe(stateListener)
        MusicLibraryRepository.connect(this, baseUrl, userId, accessToken)
    }

    override fun onStop() {
        MusicLibraryRepository.unsubscribe(stateListener)
        super.onStop()
    }

    private fun resolveSessionParams() {
        baseUrl = intent.getStringExtra(EXTRA_BASE_URL).orEmpty()
        userId = intent.getStringExtra(EXTRA_USER_ID).orEmpty()
        accessToken = intent.getStringExtra(EXTRA_ACCESS_TOKEN).orEmpty()

        if (baseUrl.isNotBlank() && userId.isNotBlank() && accessToken.isNotBlank()) return

        val activeServer = sessionStore.loadServers().firstOrNull() ?: return
        val embyApiService = EmbyApiService(this)
        baseUrl = baseUrl.ifBlank { embyApiService.buildBaseUrl(activeServer.address, activeServer.port) }
        userId = userId.ifBlank { activeServer.userId }
        accessToken = accessToken.ifBlank { activeServer.accessToken }
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
        val displayName = MusicLibraryRepository.displayName(currentLibrary).ifBlank { getString(R.string.music_library_name) }
        titleTextView.text = displayName
        partitionTextView.text = currentLibrary?.title?.ifBlank { getString(R.string.music_library_partition_fallback) }
            ?: getString(R.string.music_library_partition_fallback)

        if (state.isLoadingStats) {
            countTextView.text = getString(R.string.loading)
            songsStatsText.text = getString(R.string.loading)
            playlistsStatsText.text = getString(R.string.loading)
            albumsStatsText.text = getString(R.string.loading)
            artistsStatsText.text = getString(R.string.loading)
            statusTextView.visibility = View.GONE
            return
        }

        val stats = state.libraryStats
        if (stats != null) {
            countTextView.text = stats.songsCount.toString()
            songsStatsText.text = getString(R.string.music_library_count_with_unit, stats.songsCount)
            playlistsStatsText.text = getString(R.string.music_library_count_playlists, stats.playlistsCount)
            albumsStatsText.text = getString(R.string.music_library_count_albums, stats.albumsCount)
            artistsStatsText.text = getString(R.string.music_library_count_artists, stats.artistsCount)
            statusTextView.visibility = View.GONE
        } else {
            countTextView.text = "--"
            songsStatsText.text = "--"
            playlistsStatsText.text = "--"
            albumsStatsText.text = "--"
            artistsStatsText.text = "--"
            statusTextView.visibility = if (state.statsErrorMessage.isNullOrBlank()) View.GONE else View.VISIBLE
            statusTextView.text = state.statsErrorMessage
        }
    }

    private fun openList(browseType: MusicBrowseType) {
        startActivity(
            Intent(this, MusicListActivity::class.java)
                .putExtra(MusicListActivity.EXTRA_BROWSE_TYPE, browseType.name)
                .putExtra(EXTRA_BASE_URL, baseUrl)
                .putExtra(EXTRA_USER_ID, userId)
                .putExtra(EXTRA_ACCESS_TOKEN, accessToken)
        )
    }

    private fun showLibraryPicker() {
        val state = MusicLibraryRepository.currentState()
        if (state.musicLibraries.isEmpty()) return

        val labels = state.musicLibraries.map { MusicLibraryRepository.displayName(it) }.toTypedArray()
        val selectedIndex = state.musicLibraries.indexOfFirst { it.id == state.currentLibraryId }.coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle(R.string.music_settings_partition_dialog_title)
            .setSingleChoiceItems(labels, selectedIndex) { dialog, which ->
                MusicLibraryRepository.selectLibrary(state.musicLibraries[which].id)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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

    companion object {
        const val EXTRA_BASE_URL = "extra_base_url"
        const val EXTRA_USER_ID = "extra_user_id"
        const val EXTRA_ACCESS_TOKEN = "extra_access_token"
    }
}
