package com.liujiaming.embypro

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import java.util.concurrent.ExecutorService

class HomeTabsActivity : AppCompatActivity() {
    private val networkExecutor: ExecutorService = AppExecutors.io
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
    private lateinit var homePrimaryCategoryBar: View
    private lateinit var homeCategoryVideoTab: TextView
    private lateinit var homeCategoryAudioTab: TextView
    private lateinit var loadFailedContainer: View
    private lateinit var loadFailedText: TextView
    private lateinit var homeRefreshLayout: SwipeRefreshLayout
    private lateinit var homeFeedRecyclerView: RecyclerView
    private lateinit var mediaTabRecyclerView: RecyclerView
    private val homeFeedItems = mutableListOf<MediaPosterUiModel>()
    private lateinit var homeFeedAdapter: MediaPosterAdapter
    private lateinit var navigationHomeItem: View
    private lateinit var navigationMediaItem: View
    private lateinit var navigationMusicItem: View
    private lateinit var navigationMyItem: View
    private lateinit var navigationHomeIcon: ImageView
    private lateinit var navigationMediaIcon: ImageView
    private lateinit var navigationMusicIcon: ImageView
    private lateinit var navigationMyIcon: ImageView
    private lateinit var navigationHomeText: TextView
    private lateinit var navigationMediaText: TextView
    private lateinit var navigationMusicText: TextView
    private lateinit var navigationMyText: TextView

    private lateinit var activeServer: ServerUiModel
    private lateinit var baseUrl: String
    private lateinit var userId: String
    private lateinit var accessToken: String
    private var excludedHomeLibraryIds: Set<String> = emptySet()
    private var excludedHomeLibrarySignature = ""
    private var isHomeLoading = false
    private var currentTab = Tab.HOME
    private var isHomeLoadFailed = false
    private var currentPrimaryCategory = PrimaryCategory.VIDEO

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

        EdgeToEdgeHelper.enable(this, lightSystemBars = GlobalThemeStore(this).loadTheme().lightSystemBars)
        setContentView(R.layout.activity_home_tabs)
        supportActionBar?.hide()
        GlobalThemeManager.apply(this)

        homeContainer = findViewById(R.id.homeTabContainer)
        mediaContainer = findViewById(R.id.mediaTabContainer)
        myContainer = findViewById(R.id.myTabContainer)
        homeSearchCard = findViewById(R.id.homeSearchCard)
        homePrimaryCategoryBar = findViewById(R.id.homePrimaryCategoryBar)
        homeCategoryVideoTab = findViewById(R.id.homeCategoryVideoTab)
        homeCategoryAudioTab = findViewById(R.id.homeCategoryAudioTab)
        loadFailedContainer = findViewById(R.id.homeLoadFailedContainer)
        loadFailedText = findViewById(R.id.homeLoadFailedText)
        homeRefreshLayout = findViewById(R.id.homeTabContainer)
        homeFeedRecyclerView = findViewById(R.id.homeFeedRecyclerView)
        mediaTabRecyclerView = findViewById(R.id.mediaTabRecyclerView)
        val topBar = findViewById<View>(R.id.homeTabsTopBar)
        val bottomNavigationCard = findViewById<View>(R.id.homeTabsBottomNavigationCard)
        navigationHomeItem = findViewById(R.id.navigationHomeItem)
        navigationMediaItem = findViewById(R.id.navigationMediaItem)
        navigationMusicItem = findViewById(R.id.navigationMusicItem)
        navigationMyItem = findViewById(R.id.navigationMyItem)
        navigationHomeIcon = findViewById(R.id.navigationHomeIcon)
        navigationMediaIcon = findViewById(R.id.navigationMediaIcon)
        navigationMusicIcon = findViewById(R.id.navigationMusicIcon)
        navigationMyIcon = findViewById(R.id.navigationMyIcon)
        navigationHomeText = findViewById(R.id.navigationHomeText)
        navigationMediaText = findViewById(R.id.navigationMediaText)
        navigationMusicText = findViewById(R.id.navigationMusicText)
        navigationMyText = findViewById(R.id.navigationMyText)

