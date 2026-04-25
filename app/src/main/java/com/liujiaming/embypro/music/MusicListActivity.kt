package com.liujiaming.embypro

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

/**
 * Activity displaying a list of music items (songs, albums, artists, playlists).
 * Supports search functionality and navigation to nested lists or player.
 */
class MusicListActivity : AppCompatActivity() {
    private val sessionStore by lazy { ServerSessionStore(this) }
    private val musicRepository by lazy { MusicRepository(this) }
    private val mediaRepository by lazy { MediaRepository(this) }
    private val offlineCache by lazy { MusicOfflineCache(this) }
    private val serverRepository by lazy { ServerRepository(this) }

    private lateinit var connection: ServerConnection
    private lateinit var browseType: MusicBrowseType

    private var intentLibraryId: String? = null
    private var containerId: String? = null
    private var containerTitle: String? = null
    private var lastLibraryId: String? = null
    private var isLoadingPage = false
    private var deleteRequestInFlight = false
    
    private var currentSearchQuery: String = ""

    private lateinit var recyclerView: RecyclerView
    private lateinit var loadingContainer: View
    private lateinit var emptyContainer: View
    private lateinit var emptyTextView: TextView
    private lateinit var errorContainer: View
    private lateinit var errorTextView: TextView
    private lateinit var pageTitleView: TextView
    private lateinit var pageSubtitleView: TextView
    private lateinit var shufflePlayButton: MaterialButton
    private lateinit var adapter: MusicListAdapter
    private lateinit var searchInput: EditText
    private lateinit var searchClearButton: ImageButton

    private val items = mutableListOf<MusicListEntryUiModel>()
    private var isCurrentSongList = false

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
        intentLibraryId = intent.getStringExtra(EXTRA_LIBRARY_ID)
        containerId = intent.getStringExtra(EXTRA_CONTAINER_ID)
        containerTitle = intent.getStringExtra(EXTRA_CONTAINER_TITLE)
        if (intentLibraryId != null) {
            lastLibraryId = intentLibraryId
        }

        val topBar = findViewById<View>(R.id.musicListTopBar)
        recyclerView = findViewById(R.id.musicListRecyclerView)
        loadingContainer = findViewById(R.id.musicListLoadingContainer)
        emptyContainer = findViewById(R.id.musicListEmptyContainer)
        emptyTextView = findViewById(R.id.musicListEmptyText)
        errorContainer = findViewById(R.id.musicListErrorContainer)
        errorTextView = findViewById(R.id.musicListErrorText)
        pageTitleView = findViewById(R.id.musicListTitleText)
        pageSubtitleView = findViewById(R.id.musicListSubtitleText)
        shufflePlayButton = findViewById(R.id.musicListShufflePlayButton)
        searchInput = findViewById(R.id.musicListSearchInput)
        searchClearButton = findViewById(R.id.musicListSearchClearButton)

        findViewById<ImageButton>(R.id.musicListBackButton).setDebouncedClickListener { finish() }
        findViewById<View>(R.id.musicListRetryButton).setDebouncedClickListener {
            if (isLocalBrowse()) {
                loadLocalPage(currentSearchQuery)
            } else {
                MusicLibraryRepository.connect(
                    this,
                    connection.baseUrl,
                    connection.userId,
                    connection.accessToken,
                    forceRefresh = true
                )
            }
        }
        shufflePlayButton.setDebouncedClickListener { startShufflePlayback() }
        
