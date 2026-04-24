package com.liujiaming.embypro

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.button.MaterialButton

/**
 * Settings activity for music library configuration.
 * Displays library statistics and allows partition selection and alias customization.
 */
class MusicSettingsActivity : AppCompatActivity() {
    private val sessionStore by lazy { ServerSessionStore(this) }
    private val serverRepository by lazy { ServerRepository(this) }

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

    private lateinit var connection: ServerConnection

    private val stateListener = MusicLibraryStateListener { state ->
        renderState(state)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdgeHelper.enable(this, lightSystemBars = GlobalThemeStore(this).loadTheme().lightSystemBars)
        setContentView(R.layout.activity_music_settings)
        supportActionBar?.hide()
        GlobalThemeManager.apply(this)

        connection = requireServerConnection(sessionStore, serverRepository) ?: return

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
            MusicLibraryRepository.connect(
                this,
                connection.baseUrl,
                connection.userId,
                connection.accessToken,
                forceRefresh = true
            )
        }
        currentPartitionRow.setDebouncedClickListener { showLibraryPicker() }
        aliasRow.setDebouncedClickListener { showAliasEditor() }

        EdgeToEdgeHelper.applyInsets(topBar, applyTop = true)
        EdgeToEdgeHelper.applyInsets(contentScrollView, applyBottom = true)
    }

    override fun onStart() {
        super.onStart()
        MusicLibraryRepository.subscribe(stateListener)
        MusicLibraryRepository.connect(this, connection.baseUrl, connection.userId, connection.accessToken)
    }

    override fun onStop() {
        MusicLibraryRepository.unsubscribe(stateListener)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        GlobalThemeManager.apply(this)
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
        }

        showMusicPartitionPickerDialog(labels, selectedIndex) { which ->
            MusicLibraryRepository.selectLibrary(state.musicLibraries[which].id)
        }
    }

    private fun showAliasEditor() {
        val currentState = MusicLibraryRepository.currentState()
        val library = currentState.currentMusicLibrary ?: return
        val currentAlias = currentState.aliasMap[library.id].orEmpty()
        val dialogView = LayoutInflater.from(this)
            .inflate(R.layout.dialog_music_library_alias_editor, null)
        val aliasInput = dialogView.findViewById<TextInputEditText>(R.id.musicAliasInput)
        val saveButton = dialogView.findViewById<TextView>(R.id.musicAliasSaveButton)
        val clearButton = dialogView.findViewById<TextView>(R.id.musicAliasClearButton)
        val cancelButton = dialogView.findViewById<TextView>(R.id.musicAliasCancelButton)

        aliasInput.setText(currentAlias)
        aliasInput.setSelection(aliasInput.text?.length ?: 0)

        val dialog = createMusicGlassDialog(dialogView)

        saveButton.setDebouncedClickListener {
            val alias = aliasInput.text?.toString().orEmpty().trim()
            dialog.dismiss()
            if (alias.isBlank()) {
                MusicLibraryRepository.clearAlias(library.id)
            } else {
                MusicLibraryRepository.saveAlias(library.id, alias)
            }
        }
        clearButton.setDebouncedClickListener {
            dialog.dismiss()
            MusicLibraryRepository.clearAlias(library.id)
        }
        cancelButton.setDebouncedClickListener { dialog.dismiss() }

        dialog.applyMusicGlassWindow(this)
        aliasInput.requestFocus()
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
}
