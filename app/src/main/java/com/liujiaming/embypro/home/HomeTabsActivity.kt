package com.liujiaming.embypro

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
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

/**
 * Main activity with tabbed navigation for home, media, music, and my sections.
 * Implements swipe gestures for category switching and manages home feed loading.
 */
class HomeTabsActivity : AppCompatActivity() {
    private val networkExecutor: ExecutorService = AppExecutors.io
    private val mediaRepository by lazy { MediaRepository(this) }
    private val homeFeedRepository by lazy { HomeFeedRepository(this) }
    private val preferenceStore by lazy { AppPreferenceStore(this) }
    private val serverRepository by lazy { ServerRepository(this) }
    private val sessionStore by lazy { ServerSessionStore(this) }
    private val homeSeenItemIds = linkedSetOf<String>()
    private val homeLibraryOffsets = linkedMapOf<String, Int>()
    private val homeLibraryTotals = linkedMapOf<String, Int>()
    private val homeLibraryOrder = mutableListOf<MediaLibraryUiModel>()

    private lateinit var homeContainer: View
    private lateinit var mediaContainer: View
    private lateinit var musicContainer: View
    private lateinit var myContainer: View
    private lateinit var homeTopBar: View
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

    private lateinit var connection: ServerConnection
    private lateinit var musicLibraryScreenController: MusicLibraryScreenController
    private var excludedHomeLibraryIds: Set<String> = emptySet()
    private var excludedHomeLibrarySignature = ""
    private var isHomeLoading = false
    private var pendingHomeReload = false
    private var currentTab = Tab.HOME
    private var isHomeLoadFailed = false
    private var currentPrimaryCategory = PrimaryCategory.VIDEO
    private lateinit var primaryCategoryGestureDetector: GestureDetector
    private var homeSearchExpandedHeight = 0
    private var homeSearchCurrentHeight = 0

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

        val activeServer = sessionStore.loadCurrentServer() ?: servers.first()
        sessionStore.activateServer(activeServer)
        connection = ServerConnection(
            baseUrl = serverRepository.buildBaseUrl(activeServer.address, activeServer.port),
            userId = activeServer.userId,
            accessToken = activeServer.accessToken
        )
        syncExcludedHomeLibraries()

        EdgeToEdgeHelper.enable(this, lightSystemBars = GlobalThemeStore(this).loadTheme().lightSystemBars)
        setContentView(R.layout.activity_home_tabs)
        supportActionBar?.hide()
        GlobalThemeManager.apply(this)

        homeContainer = findViewById(R.id.homeTabContainer)
        mediaContainer = findViewById(R.id.mediaTabContainer)
        musicContainer = findViewById(R.id.musicTabContainer)
        myContainer = findViewById(R.id.myTabContainer)
        homeTopBar = findViewById(R.id.homeTabsTopBar)
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
        musicLibraryScreenController = MusicLibraryScreenController(
            activity = this,
            root = musicContainer,
            connection = connection,
            embeddedWithBottomNav = true
        )

