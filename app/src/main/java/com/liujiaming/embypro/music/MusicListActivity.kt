package com.liujiaming.embypro

import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Activity displaying a list of music items (songs, albums, artists, playlists).
 * Supports search functionality and navigation to nested lists or player.
 */
class MusicListActivity : AppCompatActivity() {
    private val sessionStore by lazy { ServerSessionStore(this) }
    private val musicRepository by lazy { MusicRepository(this) }
    private val serverRepository by lazy { ServerRepository(this) }

    private lateinit var connection: ServerConnection
    private lateinit var browseType: MusicBrowseType

    private var containerId: String? = null
    private var containerTitle: String? = null
    private var lastLibraryId: String? = null
    private var isLoadingPage = false
    
    private var currentSearchQuery: String = ""

    private lateinit var recyclerView: RecyclerView
    private lateinit var loadingContainer: View
    private lateinit var emptyContainer: View
    private lateinit var errorContainer: View
    private lateinit var errorTextView: TextView
    private lateinit var pageTitleView: TextView
    private lateinit var pageSubtitleView: TextView
    private lateinit var adapter: MusicListAdapter
    private lateinit var searchInput: EditText
    private lateinit var searchClearButton: ImageButton

    private val items = mutableListOf<MusicListEntryUiModel>()

    private val stateListener = MusicLibraryStateListener { state ->
        renderLibraryState(state)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdgeHelper.enable(this, lightSystemBars = GlobalThemeStore(this).loadTheme().lightSystemBars)
        setContentView(R.layout.activity_music_list)
        supportActionBar?.hide()
        GlobalThemeManager.apply(this)

        connection = requireServerConnection(sessionStore, serverRepository) ?: return
        browseType = MusicBrowseType.valueOf(
            intent.getStringExtra(EXTRA_BROWSE_TYPE).orEmpty().ifBlank { MusicBrowseType.SONGS.name }
        )
        containerId = intent.getStringExtra(EXTRA_CONTAINER_ID)
        containerTitle = intent.getStringExtra(EXTRA_CONTAINER_TITLE)

        val topBar = findViewById<View>(R.id.musicListTopBar)
        recyclerView = findViewById(R.id.musicListRecyclerView)
        loadingContainer = findViewById(R.id.musicListLoadingContainer)
        emptyContainer = findViewById(R.id.musicListEmptyContainer)
        errorContainer = findViewById(R.id.musicListErrorContainer)
        errorTextView = findViewById(R.id.musicListErrorText)
        pageTitleView = findViewById(R.id.musicListTitleText)
        pageSubtitleView = findViewById(R.id.musicListSubtitleText)
        searchInput = findViewById(R.id.musicListSearchInput)
        searchClearButton = findViewById(R.id.musicListSearchClearButton)

        findViewById<ImageButton>(R.id.musicListBackButton).setDebouncedClickListener { finish() }
        findViewById<View>(R.id.musicListRetryButton).setDebouncedClickListener {
            MusicLibraryRepository.connect(
                this,
                connection.baseUrl,
                connection.userId,
                connection.accessToken,
                forceRefresh = true
            )
        }
        
        searchInput.doAfterTextChanged {
            searchClearButton.visibility = if (it.isNullOrBlank()) View.GONE else View.VISIBLE
            if (it.isNullOrBlank()) {
                currentSearchQuery = ""
                if (lastLibraryId != null) {
                    loadPage(lastLibraryId!!)
                }
            }
        }
        searchInput.setOnEditorActionListener { _, actionId, event ->
            val imeHandled = actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE
            val enterHandled = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
            if (imeHandled || enterHandled) {
                performMusicSearch()
                true
            } else {
                false
            }
        }
        searchClearButton.setDebouncedClickListener {
            searchInput.setText("")
            hideKeyboard()
        }

        adapter = MusicListAdapter(items, connection.accessToken) { entry ->
            if (entry.kind == MusicEntryKind.SONG) {
                openPlayer(entry)
            } else {
                openNestedList(entry)
            }
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        EdgeToEdgeHelper.applyInsets(topBar, applyTop = true)
        EdgeToEdgeHelper.applyInsets(recyclerView, applyBottom = true)
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

    private fun renderLibraryState(state: MusicLibraryState) {
        if (state.errorMessage != null && state.musicLibraries.isEmpty()) {
            showError(state.errorMessage)
            return
        }

        if (state.isLoadingLibraries && state.musicLibraries.isEmpty()) {
            showLoading()
            return
        }

        val currentLibrary = state.currentMusicLibrary ?: run {
            showEmpty()
            return
        }

        if (lastLibraryId != null && lastLibraryId != currentLibrary.id && !containerId.isNullOrBlank()) {
            containerId = null
            containerTitle = null
        }

        if (lastLibraryId != currentLibrary.id || items.isEmpty()) {
            lastLibraryId = currentLibrary.id
            loadPage(currentLibrary.id)
        }
    }

    private fun loadPage(libraryId: String) {
        if (isLoadingPage) return
        isLoadingPage = true
        showLoading()

        AppExecutors.io.execute {
            val result = musicRepository.fetchMusicBrowsePage(
                connection = connection,
                libraryId = libraryId,
                browseType = browseType,
                containerId = containerId,
                containerTitle = containerTitle
            )
            runOnUiThread {
                isLoadingPage = false
                result.onSuccess { page ->
                    pageTitleView.text = page.title
                    pageSubtitleView.text = page.subtitle
                    adapter.submitItems(page.items)
                    if (page.items.isEmpty()) {
                        showEmpty()
                    } else {
                        showContent()
                    }
                }.onFailure { error ->
                    showError(userFriendlyErrorMessage(error, R.string.music_list_load_failed))
                }
            }
        }
    }

    private fun openNestedList(entry: MusicListEntryUiModel) {
        AppNavigator.openMusicList(
            activity = this,
            connection = connection,
            browseType = entry.browseType,
            containerId = entry.id,
            containerTitle = entry.title
        )
    }

    private fun openPlayer(entry: MusicListEntryUiModel) {
        val queue = items.filter { it.kind == MusicEntryKind.SONG }
        val currentIndex = queue.indexOfFirst { it.id == entry.id }
        if (currentIndex < 0) return

        AppNavigator.openMusicPlayer(
            activity = this,
            connection = connection,
            libraryId = lastLibraryId,
            queueTitle = pageTitleView.text.toString(),
            queueIds = ArrayList(queue.map { it.id }),
            queueTitles = ArrayList(queue.map { it.title }),
            queueSubtitles = ArrayList(queue.map { it.subtitle }),
            queueImages = ArrayList(queue.map { it.imageUrl.orEmpty() }),
            queueIndex = currentIndex
        )
    }

    private fun showLoading() {
        recyclerView.visibility = View.GONE
        loadingContainer.visibility = View.VISIBLE
        emptyContainer.visibility = View.GONE
        errorContainer.visibility = View.GONE
    }

    private fun showEmpty() {
        recyclerView.visibility = View.GONE
        loadingContainer.visibility = View.GONE
        emptyContainer.visibility = View.VISIBLE
        errorContainer.visibility = View.GONE
    }

    private fun showError(message: String) {
        recyclerView.visibility = View.GONE
        loadingContainer.visibility = View.GONE
        emptyContainer.visibility = View.GONE
        errorContainer.visibility = View.VISIBLE
        errorTextView.text = message
    }

    private fun showContent() {
        recyclerView.visibility = View.VISIBLE
        loadingContainer.visibility = View.GONE
        emptyContainer.visibility = View.GONE
        errorContainer.visibility = View.GONE
    }
    
    private fun performMusicSearch() {
        val query = searchInput.text?.toString()?.trim().orEmpty()
        if (query.isBlank()) {
            Toast.makeText(this, getString(R.string.search_keyword_required), Toast.LENGTH_SHORT).show()
            return
        }
        
        hideKeyboard()
        currentSearchQuery = query
        
        if (lastLibraryId == null) {
            Toast.makeText(this, getString(R.string.music_list_load_failed), Toast.LENGTH_SHORT).show()
            return
        }
        
        isLoadingPage = true
        showLoading()
        
        AppExecutors.io.execute {
            val result = musicRepository.searchMusicItems(
                connection = connection,
                libraryId = lastLibraryId!!,
                query = currentSearchQuery
            )
            runOnUiThread {
                isLoadingPage = false
                result.onSuccess { page ->
                    pageTitleView.text = page.title
                    pageSubtitleView.text = page.subtitle
                    adapter.submitItems(page.items)
                    if (page.items.isEmpty()) {
                        showEmpty()
                    } else {
                        showContent()
                    }
                }.onFailure { error ->
                    showError(userFriendlyErrorMessage(error, R.string.music_search_failed))
                }
            }
        }
    }
    
    private fun hideKeyboard() {
        val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        inputMethodManager?.hideSoftInputFromWindow(searchInput.windowToken, 0)
    }
    
    companion object {
        const val EXTRA_BROWSE_TYPE = "extra_browse_type"
        const val EXTRA_CONTAINER_ID = "extra_container_id"
        const val EXTRA_CONTAINER_TITLE = "extra_container_title"
    }
}
