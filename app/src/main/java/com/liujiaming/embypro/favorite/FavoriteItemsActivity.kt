package com.liujiaming.embypro

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import java.util.concurrent.ExecutorService

/**
 * Activity displaying the user's favorite media items.
 * Supports pagination and allows users to remove items from favorites.
 */
class FavoriteItemsActivity : AppCompatActivity() {
    private val networkExecutor: ExecutorService = AppExecutors.io
    private val mediaRepository by lazy { MediaRepository(this) }
    private val sessionStore by lazy { ServerSessionStore(this) }
    private val serverRepository by lazy { ServerRepository(this) }

    private lateinit var connection: ServerConnection

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyText: TextView
    private lateinit var topBar: View

    private val items = mutableListOf<PlaybackHistoryItemUiModel>()
    private lateinit var adapter: PlaybackHistoryAdapter

    private var isLoading = false
    private var totalCount = Int.MAX_VALUE
    private var startIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdgeHelper.enable(this, lightSystemBars = GlobalThemeStore(this).loadTheme().lightSystemBars)
        setContentView(R.layout.activity_favorite_items)
        supportActionBar?.hide()
        GlobalThemeManager.apply(this)

        connection = requireServerConnection(sessionStore, serverRepository) ?: return

        topBar = findViewById(R.id.favoriteItemsTopBar)
        recyclerView = findViewById(R.id.favoriteItemsRecyclerView)
        progressBar = findViewById(R.id.favoriteItemsProgressBar)
        emptyText = findViewById(R.id.favoriteItemsEmptyText)

        findViewById<ImageButton>(R.id.favoriteItemsBackButton).setDebouncedClickListener { finish() }

        adapter = PlaybackHistoryAdapter(
            items = items,
            accessToken = connection.accessToken,
            onItemClick = { item -> openVideoDirectly(item) },
            onItemLongClick = { },
            onFavoriteClick = { item -> removeFavorite(item) }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (dy <= 0 || isLoading || items.size >= totalCount) return
                val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                if (layoutManager.findLastVisibleItemPosition() >= items.size - 4) {
                    loadNextPage()
                }
            }
        })

        EdgeToEdgeHelper.applyInsets(topBar, applyTop = true)
        EdgeToEdgeHelper.applyInsets(recyclerView, applyBottom = true)
        loadFirstPage()
    }

    /**
     * Loads the first page of favorite items, clearing existing data.
     */
    private fun loadFirstPage() {
        items.clear()
        adapter.notifyDataSetChanged()
        startIndex = 0
        totalCount = Int.MAX_VALUE
        emptyText.visibility = View.GONE
        loadNextPage(showLoading = true)
    }

    /**
     * Loads the next page of favorite items with pagination support.
     * @param showLoading Whether to show loading indicator
     */
    private fun loadNextPage(showLoading: Boolean = false) {
        if (isLoading) return
        isLoading = true
        if (showLoading) {
            progressBar.visibility = View.VISIBLE
            recyclerView.visibility = View.INVISIBLE
        }

        networkExecutor.execute {
            val result = mediaRepository.fetchFavoriteItemsPage(connection, startIndex, PAGE_SIZE)
            runOnUiThread {
                isLoading = false
                progressBar.visibility = View.GONE
                result.onSuccess { page ->
                    totalCount = page.totalCount
                    val insertStart = items.size
                    items.addAll(page.items)
                    startIndex += PAGE_SIZE
                    recyclerView.visibility = View.VISIBLE
                    if (insertStart == 0) {
                        adapter.notifyDataSetChanged()
                    } else {
                        adapter.notifyItemRangeInserted(insertStart, page.items.size)
                    }
                    emptyText.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                    recyclerView.visibility = if (items.isEmpty()) View.INVISIBLE else View.VISIBLE
                }.onFailure { error ->
                    val message = userFriendlyErrorMessage(error, R.string.favorite_items_load_failed)
                    emptyText.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                    if (items.isEmpty()) {
                        emptyText.text = message
                    } else {
                        Toast.makeText(
                            this,
                            message,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    /**
     * Opens video player directly for a favorite item.
     * @param item The playback history item to play
     */
    private fun openVideoDirectly(item: PlaybackHistoryItemUiModel) {
        playVideoDirectly(
            connection = connection,
            mediaRepository = mediaRepository,
            itemId = item.itemId,
            queue = AppNavigator.buildHistoryVideoQueue(items, item.itemId)
        )
    }

    /**
     * Removes an item from favorites and updates the UI.
     * @param item The item to remove from favorites
     */
    private fun removeFavorite(item: PlaybackHistoryItemUiModel) {
        networkExecutor.execute {
            val result = mediaRepository.setFavoriteState(connection, item.itemId, false)
            runOnUiThread {
                result.onSuccess {
                    val index = items.indexOfFirst { it.itemId == item.itemId }
                    if (index >= 0) {
                        items.removeAt(index)
                        adapter.notifyItemRemoved(index)
                    }
                    emptyText.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                    recyclerView.visibility = if (items.isEmpty()) View.INVISIBLE else View.VISIBLE
                    Toast.makeText(this, getString(R.string.favorite_removed), Toast.LENGTH_SHORT).show()
                }.onFailure { error ->
                    Toast.makeText(
                        this,
                        userFriendlyErrorMessage(error, R.string.favorite_update_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    companion object {
        private const val PAGE_SIZE = 20
    }
}