        homeSearchCard.setDebouncedClickListener {
            startActivity(
                Intent(this, SearchActivity::class.java)
                    .putExtra(SearchActivity.EXTRA_BASE_URL, baseUrl)
                    .putExtra(SearchActivity.EXTRA_USER_ID, userId)
                    .putExtra(SearchActivity.EXTRA_ACCESS_TOKEN, accessToken)
            )
        }
        homeCategoryVideoTab.setDebouncedClickListener {
            updatePrimaryCategorySelection(PrimaryCategory.VIDEO)
        }
        homeCategoryAudioTab.setDebouncedClickListener {
            updatePrimaryCategorySelection(PrimaryCategory.AUDIO)
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
        findViewById<View>(R.id.myFavoriteItemsEntry).setDebouncedClickListener {
            startActivity(
                Intent(this, FavoriteItemsActivity::class.java)
                    .putExtra(FavoriteItemsActivity.EXTRA_BASE_URL, baseUrl)
                    .putExtra(FavoriteItemsActivity.EXTRA_USER_ID, userId)
                    .putExtra(FavoriteItemsActivity.EXTRA_ACCESS_TOKEN, accessToken)
            )
        }
        findViewById<View>(R.id.myPendingEntryOne).setDebouncedClickListener {
            Toast.makeText(this, getString(R.string.more_actions_pending), Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.myPendingEntryTwo).setDebouncedClickListener {
            Toast.makeText(this, getString(R.string.more_actions_pending), Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.mySettingsEntry).setDebouncedClickListener {
            startActivity(
                Intent(this, SettingsActivity::class.java)
                    .putExtra(SettingsActivity.EXTRA_BASE_URL, baseUrl)
                    .putExtra(SettingsActivity.EXTRA_USER_ID, userId)
                    .putExtra(SettingsActivity.EXTRA_ACCESS_TOKEN, accessToken)
            )
        }
        findViewById<MaterialButton>(R.id.homeRetryButton).setDebouncedClickListener {
            connectAndLoadHome()
        }

        EdgeToEdgeHelper.applyInsets(topBar, applyTop = true)
        EdgeToEdgeHelper.applyInsets(bottomNavigationCard, applyBottom = true)

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
        updatePrimaryCategorySelection(PrimaryCategory.VIDEO)

        navigationHomeItem.setDebouncedClickListener { showTab(Tab.HOME) }
        navigationMediaItem.setDebouncedClickListener { showTab(Tab.MEDIA) }
        navigationMusicItem.setDebouncedClickListener {
            startActivity(
                Intent(this, MusicLibraryActivity::class.java)
                    .putExtra(MusicLibraryActivity.EXTRA_BASE_URL, baseUrl)
                    .putExtra(MusicLibraryActivity.EXTRA_USER_ID, userId)
                    .putExtra(MusicLibraryActivity.EXTRA_ACCESS_TOKEN, accessToken)
            )
        }
        navigationMyItem.setDebouncedClickListener { showTab(Tab.MY) }
        showTab(Tab.HOME)

        connectAndLoadHome(preferPreloadedData = true)
    }

    override fun onResume() {
        super.onResume()
        GlobalThemeManager.apply(this)
        if (syncExcludedHomeLibraries() && !isHomeLoading) {
            connectAndLoadHome()
        }
    }

    private fun connectAndLoadHome(preferPreloadedData: Boolean = false) {
        if (isHomeLoading) return
        isHomeLoading = true
        isHomeLoadFailed = false
        updateHomeLoadFailedVisibility()
        homeRefreshLayout.isRefreshing = true
        val preloadTask = if (preferPreloadedData) {
            HomeDataPreloader.takeTask(baseUrl, userId, accessToken, excludedHomeLibraryIds)
        } else {
            null
        }
        networkExecutor.execute {
            val result = runCatching {
                val preloadedData = preloadTask?.get()
                    ?.getOrNull()
                    ?.takeIf { it.matches(baseUrl, userId, accessToken, excludedHomeLibrarySignature) }

                preloadedData ?: loadFreshHomeData()
            }
            runOnUiThread {
                isHomeLoading = false
                homeRefreshLayout.isRefreshing = false
                result.onSuccess { homeData ->
                    isHomeLoadFailed = false
                    updateHomeLoadFailedVisibility()
                    applyHomeData(homeData)
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

    private fun loadFreshHomeData(): PreloadedHomeData {
        embyApiService.fetchPublicServerInfo(baseUrl).getOrThrow()
        val allLibraries = embyApiService.fetchMediaLibraries(baseUrl, userId, accessToken).getOrThrow()
        val homeLibraries = allLibraries
            .filterNot { excludedHomeLibraryIds.contains(it.id) }
            .shuffled()
        resetHomeFeedState(homeLibraries)
        val homeFeed = fetchNextHomeFeedBatch()
        return PreloadedHomeData(
            baseUrl = baseUrl,
            userId = userId,
            accessToken = accessToken,
            excludedLibrarySignature = excludedHomeLibrarySignature,
            homeFeedItems = homeFeed,
            mediaLibraries = allLibraries,
            homeLibraryOrder = homeLibraryOrder.toList(),
            homeLibraryOffsets = homeLibraryOffsets.toMap(),
            homeLibraryTotals = homeLibraryTotals.toMap(),
            homeSeenItemIds = homeSeenItemIds.toSet()
        )
    }

    private fun applyHomeData(homeData: PreloadedHomeData) {
        homeSeenItemIds.clear()
        homeSeenItemIds.addAll(homeData.homeSeenItemIds)
        homeLibraryOrder.clear()
        homeLibraryOrder.addAll(homeData.homeLibraryOrder)
        homeLibraryOffsets.clear()
        homeLibraryOffsets.putAll(homeData.homeLibraryOffsets)
        homeLibraryTotals.clear()
        homeLibraryTotals.putAll(homeData.homeLibraryTotals)

        homeFeedItems.clear()
        homeFeedItems.addAll(homeData.homeFeedItems)
        homeFeedAdapter.notifyDataSetChanged()

        bindMediaLibraries(homeData.mediaLibraries)
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
        homePrimaryCategoryBar.visibility = if (tab == Tab.HOME) View.VISIBLE else View.GONE
        updateNavigationSelection(tab)
        updateHomeLoadFailedVisibility()
    }

    private fun updatePrimaryCategorySelection(category: PrimaryCategory) {
        currentPrimaryCategory = category
        applyPrimaryCategoryTabState(
            textView = homeCategoryVideoTab,
            selected = category == PrimaryCategory.VIDEO,
            selectedBackground = R.drawable.bg_home_primary_tab_video,
            selectedTextColor = getColor(R.color.home_primary_tab_video_text)
        )
        applyPrimaryCategoryTabState(
            textView = homeCategoryAudioTab,
            selected = category == PrimaryCategory.AUDIO,
            selectedBackground = R.drawable.bg_home_primary_tab_audio,
            selectedTextColor = getColor(R.color.home_primary_tab_audio_text)
        )
    }

    private fun applyPrimaryCategoryTabState(
        textView: TextView,
        selected: Boolean,
        selectedBackground: Int,
        selectedTextColor: Int
    ) {
        textView.isSelected = selected
        textView.setBackgroundResource(
            if (selected) {
                selectedBackground
            } else {
                R.drawable.bg_home_secondary_tab
            }
        )
        textView.setTextColor(
            if (selected) {
                selectedTextColor
            } else {
                getColor(R.color.home_primary_tab_unselected_text)
            }
        )
        textView.alpha = if (selected) 1f else 0.92f
    }

    private fun updateNavigationSelection(tab: Tab) {
        val activeColor = getColor(R.color.nav_active)
        val inactiveColor = getColor(R.color.nav_inactive)
        applyNavigationState(navigationHomeIcon, navigationHomeText, tab == Tab.HOME, activeColor, inactiveColor)
        applyNavigationState(navigationMediaIcon, navigationMediaText, tab == Tab.MEDIA, activeColor, inactiveColor)
        applyNavigationState(navigationMusicIcon, navigationMusicText, false, activeColor, inactiveColor)
        applyNavigationState(navigationMyIcon, navigationMyText, tab == Tab.MY, activeColor, inactiveColor)
    }

    private fun applyNavigationState(
        iconView: ImageView,
        textView: TextView,
        selected: Boolean,
        activeColor: Int,
        inactiveColor: Int
    ) {
        val targetColor = if (selected) activeColor else inactiveColor
        iconView.imageTintList = ColorStateList.valueOf(targetColor)
        textView.setTextColor(targetColor)
        textView.alpha = if (selected) 1f else 0.92f
        iconView.alpha = if (selected) 1f else 0.88f
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
                            .putExtra(PlayerActivity.EXTRA_COVER_IMAGE_URL, detail.heroImageUrl)
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

    private enum class PrimaryCategory {
        VIDEO,
        AUDIO
    }

    companion object {
        private const val HOME_PAGE_SIZE = 12
        private const val HOME_BATCH_SIZE = 18
    }
}

private fun PreloadedHomeData.matches(
    baseUrl: String,
    userId: String,
    accessToken: String,
    excludedLibrarySignature: String
): Boolean {
    return this.baseUrl == baseUrl &&
        this.userId == userId &&
        this.accessToken == accessToken &&
        this.excludedLibrarySignature == excludedLibrarySignature
}