        homeSearchCard.setDebouncedClickListener {
            AppNavigator.openSearch(this, connection)
        }
        primaryCategoryGestureDetector = GestureDetector(
            this,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true

                override fun onFling(
                    e1: MotionEvent,
                    e2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float
                ): Boolean {
                    val deltaX = e2.x - e1.x
                    val deltaY = e2.y - e1.y
                    if (kotlin.math.abs(deltaX) < 60f || kotlin.math.abs(deltaX) <= kotlin.math.abs(deltaY)) {
                        return false
                    }
                    if (deltaX > 0f) {
                        updatePrimaryCategorySelection(PrimaryCategory.VIDEO)
                    } else {
                        updatePrimaryCategorySelection(PrimaryCategory.AUDIO)
                    }
                    return true
                }
            }
        )
        homePrimaryCategoryBar.setOnTouchListener { _, event ->
            primaryCategoryGestureDetector.onTouchEvent(event)
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
            AppNavigator.openPlaybackHistory(this, connection)
        }
        findViewById<View>(R.id.myFavoriteItemsEntry).setDebouncedClickListener {
            AppNavigator.openFavoriteItems(this, connection)
        }
        findViewById<View>(R.id.myPendingEntryOne).setDebouncedClickListener {
            Toast.makeText(this, getString(R.string.more_actions_pending), Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.myPendingEntryTwo).setDebouncedClickListener {
            Toast.makeText(this, getString(R.string.more_actions_pending), Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.mySettingsEntry).setDebouncedClickListener {
            AppNavigator.openSettings(this, connection)
        }
        findViewById<MaterialButton>(R.id.homeRetryButton).setDebouncedClickListener {
            connectAndLoadHome()
        }

        EdgeToEdgeHelper.applyInsets(topBar, applyTop = true)
        EdgeToEdgeHelper.applyInsets(mediaContainer, applyTop = true)
        EdgeToEdgeHelper.applyInsets(bottomNavigationCard, applyBottom = true)
        homeTopBar.post {
            if (homeSearchExpandedHeight == 0 && homeSearchCard.height > 0) {
                homeSearchExpandedHeight = homeSearchCard.height
                homeSearchCurrentHeight = homeSearchExpandedHeight
                applyHomeTopBarCollapseProgress(1f)
            }
        }

        homeFeedAdapter = MediaPosterAdapter(
            homeFeedItems,
            if (isTabletLayout()) R.layout.item_home_feature_card else R.layout.item_library_grid_card,
            connection.accessToken,
            onItemClick = { item ->
                openHomeFeedItem(item)
            },
            onItemLongClick = { item ->
                openHomeFeedItemDetail(item)
            }
        )
        homeFeedRecyclerView.layoutManager = GridLayoutManager(this, if (isTabletLayout()) 2 else 2)
        homeFeedRecyclerView.adapter = homeFeedAdapter
        homeFeedRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (currentTab == Tab.HOME) {
                    adjustHomeTopBarByScroll(dy)
                }
                if (dy <= 0 || isHomeLoading) return
                val layoutManager = recyclerView.layoutManager as? GridLayoutManager ?: return
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                if (lastVisible >= homeFeedItems.size - 6) {
                    loadMoreHomeFeedItems()
                }
            }
        })
        homeRefreshLayout.setOnRefreshListener { refreshHomeFeed() }
        mediaTabRecyclerView.layoutManager = GridLayoutManager(this, if (isTabletLayout()) 2 else 1)
        loadMediaTabLibraries()
        updatePrimaryCategorySelection(PrimaryCategory.VIDEO)

        navigationHomeItem.setDebouncedClickListener { showTab(Tab.HOME) }
        navigationMediaItem.setDebouncedClickListener { showTab(Tab.MEDIA) }
        navigationMusicItem.setDebouncedClickListener {
            showTab(Tab.MUSIC)
        }
        navigationMyItem.setDebouncedClickListener { showTab(Tab.MY) }
        showTab(Tab.HOME)

        connectAndLoadHome(preferPreloadedData = true)
    }

    override fun onStart() {
        super.onStart()
        musicLibraryScreenController.onStart()
    }

    override fun onStop() {
        musicLibraryScreenController.onStop()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        GlobalThemeManager.apply(this)
        if (syncExcludedHomeLibraries() && !isHomeLoading) {
            connectAndLoadHome()
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (currentTab == Tab.HOME) {
            primaryCategoryGestureDetector.onTouchEvent(ev)
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun connectAndLoadHome(preferPreloadedData: Boolean = false) {
        if (isHomeLoading) {
            pendingHomeReload = true
            return
        }
        isHomeLoading = true
        isHomeLoadFailed = false
        updateHomeLoadFailedVisibility()
        homeRefreshLayout.isRefreshing = true
        preloadCurrentCategoryData()
        val requestedCategoryKey = currentPrimaryCategory.key
        val preloadTask = if (preferPreloadedData) {
            HomeDataPreloader.takeTask(
                connection.baseUrl,
                connection.userId,
                connection.accessToken,
                excludedHomeLibraryIds,
                requestedCategoryKey
            )
        } else {
            null
        }
        networkExecutor.execute {
            val result = runCatching {
                val preloadedData = preloadTask?.get()
                    ?.getOrNull()
                    ?.takeIf {
                        it.matches(
                            connection.baseUrl,
                            connection.userId,
                            connection.accessToken,
                            excludedHomeLibrarySignature,
                            requestedCategoryKey
                        )
                    }

                preloadedData ?: loadFreshHomeData(requestedCategoryKey)
            }
            runOnUiThread {
                isHomeLoading = false
                homeRefreshLayout.isRefreshing = false
                result.onSuccess { homeData ->
                    if (homeData.primaryCategoryKey == currentPrimaryCategory.key) {
                        isHomeLoadFailed = false
                        updateHomeLoadFailedVisibility()
                        applyHomeData(homeData)
                    } else {
                        pendingHomeReload = true
                    }
                }.onFailure {
                    isHomeLoadFailed = true
                    updateHomeLoadFailedVisibility()
                }
                if (pendingHomeReload) {
                    pendingHomeReload = false
                    connectAndLoadHome(preferPreloadedData = true)
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
            val result = runCatching {
                homeFeedRepository.loadNextHomeFeedBatch(
                    connection = connection,
                    homeLibraryOrder = homeLibraryOrder,
                    homeSeenItemIds = homeSeenItemIds,
                    homeLibraryOffsets = homeLibraryOffsets,
                    homeLibraryTotals = homeLibraryTotals,
                    contentCategory = homeFeedRepository.contentCategoryFor(currentPrimaryCategory.key)
                )
            }
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
                            userFriendlyErrorMessage(error, R.string.server_home_load_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    private fun loadFreshHomeData(primaryCategoryKey: String = currentPrimaryCategory.key): PreloadedHomeData {
        return homeFeedRepository.buildHomeData(
            connection = connection,
            excludedLibraryIds = excludedHomeLibraryIds,
            primaryCategoryKey = primaryCategoryKey
        )
            .getOrThrow()
            .copy(
                excludedLibrarySignature = excludedHomeLibrarySignature,
                primaryCategoryKey = primaryCategoryKey
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

        if (homeData.primaryCategoryKey == PrimaryCategory.VIDEO.key) {
            bindMediaTabLibraries(homeData.mediaLibraries)
        }
    }

    private fun loadMediaTabLibraries() {
        networkExecutor.execute {
            val result = mediaRepository.fetchMediaLibraries(connection)
                .map { libraries ->
                    libraries.filterNot { it.collectionType.equals("music", ignoreCase = true) }
                }
            runOnUiThread {
                result.onSuccess { libraries ->
                    bindMediaTabLibraries(libraries)
                }
            }
        }
    }

    private fun bindMediaTabLibraries(libraries: List<MediaLibraryUiModel>) {
        mediaTabRecyclerView.adapter = MediaLibraryGridAdapter(
            libraries,
            connection.accessToken
        ) { library ->
            AppNavigator.openLibrary(this, connection, library.id, library.title)
        }
    }

    private fun openHomeFeedItem(item: MediaPosterUiModel) {
        if (currentPrimaryCategory == PrimaryCategory.AUDIO) {
            openHomeAudioItem(item)
            return
        }

        playVideoDirectly(
            connection = connection,
            mediaRepository = mediaRepository,
            itemId = item.id,
            queue = AppNavigator.buildPosterVideoQueue(homeFeedItems, item.id)
        )
    }

    private fun openHomeAudioItem(item: MediaPosterUiModel) {
        val queue = homeFeedItems.filter { it.itemType.equals("Audio", ignoreCase = true) }
        val currentIndex = queue.indexOfFirst { it.id == item.id }
        if (currentIndex < 0) return

        AppNavigator.openMusicPlayer(
            activity = this,
            connection = connection,
            libraryId = homeLibraryOrder.firstOrNull()?.id,
            queueTitle = getString(R.string.home_category_audio),
            queueIds = ArrayList(queue.map { it.id }),
            queueTitles = ArrayList(queue.map { it.title }),
            queueSubtitles = ArrayList(queue.map { it.subtitle }),
            queueImages = ArrayList(queue.map { it.imageUrl.orEmpty() }),
            queueIndex = currentIndex
        )
    }

    private fun openHomeFeedItemDetail(item: MediaPosterUiModel) {
        if (currentPrimaryCategory != PrimaryCategory.VIDEO) return
        AppNavigator.openPosterItemDetail(this, connection, item, homeFeedItems)
    }

    private fun updateHomeLoadFailedVisibility() {
        loadFailedContainer.visibility = if (currentTab == Tab.HOME && isHomeLoadFailed) View.VISIBLE else View.GONE
        loadFailedText.text = getString(R.string.home_load_failed_retry)
    }

    private fun syncExcludedHomeLibraries(): Boolean {
        val latest = preferenceStore.loadExcludedHomeLibraryIds(connection.baseUrl, connection.userId)
        val latestSignature = homeFeedRepository.buildExcludedSignature(latest)
        val changed = latestSignature != excludedHomeLibrarySignature
        excludedHomeLibraryIds = latest
        excludedHomeLibrarySignature = latestSignature
        return changed
    }

    private fun showTab(tab: Tab) {
        currentTab = tab
        homeTopBar.visibility = if (tab == Tab.HOME) View.VISIBLE else View.GONE
        homeContainer.visibility = if (tab == Tab.HOME) View.VISIBLE else View.GONE
        mediaContainer.visibility = if (tab == Tab.MEDIA) View.VISIBLE else View.GONE
        musicContainer.visibility = if (tab == Tab.MUSIC) View.VISIBLE else View.GONE
        myContainer.visibility = if (tab == Tab.MY) View.VISIBLE else View.GONE
        homeSearchCard.visibility = if (tab == Tab.HOME) View.VISIBLE else View.GONE
        homePrimaryCategoryBar.visibility = if (tab == Tab.HOME) View.VISIBLE else View.GONE
        updateNavigationSelection(tab)
        updateHomeLoadFailedVisibility()
        if (tab == Tab.HOME && !homeFeedRecyclerView.canScrollVertically(-1)) {
            expandHomeTopBar()
        }
    }

    private fun adjustHomeTopBarByScroll(dy: Int) {
        if (dy == 0 || homeTopBar.visibility != View.VISIBLE) return
        if (homeSearchExpandedHeight <= 0) {
            val measuredHeight = homeSearchCard.height
            if (measuredHeight <= 0) return
            homeSearchExpandedHeight = measuredHeight
            if (homeSearchCurrentHeight == 0) {
                homeSearchCurrentHeight = measuredHeight
            }
        }
        val targetHeight = (homeSearchCurrentHeight - dy).coerceIn(0, homeSearchExpandedHeight)
        if (targetHeight == homeSearchCurrentHeight) return
        homeSearchCurrentHeight = targetHeight
        val progress = targetHeight.toFloat() / homeSearchExpandedHeight.toFloat()
        applyHomeTopBarCollapseProgress(progress)
    }

    private fun applyHomeTopBarCollapseProgress(progress: Float) {
        val normalized = progress.coerceIn(0f, 1f)
        val layoutParams = homeSearchCard.layoutParams
        if (layoutParams.height != homeSearchCurrentHeight) {
            layoutParams.height = homeSearchCurrentHeight
            homeSearchCard.layoutParams = layoutParams
        }
        homeSearchCard.alpha = 0.4f + (normalized * 0.6f)
        homeSearchCard.translationY = -(1f - normalized) * dp(6)
        homePrimaryCategoryBar.alpha = 1f
        homePrimaryCategoryBar.translationY = 0f
    }

    private fun expandHomeTopBar() {
        if (homeSearchExpandedHeight <= 0) return
        homeSearchCurrentHeight = homeSearchExpandedHeight
        applyHomeTopBarCollapseProgress(1f)
    }

    private fun dp(value: Int): Float {
        return value * resources.displayMetrics.density
    }

    private fun updatePrimaryCategorySelection(category: PrimaryCategory) {
        val changed = currentPrimaryCategory != category
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
        if (changed && currentTab == Tab.HOME && !isHomeLoading) {
            connectAndLoadHome(preferPreloadedData = true)
        } else if (changed && currentTab == Tab.HOME) {
            pendingHomeReload = true
        }
    }

    private fun preloadCurrentCategoryData() {
        HomeDataPreloader.preload(
            context = this,
            baseUrl = connection.baseUrl,
            userId = connection.userId,
            accessToken = connection.accessToken,
            excludedLibraryIds = excludedHomeLibraryIds,
            primaryCategoryKey = currentPrimaryCategory.key
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
        applyNavigationState(navigationMusicIcon, navigationMusicText, tab == Tab.MUSIC, activeColor, inactiveColor)
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

    private fun isTabletLayout(): Boolean = resources.configuration.smallestScreenWidthDp >= 600

    private enum class Tab {
        HOME,
        MEDIA,
        MUSIC,
        MY
    }

    private enum class PrimaryCategory {
        VIDEO,
        AUDIO;

        val key: String
            get() = name.lowercase()
    }
}

private fun PreloadedHomeData.matches(
    baseUrl: String,
    userId: String,
    accessToken: String,
    excludedLibrarySignature: String,
    primaryCategoryKey: String
): Boolean {
    return this.baseUrl == baseUrl &&
        this.userId == userId &&
        this.accessToken == accessToken &&
        this.excludedLibrarySignature == excludedLibrarySignature &&
        this.primaryCategoryKey == primaryCategoryKey
}