        searchInput.doAfterTextChanged {
            searchClearButton.visibility = if (it.isNullOrBlank()) View.GONE else View.VISIBLE
            if (it.isNullOrBlank()) {
                currentSearchQuery = ""
                if (isLocalBrowse()) {
                    loadLocalPage()
                } else if (lastLibraryId != null) {
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

        adapter = MusicListAdapter(
            items = items,
            accessToken = connection.accessToken,
            onItemClick = { entry ->
                if (entry.kind == MusicEntryKind.SONG) {
                    openPlayer(entry)
                } else {
                    openNestedList(entry)
                }
            },
            canDeleteItem = { entry ->
                if (isLocalBrowse()) {
                    entry.kind == MusicEntryKind.SONG
                } else {
                    entry.kind == MusicEntryKind.SONG || isPlaylistEntry(entry)
                }
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        attachSwipeToDelete()

        EdgeToEdgeHelper.applyInsets(topBar, applyTop = true)
        EdgeToEdgeHelper.applyInsets(recyclerView, applyBottom = true)
    }

    override fun onStart() {
        super.onStart()
        if (isLocalBrowse()) {
            loadLocalPage(currentSearchQuery)
        } else {
            MusicLibraryRepository.subscribe(stateListener)
            MusicLibraryRepository.connect(this, connection.baseUrl, connection.userId, connection.accessToken)
        }
    }

    override fun onStop() {
        if (!isLocalBrowse()) {
            MusicLibraryRepository.unsubscribe(stateListener)
        }
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
                    isCurrentSongList = page.isSongList
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
            libraryId = lastLibraryId,
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

    private fun startShufflePlayback() {
        val queue = items.filter { it.kind == MusicEntryKind.SONG }
        if (queue.isEmpty()) return
        val shuffledQueue = queue.shuffled()
        AppNavigator.openMusicPlayer(
            activity = this,
            connection = connection,
            libraryId = lastLibraryId,
            queueTitle = pageTitleView.text.toString(),
            queueIds = ArrayList(shuffledQueue.map { it.id }),
            queueTitles = ArrayList(shuffledQueue.map { it.title }),
            queueSubtitles = ArrayList(shuffledQueue.map { it.subtitle }),
            queueImages = ArrayList(shuffledQueue.map { it.imageUrl.orEmpty() }),
            queueIndex = 0,
            shuffleModeEnabled = true
        )
    }

    private fun attachSwipeToDelete() {
        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D95C536C")
        }
        val deleteIcon = ContextCompat.getDrawable(this, R.drawable.ic_video_action_delete)?.mutate()
        deleteIcon?.setTint(Color.WHITE)
        val cornerRadius = dp(22).toFloat()
        val horizontalPadding = dp(16)

        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun getSwipeDirs(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                if (deleteRequestInFlight) return 0
                return if (adapter.canSwipeDelete(viewHolder.bindingAdapterPosition)) {
                    super.getSwipeDirs(recyclerView, viewHolder)
                } else {
                    0
                }
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                val item = adapter.getItemAt(position)
                if (item == null || !adapter.canSwipeDelete(position)) {
                    if (position >= 0) {
                        adapter.restoreItem(position)
                    }
                    return
                }
                showDeleteMusicItemConfirmDialog(item, position)
            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && dX < 0f && adapter.canSwipeDelete(viewHolder.bindingAdapterPosition)) {
                    val itemView = viewHolder.itemView
                    val rect = RectF(
                        itemView.right + dX + dp(6),
                        itemView.top.toFloat(),
                        itemView.right.toFloat(),
                        itemView.bottom.toFloat() - dp(10)
                    )
                    c.drawRoundRect(rect, cornerRadius, cornerRadius, backgroundPaint)

                    deleteIcon?.let { icon ->
                        val iconTop = itemView.top + ((itemView.height - dp(10) - icon.intrinsicHeight) / 2)
                        val iconMargin = horizontalPadding
                        val iconLeft = itemView.right - iconMargin - icon.intrinsicWidth
                        val iconRight = itemView.right - iconMargin
                        val iconBottom = iconTop + icon.intrinsicHeight
                        icon.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                        icon.draw(c)
                    }
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        }).attachToRecyclerView(recyclerView)
    }

    private fun showDeleteMusicItemConfirmDialog(item: MusicListEntryUiModel, position: Int) {
        var shouldRestoreOnDismiss = true
        val dialogView = layoutInflater.inflate(R.layout.dialog_clear_played_state, null)
        val deletingLocal = isLocalBrowse()
        val deletingPlaylist = isPlaylistEntry(item)
        dialogView.findViewById<TextView>(R.id.clearPlayedStateDialogTitle)
            .text = getString(
                if (deletingLocal) R.string.delete_local_music_confirm_title
                else if (deletingPlaylist) R.string.delete_playlist_confirm_title
                else R.string.delete_music_list_confirm_title
            )
        dialogView.findViewById<TextView>(R.id.clearPlayedStateDialogMessage)
            .text = getString(
                if (deletingLocal) R.string.delete_local_music_confirm_message
                else if (deletingPlaylist) R.string.delete_playlist_confirm_message
                else R.string.delete_music_list_confirm_message,
                item.title
            )
        dialogView.findViewById<TextView>(R.id.clearPlayedStateDialogConfirmButton)
            .text = getString(
                if (deletingLocal) R.string.action_delete_local_music
                else if (deletingPlaylist) R.string.action_delete_playlist
                else R.string.action_delete_music
            )

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        dialog.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        dialog.window?.attributes = dialog.window?.attributes?.apply {
            dimAmount = 0.22f
        }

        dialog.setOnDismissListener {
            if (shouldRestoreOnDismiss) {
                adapter.restoreItem(position)
            }
        }
        dialogView.findViewById<TextView>(R.id.clearPlayedStateDialogCancelButton)
            .setDebouncedClickListener { dialog.dismiss() }
        dialogView.findViewById<TextView>(R.id.clearPlayedStateDialogConfirmButton)
            .setDebouncedClickListener {
                shouldRestoreOnDismiss = false
                dialog.dismiss()
                deleteMusicItem(item, position)
            }
        dialog.show()
    }

    private fun deleteMusicItem(item: MusicListEntryUiModel, position: Int) {
        if (deleteRequestInFlight) return
        deleteRequestInFlight = true
        val deletingLocal = isLocalBrowse()
        val deletingPlaylist = isPlaylistEntry(item)

        AppExecutors.io.execute {
            val result = if (deletingLocal) {
                runCatching { offlineCache.remove(connection, item.id) }
            } else {
                mediaRepository.deleteItem(connection, item.id)
            }
            runOnUiThread {
                deleteRequestInFlight = false
                result.onSuccess {
                    adapter.removeItemAt(position)
                    Toast.makeText(
                        this,
                        getString(
                            if (deletingLocal) R.string.delete_local_music_success
                            else if (deletingPlaylist) R.string.delete_playlist_success
                            else R.string.delete_music_success
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                    if (items.isEmpty()) {
                        showEmpty()
                    } else {
                        showContent()
                    }
                }.onFailure { error ->
                    adapter.restoreItem(position)
                    Toast.makeText(
                        this,
                        userFriendlyErrorMessage(
                            error,
                            if (deletingLocal) R.string.delete_local_music_failed
                            else if (deletingPlaylist) R.string.delete_playlist_failed
                            else R.string.delete_music_failed
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun loadLocalPage(query: String = "") {
        if (isLoadingPage) return
        isLoadingPage = true
        showLoading()

        AppExecutors.io.execute {
            val result = offlineCache.buildLocalPage(
                connection = connection,
                libraryId = intentLibraryId ?: lastLibraryId,
                query = query
            )
            runOnUiThread {
                isLoadingPage = false
                result.onSuccess { page ->
                    pageTitleView.text = page.title
                    pageSubtitleView.text = page.subtitle
                    isCurrentSongList = page.isSongList
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

    private fun showLoading() {
        shufflePlayButton.visibility = View.GONE
        recyclerView.visibility = View.GONE
        loadingContainer.visibility = View.VISIBLE
        emptyContainer.visibility = View.GONE
        errorContainer.visibility = View.GONE
    }

    private fun showEmpty() {
        shufflePlayButton.visibility = View.GONE
        recyclerView.visibility = View.GONE
        loadingContainer.visibility = View.GONE
        emptyContainer.visibility = View.VISIBLE
        errorContainer.visibility = View.GONE
        emptyTextView.text = if (isLocalBrowse()) {
            getString(R.string.music_local_empty)
        } else {
            getString(R.string.music_list_empty)
        }
    }

    private fun showError(message: String) {
        shufflePlayButton.visibility = View.GONE
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
        shufflePlayButton.visibility = if (isCurrentSongList && items.any { it.kind == MusicEntryKind.SONG }) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }
    
    private fun performMusicSearch() {
        val query = searchInput.text?.toString()?.trim().orEmpty()
        if (query.isBlank()) {
            Toast.makeText(this, getString(R.string.search_keyword_required), Toast.LENGTH_SHORT).show()
            return
        }
        
        hideKeyboard()
        currentSearchQuery = query

        if (isLocalBrowse()) {
            loadLocalPage(currentSearchQuery)
            return
        }
        
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
                    isCurrentSongList = page.isSongList
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

    private fun dp(value: Int): Int {
        return (resources.displayMetrics.density * value).toInt()
    }

    private fun isPlaylistEntry(item: MusicListEntryUiModel): Boolean {
        return browseType == MusicBrowseType.PLAYLISTS &&
            containerId.isNullOrBlank() &&
            item.browseType == MusicBrowseType.PLAYLISTS &&
            item.itemType == "Playlist"
    }

    private fun isLocalBrowse(): Boolean = browseType == MusicBrowseType.LOCAL
    
    companion object {
        const val EXTRA_BROWSE_TYPE = "extra_browse_type"
        const val EXTRA_LIBRARY_ID = "extra_library_id"
        const val EXTRA_CONTAINER_ID = "extra_container_id"
        const val EXTRA_CONTAINER_TITLE = "extra_container_title"
    }
}
