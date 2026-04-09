package com.liujiaming.embypro

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.navigation.NavigationBarView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class HomeTabsActivity : AppCompatActivity() {
    private val networkExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val embyApiService by lazy { EmbyApiService(this) }
    private val homeLibraryFilterStore by lazy { HomeLibraryFilterStore(this) }
    private val sessionStore by lazy { ServerSessionStore(this) }
    private val homeSeenItemIds = linkedSetOf<String>()
    private val homeLibraryOffsets = linkedMapOf<String, Int>()
    private val homeLibraryTotals = linkedMapOf<String, Int>()
    private val homeLibraryOrder = mutableListOf<MediaLibraryUiModel>()

    private lateinit var homeContainer: View
    private lateinit var mediaContainer: View
    private lateinit var myContainer: View
    private lateinit var homeSearchCard: View
    private lateinit var loadFailedContainer: View
    private lateinit var loadFailedText: TextView
    private lateinit var homeRefreshLayout: SwipeRefreshLayout
    private lateinit var homeFeedRecyclerView: RecyclerView
    private lateinit var mediaTabRecyclerView: RecyclerView
    private val homeFeedItems = mutableListOf<MediaPosterUiModel>()
    private lateinit var homeFeedAdapter: MediaPosterAdapter

    private lateinit var activeServer: ServerUiModel
    private lateinit var baseUrl: String
    private lateinit var userId: String
    private lateinit var accessToken: String
    private var excludedHomeLibraryIds: Set<String> = emptySet()
    private var excludedHomeLibrarySignature = ""
    private var isHomeLoading = false
    private var currentTab = Tab.HOME
    private var isHomeLoadFailed = false

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
        syncExcludedHomeLibraries()

        EdgeToEdgeHelper.enable(this, lightSystemBars = true)
        setContentView(R.layout.activity_home_tabs)
        supportActionBar?.hide()

        homeContainer = findViewById(R.id.homeTabContainer)
        mediaContainer = findViewById(R.id.mediaTabContainer)
        myContainer = findViewById(R.id.myTabContainer)
        homeSearchCard = findViewById(R.id.homeSearchCard)
        loadFailedContainer = findViewById(R.id.homeLoadFailedContainer)
        loadFailedText = findViewById(R.id.homeLoadFailedText)
        homeRefreshLayout = findViewById(R.id.homeTabContainer)
        homeFeedRecyclerView = findViewById(R.id.homeFeedRecyclerView)
        mediaTabRecyclerView = findViewById(R.id.mediaTabRecyclerView)
        val topBar = findViewById<View>(R.id.homeTabsTopBar)
        val bottomNavigation = findViewById<NavigationBarView>(R.id.homeTabsBottomNavigation)

        homeSearchCard.setDebouncedClickListener {
            startActivity(
                Intent(this, SearchActivity::class.java)
                    .putExtra(SearchActivity.EXTRA_BASE_URL, baseUrl)
                    .putExtra(SearchActivity.EXTRA_USER_ID, userId)
                    .putExtra(SearchActivity.EXTRA_ACCESS_TOKEN, accessToken)
            )
        }
        findViewById<View>(R.id.myServerListEntry).setDebouncedClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
        findViewById<View>(R.id.myPlaybackHistoryEntry).setDebouncedClickListener {
            startActivity(
                Intent(this, PlaybackHistoryActivity::class.java)
                    .putExtra(PlaybackHistoryActivity.EXTRA_BASE_URL, baseUrl)
                    .putExtra(PlaybackHistoryActivity.EXTRA_USER_ID, userId)
                    .putExtra(PlaybackHistoryActivity.EXTRA_ACCESS_TOKEN, accessToken)
            )
        }
        findViewById<View>(R.id.myHomeSettingsEntry).setDebouncedClickListener {
            startActivity(
                Intent(this, HomeSettingsActivity::class.java)
                    .putExtra(HomeSettingsActivity.EXTRA_BASE_URL, baseUrl)
                    .putExtra(HomeSettingsActivity.EXTRA_USER_ID, userId)
                    .putExtra(HomeSettingsActivity.EXTRA_ACCESS_TOKEN, accessToken)
            )
        }
        findViewById<MaterialButton>(R.id.homeRetryButton).setDebouncedClickListener {
            connectAndLoadHome()
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
        homeFeedRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (dy <= 0 || isHomeLoading) return
                val layoutManager = recyclerView.layoutManager as? GridLayoutManager ?: return
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                if (lastVisible >= homeFeedItems.size - 6) {
                    loadMoreHomeFeedItems()
                }
            }
        })
        homeRefreshLayout.setOnRefreshListener { refreshHomeFeed() }
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

        connectAndLoadHome()
        loadMediaLibraries()
    }

    override fun onResume() {
        super.onResume()
        if (syncExcludedHomeLibraries() && !isHomeLoading) {
            connectAndLoadHome()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        networkExecutor.shutdownNow()
    }

    private fun connectAndLoadHome() {
        if (isHomeLoading) return
        isHomeLoading = true
        isHomeLoadFailed = false
        updateHomeLoadFailedVisibility()
        homeRefreshLayout.isRefreshing = true
        networkExecutor.execute {
            val result = runCatching {
                embyApiService.fetchPublicServerInfo(baseUrl).getOrThrow()
                val libraries = embyApiService.fetchMediaLibraries(baseUrl, userId, accessToken)
                    .getOrThrow()
                    .filterNot { excludedHomeLibraryIds.contains(it.id) }
                    .shuffled()
                resetHomeFeedState(libraries)
                fetchNextHomeFeedBatch()
            }
            runOnUiThread {
                isHomeLoading = false
                homeRefreshLayout.isRefreshing = false
                result.onSuccess { homeData ->
                    isHomeLoadFailed = false
                    updateHomeLoadFailedVisibility()
                    homeFeedItems.clear()
                    homeFeedItems.addAll(homeData)
                    homeFeedAdapter.notifyDataSetChanged()
                }.onFailure {
                    isHomeLoadFailed = true
                    updateHomeLoadFailedVisibility()
                }
            }
        }
    }

    private fun refreshHomeFeed() {
        connectAndLoadHome()
    }

    private fun loadMoreHomeFeedItems() {
        if (isHomeLoading || homeLibraryOrder.isEmpty()) return
        isHomeLoading = true
        networkExecutor.execute {
            val result = runCatching { fetchNextHomeFeedBatch() }
            runOnUiThread {
                isHomeLoading = false
                result.onSuccess { newItems ->
                    if (newItems.isNotEmpty()) {
                        val start = homeFeedItems.size
                        homeFeedItems.addAll(newItems)
                        homeFeedAdapter.notifyItemRangeInserted(start, newItems.size)
                    }
                }.onFailure { error ->
                    if (homeFeedItems.isEmpty()) {
                        isHomeLoadFailed = true
                        updateHomeLoadFailedVisibility()
                    } else {
                        Toast.makeText(
                            this,
                            error.message ?: getString(R.string.server_home_load_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    private fun resetHomeFeedState(libraries: List<MediaLibraryUiModel>) {
        homeSeenItemIds.clear()
        homeLibraryOrder.clear()
        homeLibraryOrder.addAll(libraries)
        homeLibraryOffsets.clear()
        homeLibraryTotals.clear()
        libraries.forEach { library ->
            homeLibraryOffsets[library.id] = 0
            homeLibraryTotals[library.id] = Int.MAX_VALUE
        }
    }

    private fun fetchNextHomeFeedBatch(): List<MediaPosterUiModel> {
        if (homeLibraryOrder.isEmpty()) return emptyList()
        val freshItems = mutableListOf<MediaPosterUiModel>()
        val libraries = homeLibraryOrder.shuffled()
        val maxAttempts = (libraries.size * 3).coerceAtLeast(3)
        var attempts = 0

        while (freshItems.size < HOME_BATCH_SIZE && attempts < maxAttempts) {
            val library = libraries[attempts % libraries.size]
            val offset = homeLibraryOffsets[library.id] ?: 0
            val total = homeLibraryTotals[library.id] ?: Int.MAX_VALUE
            if (offset >= total) {
                attempts++
                continue
            }

            val page = embyApiService.fetchLibraryItemsPage(
                baseUrl = baseUrl,
                userId = userId,
                accessToken = accessToken,
                parentId = library.id,
                startIndex = offset,
                limit = HOME_PAGE_SIZE,
                sortField = LibrarySortField.RANDOM,
                sortDescending = true
            )
            homeLibraryOffsets[library.id] = offset + HOME_PAGE_SIZE
            homeLibraryTotals[library.id] = page.totalCount

            page.items
                .asSequence()
                .filter { !it.isFolder && it.itemType != "BoxSet" && it.itemType != "Folder" }
                .filter { it.id.isNotBlank() }
                .filter { homeSeenItemIds.add(it.id) }
                .take(HOME_BATCH_SIZE - freshItems.size)
                .forEach { freshItems.add(it) }

            attempts++
            if (homeLibraryTotals.values.all { knownTotal ->
                    knownTotal != Int.MAX_VALUE
                } && homeLibraryOffsets.all { (libraryId, currentOffset) ->
                    currentOffset >= (homeLibraryTotals[libraryId] ?: Int.MAX_VALUE)
                }
            ) {
                break
            }
        }
        return freshItems.shuffled()
    }

    private fun bindMediaLibraries(libraries: List<MediaLibraryUiModel>) {
        mediaTabRecyclerView.adapter = MediaLibraryGridAdapter(
            libraries,
            accessToken
        ) { library ->
            openLibrary(library)
        }
    }

    private fun loadMediaLibraries() {
        networkExecutor.execute {
            val result = embyApiService.fetchMediaLibraries(baseUrl, userId, accessToken)
            runOnUiThread {
                result.onSuccess { libraries ->
                    bindMediaLibraries(libraries)
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

    private fun updateHomeLoadFailedVisibility() {
        loadFailedContainer.visibility = if (currentTab == Tab.HOME && isHomeLoadFailed) View.VISIBLE else View.GONE
        loadFailedText.text = getString(R.string.home_load_failed_retry)
    }

    private fun syncExcludedHomeLibraries(): Boolean {
        val latest = homeLibraryFilterStore.loadExcludedLibraryIds(baseUrl, userId)
        val latestSignature = latest.toList().sorted().joinToString("|")
        val changed = latestSignature != excludedHomeLibrarySignature
        excludedHomeLibraryIds = latest
        excludedHomeLibrarySignature = latestSignature
        return changed
    }

    private fun showTab(tab: Tab) {
        currentTab = tab
        homeContainer.visibility = if (tab == Tab.HOME) View.VISIBLE else View.GONE
        mediaContainer.visibility = if (tab == Tab.MEDIA) View.VISIBLE else View.GONE
        myContainer.visibility = if (tab == Tab.MY) View.VISIBLE else View.GONE
        homeSearchCard.visibility = if (tab == Tab.HOME) View.VISIBLE else View.GONE
        updateHomeLoadFailedVisibility()
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

    companion object {
        private const val HOME_PAGE_SIZE = 12
        private const val HOME_BATCH_SIZE = 18
    }
}
