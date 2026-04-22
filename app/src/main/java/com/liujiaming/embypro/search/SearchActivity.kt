package com.liujiaming.embypro

import android.content.Context
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

/**
 * Activity for searching media items with history support.
 * Displays search results in a grid layout and maintains search history.
 */
class SearchActivity : AppCompatActivity() {
    private val networkExecutor: ExecutorService = AppExecutors.io
    private val mediaRepository by lazy { MediaRepository(this) }
    private val preferenceStore by lazy { AppPreferenceStore(this) }
    private val sessionStore by lazy { ServerSessionStore(this) }
    private val serverRepository by lazy { ServerRepository(this) }
    private val loadedItems = mutableListOf<MediaPosterUiModel>()

    private lateinit var connection: ServerConnection

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
        EdgeToEdgeHelper.enable(this, lightSystemBars = GlobalThemeStore(this).loadTheme().lightSystemBars)
        setContentView(R.layout.activity_search)
        supportActionBar?.hide()
        GlobalThemeManager.apply(this)

        connection = requireServerConnection(sessionStore, serverRepository) ?: return

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
            preferenceStore.clearSearchHistory()
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
            connection.accessToken,
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

    private fun submitSearch() {
        val query = searchInput.text?.toString()?.trim().orEmpty()
        if (query.isBlank()) {
            Toast.makeText(this, getString(R.string.search_keyword_required), Toast.LENGTH_SHORT).show()
            return
        }

        hideKeyboard()
        preferenceStore.saveSearchQuery(query)
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
            val result = mediaRepository.searchMediaItemsPage(connection, currentQuery, startIndex, pageSize)
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
                        userFriendlyErrorMessage(error, R.string.search_load_failed),
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
        val history = preferenceStore.loadSearchHistory()
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
                preferenceStore.deleteSearchQuery(query)
                renderHistory()
                updateEmptyState()
            }
        }
    }

    private fun openItem(item: MediaPosterUiModel) {
        AppNavigator.openPosterItem(this, connection, item, loadedItems)
    }

    private fun hideKeyboard() {
        val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        inputMethodManager?.hideSoftInputFromWindow(searchInput.windowToken, 0)
    }
}
