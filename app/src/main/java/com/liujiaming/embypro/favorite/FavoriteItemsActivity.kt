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

class FavoriteItemsActivity : AppCompatActivity() {
    private val networkExecutor: ExecutorService = AppExecutors.io
    private val embyApiService by lazy { EmbyApiService(this) }

    private lateinit var baseUrl: String
    private lateinit var userId: String
    private lateinit var accessToken: String

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

        baseUrl = intent.getStringExtra(EXTRA_BASE_URL).orEmpty()
        userId = intent.getStringExtra(EXTRA_USER_ID).orEmpty()
        accessToken = intent.getStringExtra(EXTRA_ACCESS_TOKEN).orEmpty()

        if (baseUrl.isBlank() || userId.isBlank() || accessToken.isBlank()) {
            Toast.makeText(this, getString(R.string.server_data_missing), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        topBar = findViewById(R.id.favoriteItemsTopBar)
        recyclerView = findViewById(R.id.favoriteItemsRecyclerView)
        progressBar = findViewById(R.id.favoriteItemsProgressBar)
        emptyText = findViewById(R.id.favoriteItemsEmptyText)

        findViewById<ImageButton>(R.id.favoriteItemsBackButton).setDebouncedClickListener { finish() }

        adapter = PlaybackHistoryAdapter(
            items = items,
            accessToken = accessToken,
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

    private fun loadFirstPage() {
        items.clear()
        adapter.notifyDataSetChanged()
        startIndex = 0
        totalCount = Int.MAX_VALUE
        emptyText.visibility = View.GONE
        loadNextPage(showLoading = true)
    }

    private fun loadNextPage(showLoading: Boolean = false) {
        if (isLoading) return
        isLoading = true
        if (showLoading) {
            progressBar.visibility = View.VISIBLE
            recyclerView.visibility = View.INVISIBLE
        }

        networkExecutor.execute {
            val result = embyApiService.fetchFavoriteItemsPage(
                baseUrl = baseUrl,
                userId = userId,
                accessToken = accessToken,
                startIndex = startIndex,
                limit = PAGE_SIZE
            )
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
                    emptyText.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                    if (items.isEmpty()) {
                        emptyText.text = error.message ?: getString(R.string.favorite_items_load_failed)
                    } else {
                        Toast.makeText(
                            this,
                            error.message ?: getString(R.string.favorite_items_load_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    private fun openVideoDirectly(item: PlaybackHistoryItemUiModel) {
        val playlistIds = ArrayList(items.map { it.itemId })
        val playlistTitles = ArrayList(items.map { it.title })
        val playlistIndex = items.indexOfFirst { it.itemId == item.itemId }

        networkExecutor.execute {
            val result = embyApiService.fetchVideoDetail(baseUrl, userId, accessToken, item.itemId)
            runOnUiThread {
                result.onSuccess { detail ->
                    startActivity(
                        Intent(this, PlayerActivity::class.java)
                            .putExtra(PlayerActivity.EXTRA_PLAYBACK_URL, detail.playbackUrl)
                            .putExtra(PlayerActivity.EXTRA_ACCESS_TOKEN, accessToken)
                            .putExtra(PlayerActivity.EXTRA_TITLE, detail.title)
                            .putExtra(PlayerActivity.EXTRA_COVER_IMAGE_URL, detail.heroImageUrl)
                            .putExtra(PlayerActivity.EXTRA_BASE_URL, baseUrl)
                            .putExtra(PlayerActivity.EXTRA_USER_ID, userId)
                            .putExtra(PlayerActivity.EXTRA_ITEM_ID, item.itemId)
                            .putExtra(PlayerActivity.EXTRA_START_POSITION_MS, detail.playbackPositionTicks / 10_000L)
                            .putStringArrayListExtra(PlayerActivity.EXTRA_PLAYLIST_ITEM_IDS, playlistIds)
                            .putStringArrayListExtra(PlayerActivity.EXTRA_PLAYLIST_ITEM_TITLES, playlistTitles)
                            .putExtra(PlayerActivity.EXTRA_PLAYLIST_INDEX, playlistIndex)
                    )
                }.onFailure { error ->
                    Toast.makeText(
                        this,
                        error.message ?: getString(R.string.player_error),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun removeFavorite(item: PlaybackHistoryItemUiModel) {
        networkExecutor.execute {
            val result = embyApiService.setFavoriteState(
                baseUrl = baseUrl,
                userId = userId,
                accessToken = accessToken,
                itemId = item.itemId,
                favorite = false
            )
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
                        error.message ?: getString(R.string.favorite_update_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    companion object {
        const val EXTRA_BASE_URL = "extra_base_url"
        const val EXTRA_USER_ID = "extra_user_id"
        const val EXTRA_ACCESS_TOKEN = "extra_access_token"

        private const val PAGE_SIZE = 20
    }
}
