package com.liujiaming.embypro

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class SearchActivity : AppCompatActivity() {
    private val networkExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val embyApiService by lazy { EmbyApiService(this) }
    private val searchHistoryStore by lazy { SearchHistoryStore(this) }
    private val loadedItems = mutableListOf<MediaPosterUiModel>()

    private lateinit var baseUrl: String
    private lateinit var userId: String
    private lateinit var accessToken: String

    private lateinit var searchInput: EditText
    private lateinit var clearButton: ImageButton
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var historyContainer: View
    private lateinit var historyChipGroup: ChipGroup
    private lateinit var clearAllHistoryText: TextView
    private lateinit var adapter: MediaPosterAdapter

    private var currentQuery: String = ""
    private var startIndex = 0
    private var totalCount = Int.MAX_VALUE
    private var isLoading = false
    private val pageSize = 40

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdgeHelper.enable(this, lightSystemBars = true)
        setContentView(R.layout.activity_search)
        supportActionBar?.hide()

        baseUrl = intent.getStringExtra(EXTRA_BASE_URL).orEmpty()
        userId = intent.getStringExtra(EXTRA_USER_ID).orEmpty()
        accessToken = intent.getStringExtra(EXTRA_ACCESS_TOKEN).orEmpty()

        if (baseUrl.isBlank() || userId.isBlank() || accessToken.isBlank()) {
            Toast.makeText(this, getString(R.string.server_data_missing), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val topBar = findViewById<View>(R.id.searchTopBar)
        searchInput = findViewById(R.id.searchEditText)
        clearButton = findViewById(R.id.searchClearButton)
        recyclerView = findViewById(R.id.searchResultsRecyclerView)
        emptyView = findViewById(R.id.searchEmptyText)
        progressBar = findViewById(R.id.searchProgressBar)
        historyContainer = findViewById(R.id.searchHistoryContainer)
        historyChipGroup = findViewById(R.id.searchHistoryChipGroup)
        clearAllHistoryText = findViewById(R.id.searchHistoryClearAllText)

        findViewById<ImageButton>(R.id.searchBackButton).setDebouncedClickListener { finish() }
        clearButton.setDebouncedClickListener {
            searchInput.setText("")
            updateEmptyState()
        }
        clearAllHistoryText.setDebouncedClickListener {
            searchHistoryStore.clearAll()
            renderHistory()
            updateEmptyState()
        }

        searchInput.doAfterTextChanged {
            clearButton.visibility = if (it.isNullOrBlank()) View.GONE else View.VISIBLE
            if (it.isNullOrBlank()) {
                currentQuery = ""
                startIndex = 0
                totalCount = Int.MAX_VALUE
                loadedItems.clear()
                adapter.notifyDataSetChanged()
                progressBar.visibility = View.GONE
                renderHistory()
                updateEmptyState()
            }
        }
        searchInput.setOnEditorActionListener { _, actionId, event ->
            val imeHandled = actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE
            val enterHandled = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
            if (imeHandled || enterHandled) {
                submitSearch()
                true
            } else {
                false
            }
        }

        adapter = MediaPosterAdapter(
            loadedItems,
            R.layout.item_library_grid_card,
            accessToken,
            onItemClick = { openItem(it) }
        )
        recyclerView.layoutManager = GridLayoutManager(this, 2)
        recyclerView.adapter = adapter
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (dy <= 0 || currentQuery.isBlank() || isLoading) return
                val layoutManager = recyclerView.layoutManager as? GridLayoutManager ?: return
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                if (lastVisible >= loadedItems.size - 6 && loadedItems.size < totalCount) {
                    loadMoreSearchResults()
                }
            }
        })

        EdgeToEdgeHelper.applyInsets(topBar, applyTop = true)
        EdgeToEdgeHelper.applyInsets(recyclerView, applyBottom = true)
        renderHistory()
        updateEmptyState()
    }

    override fun onDestroy() {
        super.onDestroy()
        networkExecutor.shutdownNow()
    }

    private fun submitSearch() {
        val query = searchInput.text?.toString()?.trim().orEmpty()
        if (query.isBlank()) {
            Toast.makeText(this, getString(R.string.search_keyword_required), Toast.LENGTH_SHORT).show()
            return
        }

        hideKeyboard()
        searchHistoryStore.saveQuery(query)
        renderHistory()
        currentQuery = query
        startIndex = 0
        totalCount = Int.MAX_VALUE
        loadedItems.clear()
        adapter.notifyDataSetChanged()
        updateEmptyState()
        loadMoreSearchResults()
    }

    private fun loadMoreSearchResults() {
        if (currentQuery.isBlank()) return
        isLoading = true
        progressBar.visibility = View.VISIBLE
        networkExecutor.execute {
            val result = embyApiService.searchMediaItemsPage(
                baseUrl = baseUrl,
                userId = userId,
                accessToken = accessToken,
                query = currentQuery,
                startIndex = startIndex,
                limit = pageSize
            )
            runOnUiThread {
                isLoading = false
                progressBar.visibility = View.GONE
                result.onSuccess { page ->
                    val previousSize = loadedItems.size
                    loadedItems.addAll(page.items)
                    totalCount = page.totalCount
                    startIndex = loadedItems.size
                    adapter.notifyItemRangeInserted(previousSize, page.items.size)
                    updateEmptyState()
                }.onFailure { error ->
                    updateEmptyState()
                    Toast.makeText(
                        this,
                        error.message ?: getString(R.string.search_load_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun updateEmptyState() {
        val messageRes = when {
            currentQuery.isBlank() -> R.string.search_empty_hint
            loadedItems.isEmpty() && !isLoading -> R.string.search_no_results
            else -> null
        }
        historyContainer.visibility = if (currentQuery.isBlank()) View.VISIBLE else View.GONE
        emptyView.visibility = if (messageRes != null) View.VISIBLE else View.GONE
        recyclerView.visibility = if (messageRes != null && loadedItems.isEmpty()) View.GONE else View.VISIBLE
        messageRes?.let { emptyView.text = getString(it) }
    }

    private fun renderHistory() {
        val history = searchHistoryStore.loadHistory()
        historyChipGroup.removeAllViews()
        clearAllHistoryText.visibility = if (history.isEmpty()) View.GONE else View.VISIBLE
        historyContainer.visibility = if (history.isEmpty()) View.GONE else View.VISIBLE
        history.forEach { query ->
            historyChipGroup.addView(createHistoryChip(query))
        }
    }

    private fun createHistoryChip(query: String): Chip {
        return Chip(this).apply {
            text = query
            isCloseIconVisible = true
            setEnsureMinTouchTargetSize(false)
            chipBackgroundColor = getColorStateList(R.color.chip_background_color)
            setTextColor(getColor(R.color.text_primary))
            closeIconTint = getColorStateList(R.color.search_history_close_color)
            setDebouncedClickListener {
                searchInput.setText(query)
                searchInput.setSelection(query.length)
                submitSearch()
            }
            setOnCloseIconClickListener {
                searchHistoryStore.deleteQuery(query)
                renderHistory()
                updateEmptyState()
            }
        }
    }

    private fun openItem(item: MediaPosterUiModel) {
        if (item.id.isBlank()) return
        if (item.isFolder || item.itemType == "BoxSet" || item.itemType == "Folder") {
            startActivity(
                Intent(this, LibraryItemsActivity::class.java)
                    .putExtra(LibraryItemsActivity.EXTRA_LIBRARY_ID, item.id)
                    .putExtra(LibraryItemsActivity.EXTRA_LIBRARY_NAME, item.title)
                    .putExtra(LibraryItemsActivity.EXTRA_BASE_URL, baseUrl)
                    .putExtra(LibraryItemsActivity.EXTRA_USER_ID, userId)
                    .putExtra(LibraryItemsActivity.EXTRA_ACCESS_TOKEN, accessToken)
            )
            return
        }

        val playableItems = loadedItems.filter { !it.isFolder && it.itemType != "BoxSet" && it.itemType != "Folder" }
        val playlistIds = ArrayList(playableItems.map { it.id })
        val playlistTitles = ArrayList(playableItems.map { it.title })
        val playlistIndex = playableItems.indexOfFirst { it.id == item.id }

        startActivity(
            Intent(this, VideoDetailActivity::class.java)
                .putExtra(VideoDetailActivity.EXTRA_ITEM_ID, item.id)
                .putExtra(VideoDetailActivity.EXTRA_BASE_URL, baseUrl)
                .putExtra(VideoDetailActivity.EXTRA_USER_ID, userId)
                .putExtra(VideoDetailActivity.EXTRA_ACCESS_TOKEN, accessToken)
                .putStringArrayListExtra(VideoDetailActivity.EXTRA_PLAYLIST_ITEM_IDS, playlistIds)
                .putStringArrayListExtra(VideoDetailActivity.EXTRA_PLAYLIST_ITEM_TITLES, playlistTitles)
                .putExtra(VideoDetailActivity.EXTRA_PLAYLIST_INDEX, playlistIndex)
        )
    }

    private fun hideKeyboard() {
        val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        inputMethodManager?.hideSoftInputFromWindow(searchInput.windowToken, 0)
    }

    companion object {
        const val EXTRA_BASE_URL = "extra_base_url"
        const val EXTRA_USER_ID = "extra_user_id"
        const val EXTRA_ACCESS_TOKEN = "extra_access_token"
    }
}
