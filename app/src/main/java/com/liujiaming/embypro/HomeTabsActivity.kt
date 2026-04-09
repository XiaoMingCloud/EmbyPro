package com.liujiaming.embypro

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.navigation.NavigationBarView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class HomeTabsActivity : AppCompatActivity() {
    private val networkExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val embyApiService by lazy { EmbyApiService(this) }
    private val sessionStore by lazy { ServerSessionStore(this) }

    private lateinit var homeContainer: View
    private lateinit var mediaContainer: View
    private lateinit var myContainer: View
    private lateinit var homeSearchCard: View
    private lateinit var homeRefreshLayout: SwipeRefreshLayout
    private lateinit var homeFeedRecyclerView: RecyclerView
    private lateinit var mediaTabRecyclerView: RecyclerView
    private val homeFeedItems = mutableListOf<MediaPosterUiModel>()
    private lateinit var homeFeedAdapter: MediaPosterAdapter

    private lateinit var activeServer: ServerUiModel
    private lateinit var baseUrl: String
    private lateinit var userId: String
    private lateinit var accessToken: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val servers = sessionStore.loadServers()
        if (servers.isEmpty()) {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_AUTO_OPEN_CONNECT, true)
                    .putExtra(MainActivity.EXTRA_RETURN_HOME_ON_SUCCESS, true)
            )
            finish()
            return
        }

        activeServer = servers.first()
        baseUrl = embyApiService.buildBaseUrl(activeServer.address, activeServer.port)
        userId = activeServer.userId
        accessToken = activeServer.accessToken

        EdgeToEdgeHelper.enable(this, lightSystemBars = true)
        setContentView(R.layout.activity_home_tabs)
        supportActionBar?.hide()

        homeContainer = findViewById(R.id.homeTabContainer)
        mediaContainer = findViewById(R.id.mediaTabContainer)
        myContainer = findViewById(R.id.myTabContainer)
        homeSearchCard = findViewById(R.id.homeSearchCard)
        homeRefreshLayout = findViewById(R.id.homeTabContainer)
        homeFeedRecyclerView = findViewById(R.id.homeFeedRecyclerView)
        mediaTabRecyclerView = findViewById(R.id.mediaTabRecyclerView)
        val topBar = findViewById<View>(R.id.homeTabsTopBar)
        val bottomNavigation = findViewById<NavigationBarView>(R.id.homeTabsBottomNavigation)

        homeSearchCard.setOnClickListener {
            startActivity(
                Intent(this, SearchActivity::class.java)
                    .putExtra(SearchActivity.EXTRA_BASE_URL, baseUrl)
                    .putExtra(SearchActivity.EXTRA_USER_ID, userId)
                    .putExtra(SearchActivity.EXTRA_ACCESS_TOKEN, accessToken)
            )
        }
        findViewById<View>(R.id.myServerListEntry).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }

        EdgeToEdgeHelper.applyInsets(topBar, applyTop = true)
        EdgeToEdgeHelper.applyInsets(bottomNavigation, applyBottom = true)

        homeFeedAdapter = MediaPosterAdapter(
            homeFeedItems,
            R.layout.item_library_grid_card,
            accessToken,
            onItemClick = { item -> openVideoDirectly(item.id, homeFeedItems) }
        )
        homeFeedRecyclerView.layoutManager = GridLayoutManager(this, 2)
        homeFeedRecyclerView.adapter = homeFeedAdapter
        homeRefreshLayout.setOnRefreshListener { loadHomeFeed() }
        mediaTabRecyclerView.layoutManager = GridLayoutManager(this, 1)

        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> showTab(Tab.HOME)
                R.id.navigation_media -> showTab(Tab.MEDIA)
                R.id.navigation_my -> showTab(Tab.MY)
            }
            true
        }
        bottomNavigation.selectedItemId = R.id.navigation_home

        loadHomeFeed()
        loadMediaLibraries()
    }

    override fun onDestroy() {
        super.onDestroy()
        networkExecutor.shutdownNow()
    }

    private fun loadHomeFeed() {
        networkExecutor.execute {
            val result = runCatching {
                val libraries = embyApiService.fetchMediaLibraries(baseUrl, userId, accessToken).getOrThrow()
                libraries.flatMap { library ->
                    embyApiService.fetchLibraryItemsPage(
                        baseUrl = baseUrl,
                        userId = userId,
                        accessToken = accessToken,
                        parentId = library.id,
                        startIndex = 0,
                        limit = 8,
                        sortField = LibrarySortField.RANDOM,
                        sortDescending = true
                    ).items
                }.filter { !it.isFolder && it.itemType != "BoxSet" && it.itemType != "Folder" }
                    .shuffled()
            }
            runOnUiThread {
                homeRefreshLayout.isRefreshing = false
                result.onSuccess { homeData ->
                    homeFeedItems.clear()
                    homeFeedItems.addAll(homeData)
                    homeFeedAdapter.notifyDataSetChanged()
                }.onFailure { error ->
                    Toast.makeText(
                        this,
                        error.message ?: getString(R.string.server_home_load_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun loadMediaLibraries() {
        networkExecutor.execute {
            val result = embyApiService.fetchMediaLibraries(baseUrl, userId, accessToken)
            runOnUiThread {
                result.onSuccess { libraries ->
                    mediaTabRecyclerView.adapter = MediaLibraryGridAdapter(
                        libraries,
                        accessToken
                    ) { library ->
                        openLibrary(library)
                    }
                }.onFailure { error ->
                    Toast.makeText(
                        this,
                        error.message ?: getString(R.string.server_home_load_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun showTab(tab: Tab) {
        homeContainer.visibility = if (tab == Tab.HOME) View.VISIBLE else View.GONE
        mediaContainer.visibility = if (tab == Tab.MEDIA) View.VISIBLE else View.GONE
        myContainer.visibility = if (tab == Tab.MY) View.VISIBLE else View.GONE
        homeSearchCard.visibility = if (tab == Tab.HOME) View.VISIBLE else View.GONE
    }

    private fun openLibrary(library: MediaLibraryUiModel) {
        startActivity(
            Intent(this, LibraryItemsActivity::class.java)
                .putExtra(LibraryItemsActivity.EXTRA_LIBRARY_ID, library.id)
                .putExtra(LibraryItemsActivity.EXTRA_LIBRARY_NAME, library.title)
                .putExtra(LibraryItemsActivity.EXTRA_BASE_URL, baseUrl)
                .putExtra(LibraryItemsActivity.EXTRA_USER_ID, userId)
                .putExtra(LibraryItemsActivity.EXTRA_ACCESS_TOKEN, accessToken)
        )
    }

    private fun openVideoDirectly(itemId: String, items: List<MediaPosterUiModel>) {
        if (itemId.isBlank()) return
        val playableItems = items.filter { !it.isFolder && it.itemType != "BoxSet" && it.itemType != "Folder" }
        val playlistIds = ArrayList(playableItems.map { it.id })
        val playlistTitles = ArrayList(playableItems.map { it.title })
        val playlistIndex = playableItems.indexOfFirst { it.id == itemId }
        networkExecutor.execute {
            val result = embyApiService.fetchVideoDetail(baseUrl, userId, accessToken, itemId)
            runOnUiThread {
                result.onSuccess { detail ->
                    startActivity(
                        Intent(this, PlayerActivity::class.java)
                            .putExtra(PlayerActivity.EXTRA_PLAYBACK_URL, detail.playbackUrl)
                            .putExtra(PlayerActivity.EXTRA_ACCESS_TOKEN, accessToken)
                            .putExtra(PlayerActivity.EXTRA_TITLE, detail.title)
                            .putExtra(PlayerActivity.EXTRA_BASE_URL, baseUrl)
                            .putExtra(PlayerActivity.EXTRA_USER_ID, userId)
                            .putExtra(PlayerActivity.EXTRA_ITEM_ID, itemId)
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

    private enum class Tab {
        HOME,
        MEDIA,
        MY
    }
}
