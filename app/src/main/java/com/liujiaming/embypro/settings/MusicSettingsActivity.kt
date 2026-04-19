package com.liujiaming.embypro

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class MusicSettingsActivity : AppCompatActivity() {
    private val sessionStore by lazy { ServerSessionStore(this) }

    private lateinit var topBar: View
    private lateinit var loadingContainer: View
    private lateinit var emptyContainer: View
    private lateinit var errorContainer: View
    private lateinit var contentScrollView: View
    private lateinit var retryButton: MaterialButton
    private lateinit var errorTextView: TextView
    private lateinit var inlineStatusText: TextView
    private lateinit var currentPartitionRow: View
    private lateinit var aliasRow: View
    private lateinit var songsValueText: TextView
    private lateinit var albumsValueText: TextView
    private lateinit var artistsValueText: TextView
    private lateinit var playlistsValueText: TextView
    private lateinit var partitionValueText: TextView
    private lateinit var aliasValueText: TextView

    private lateinit var baseUrl: String
    private lateinit var userId: String
    private lateinit var accessToken: String

    private val stateListener = MusicLibraryStateListener { state ->
        renderState(state)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdgeHelper.enable(this, lightSystemBars = GlobalThemeStore(this).loadTheme().lightSystemBars)
        setContentView(R.layout.activity_music_settings)
        supportActionBar?.hide()
        GlobalThemeManager.apply(this)

        resolveSessionParams()

        topBar = findViewById(R.id.musicSettingsTopBar)
        loadingContainer = findViewById(R.id.musicSettingsLoadingContainer)
        emptyContainer = findViewById(R.id.musicSettingsEmptyContainer)
        errorContainer = findViewById(R.id.musicSettingsErrorContainer)
        contentScrollView = findViewById(R.id.musicSettingsScrollView)
        retryButton = findViewById(R.id.musicSettingsRetryButton)
        errorTextView = findViewById(R.id.musicSettingsErrorText)
        inlineStatusText = findViewById(R.id.musicSettingsInlineStatusText)
        currentPartitionRow = findViewById(R.id.musicSettingsPartitionRow)
        aliasRow = findViewById(R.id.musicSettingsAliasRow)
        songsValueText = findViewById(R.id.musicSettingsSongsValueText)
        albumsValueText = findViewById(R.id.musicSettingsAlbumsValueText)
        artistsValueText = findViewById(R.id.musicSettingsArtistsValueText)
        playlistsValueText = findViewById(R.id.musicSettingsPlaylistsValueText)
        partitionValueText = findViewById(R.id.musicSettingsPartitionValueText)
        aliasValueText = findViewById(R.id.musicSettingsAliasValueText)

        findViewById<ImageButton>(R.id.musicSettingsBackButton).setDebouncedClickListener { finish() }
        retryButton.setDebouncedClickListener {
            MusicLibraryRepository.connect(this, baseUrl, userId, accessToken, forceRefresh = true)
        }
        currentPartitionRow.setDebouncedClickListener { showLibraryPicker() }
        aliasRow.setDebouncedClickListener { showAliasEditor() }

        EdgeToEdgeHelper.applyInsets(topBar, applyTop = true)
        EdgeToEdgeHelper.applyInsets(contentScrollView, applyBottom = true)
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

    override fun onResume() {
        super.onResume()
        GlobalThemeManager.apply(this)
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
            showFullScreenError(state.errorMessage)
            return
        }

        if (state.isLoadingLibraries && state.musicLibraries.isEmpty()) {
            showLoadingState()
            return
        }

        if (state.isEmpty) {
            showEmptyState()
            return
        }

        showContentState()
        val currentLibrary = state.currentMusicLibrary
        val displayName = MusicLibraryRepository.displayName(currentLibrary)
        partitionValueText.text = displayName.ifBlank { "--" }
        aliasValueText.text = displayName.ifBlank { "--" }

        when {
            state.isLoadingStats -> {
                songsValueText.text = getString(R.string.loading)
                albumsValueText.text = getString(R.string.loading)
                artistsValueText.text = getString(R.string.loading)
                playlistsValueText.text = getString(R.string.loading)
                inlineStatusText.visibility = View.GONE
            }

            state.libraryStats != null -> {
                songsValueText.text = getString(R.string.music_settings_count_value, state.libraryStats.songsCount)
                albumsValueText.text = getString(R.string.music_settings_count_value, state.libraryStats.albumsCount)
                artistsValueText.text = getString(R.string.music_settings_count_value, state.libraryStats.artistsCount)
                playlistsValueText.text = getString(R.string.music_settings_count_value, state.libraryStats.playlistsCount)
                inlineStatusText.visibility = View.GONE
            }

            else -> {
                songsValueText.text = "--"
                albumsValueText.text = "--"
                artistsValueText.text = "--"
                playlistsValueText.text = "--"
                inlineStatusText.visibility = if (state.statsErrorMessage.isNullOrBlank()) View.GONE else View.VISIBLE
                inlineStatusText.text = state.statsErrorMessage
            }
        }
    }

    private fun showLibraryPicker() {
        val state = MusicLibraryRepository.currentState()
        if (state.musicLibraries.isEmpty()) {
            Toast.makeText(this, getString(R.string.music_settings_empty), Toast.LENGTH_SHORT).show()
            return
        }

        val selectedIndex = state.musicLibraries.indexOfFirst { it.id == state.currentLibraryId }.coerceAtLeast(0)
        val labels = state.musicLibraries.map { library ->
            MusicLibraryRepository.displayName(library)
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(R.string.music_settings_partition_dialog_title)
            .setSingleChoiceItems(labels, selectedIndex) { dialog, which ->
                MusicLibraryRepository.selectLibrary(state.musicLibraries[which].id)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showAliasEditor() {
        val currentState = MusicLibraryRepository.currentState()
        val library = currentState.currentMusicLibrary ?: return
        val currentAlias = currentState.aliasMap[library.id].orEmpty()
        val editText = EditText(this).apply {
            setSingleLine()
            setText(currentAlias)
            setSelection(text.length)
            hint = getString(R.string.music_settings_alias_hint)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.music_settings_alias_dialog_title)
            .setView(editText)
            .setPositiveButton(R.string.save_changes) { _, _ ->
                val alias = editText.text?.toString().orEmpty().trim()
                if (alias.isBlank()) {
                    MusicLibraryRepository.clearAlias(library.id)
                } else {
                    MusicLibraryRepository.saveAlias(library.id, alias)
                }
            }
            .setNeutralButton(R.string.music_settings_clear_alias) { _, _ ->
                MusicLibraryRepository.clearAlias(library.id)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showLoadingState() {
        loadingContainer.visibility = View.VISIBLE
        emptyContainer.visibility = View.GONE
        errorContainer.visibility = View.GONE
        contentScrollView.visibility = View.GONE
    }

    private fun showEmptyState() {
        loadingContainer.visibility = View.GONE
        emptyContainer.visibility = View.VISIBLE
        errorContainer.visibility = View.GONE
        contentScrollView.visibility = View.GONE
    }

    private fun showFullScreenError(message: String) {
        loadingContainer.visibility = View.GONE
        emptyContainer.visibility = View.GONE
        errorContainer.visibility = View.VISIBLE
        contentScrollView.visibility = View.GONE
        errorTextView.text = message
    }

    private fun showContentState() {
        loadingContainer.visibility = View.GONE
        emptyContainer.visibility = View.GONE
        errorContainer.visibility = View.GONE
        contentScrollView.visibility = View.VISIBLE
    }

    companion object {
        const val EXTRA_BASE_URL = "extra_base_url"
        const val EXTRA_USER_ID = "extra_user_id"
        const val EXTRA_ACCESS_TOKEN = "extra_access_token"
    }
}
