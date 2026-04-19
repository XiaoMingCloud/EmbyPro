package com.liujiaming.embypro

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.navigation.NavigationBarView
import java.util.concurrent.ExecutorService

data class MediaPosterUiModel(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val style: ServerIconStyle = ServerIconStyle.INDIGO,
    val imageUrl: String? = null,
    val isFolder: Boolean = false,
    val itemType: String = ""
)

data class MediaLibraryUiModel(
    val id: String,
    val title: String,
    val style: ServerIconStyle,
    val imageUrl: String? = null,
    val totalCount: Int = 0,
    val collectionType: String = ""
)

class ServerHomeActivity : AppCompatActivity() {
    private val networkExecutor: ExecutorService = AppExecutors.io
    private val embyApiService by lazy { EmbyApiService(this) }

    private lateinit var continueWatchingRecyclerView: RecyclerView
    private lateinit var mediaLibraryRecyclerView: RecyclerView
    private lateinit var librarySectionsContainer: LinearLayout

    private lateinit var baseUrl: String
    private lateinit var userId: String
    private lateinit var accessToken: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdgeHelper.enable(this, lightSystemBars = GlobalThemeStore(this).loadTheme().lightSystemBars)
        setContentView(R.layout.activity_server_home)
        GlobalThemeManager.apply(this)

        supportActionBar?.hide()

        val serverName = intent.getStringExtra(EXTRA_SERVER_NAME).orEmpty().ifBlank {
            getString(R.string.server_default_name)
        }
        baseUrl = intent.getStringExtra(EXTRA_BASE_URL).orEmpty()
        userId = intent.getStringExtra(EXTRA_USER_ID).orEmpty()
        accessToken = intent.getStringExtra(EXTRA_ACCESS_TOKEN).orEmpty()

        continueWatchingRecyclerView = findViewById(R.id.continueWatchingRecyclerView)
        mediaLibraryRecyclerView = findViewById(R.id.mediaLibraryRecyclerView)
        librarySectionsContainer = findViewById(R.id.librarySectionsContainer)
        val topBar = findViewById<ImageButton>(R.id.serverBackButton).parent as View
        val bottomNavigation = findViewById<NavigationBarView>(R.id.serverBottomNavigation)

        findViewById<TextView>(R.id.serverHomeTitle).text = serverName
        findViewById<ImageButton>(R.id.serverBackButton).setDebouncedClickListener { finish() }
        findViewById<ImageButton>(R.id.serverSettingsButton).setDebouncedClickListener {
            Toast.makeText(this, getString(R.string.server_settings_pending), Toast.LENGTH_SHORT).show()
        }
        findViewById<ImageButton>(R.id.serverCastButton).setDebouncedClickListener {
            Toast.makeText(this, getString(R.string.server_cast_pending), Toast.LENGTH_SHORT).show()
        }

        EdgeToEdgeHelper.applyInsets(topBar, applyTop = true)
        EdgeToEdgeHelper.applyInsets(bottomNavigation, applyBottom = true)

        bindHorizontalList(
            recyclerView = continueWatchingRecyclerView,
            adapter = MediaPosterAdapter(emptyList(), R.layout.item_continue_watching_card, accessToken)
        )
        bindHorizontalList(
            recyclerView = mediaLibraryRecyclerView,
            adapter = LibraryStripAdapter(emptyList(), accessToken) { }
        )

        bottomNavigation.setOnItemSelectedListener { item ->
            Toast.makeText(this, item.title, Toast.LENGTH_SHORT).show()
            true
        }

        if (baseUrl.isBlank() || userId.isBlank() || accessToken.isBlank()) {
            Toast.makeText(this, getString(R.string.server_data_missing), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        loadServerHome()
    }

    private fun loadServerHome() {
        networkExecutor.execute {
            val result = embyApiService.fetchServerHomeData(baseUrl, userId, accessToken)

            runOnUiThread {
                result.onSuccess { homeData ->
                    bindHorizontalList(
                        recyclerView = continueWatchingRecyclerView,
                        adapter = MediaPosterAdapter(
                            homeData.continueWatching,
                            R.layout.item_continue_watching_card,
                            accessToken,
                            onItemClick = { item ->
                                openVideoDetail(item.id, homeData.continueWatching)
                            }
                        )
                    )

                    bindHorizontalList(
                        recyclerView = mediaLibraryRecyclerView,
                        adapter = LibraryStripAdapter(
                            items = homeData.mediaLibraries,
                            accessToken = accessToken,
                            onLibraryClick = { openLibrary(it) }
                        )
                    )

                    bindLibrarySections(homeData.mediaLibraries, homeData.librarySections)
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

    private fun bindLibrarySections(
        libraries: List<MediaLibraryUiModel>,
        sectionItems: Map<String, List<MediaPosterUiModel>>
    ) {
        librarySectionsContainer.removeAllViews()
        val inflater = LayoutInflater.from(this)

        libraries.forEach { library ->
            val sectionView = inflater.inflate(R.layout.item_library_section, librarySectionsContainer, false)
            val titleText = sectionView.findViewById<TextView>(R.id.librarySectionTitle)
            val recyclerView = sectionView.findViewById<RecyclerView>(R.id.librarySectionRecyclerView)

            titleText.text = library.title
            titleText.setDebouncedClickListener { openLibrary(library) }

            bindHorizontalList(
                recyclerView = recyclerView,
                adapter = MediaPosterAdapter(
                    items = sectionItems[library.id].orEmpty(),
                    cardLayout = R.layout.item_library_media_card,
                    accessToken = accessToken,
                    onItemClick = { item ->
                        openVideoDetail(item.id, sectionItems[library.id].orEmpty())
                    }
                )
            )

            librarySectionsContainer.addView(sectionView)
        }
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

    private fun openVideoDetail(itemId: String, items: List<MediaPosterUiModel> = emptyList()) {
        if (itemId.isBlank()) return
        val playableItems = items.filter { !it.isFolder && it.itemType != "BoxSet" && it.itemType != "Folder" }
        val playlistIds = ArrayList(playableItems.map { it.id })
        val playlistTitles = ArrayList(playableItems.map { it.title })
        val playlistIndex = playableItems.indexOfFirst { it.id == itemId }
        startActivity(
            Intent(this, VideoDetailActivity::class.java)
                .putExtra(VideoDetailActivity.EXTRA_ITEM_ID, itemId)
                .putExtra(VideoDetailActivity.EXTRA_BASE_URL, baseUrl)
                .putExtra(VideoDetailActivity.EXTRA_USER_ID, userId)
                .putExtra(VideoDetailActivity.EXTRA_ACCESS_TOKEN, accessToken)
                .putStringArrayListExtra(VideoDetailActivity.EXTRA_PLAYLIST_ITEM_IDS, playlistIds)
                .putStringArrayListExtra(VideoDetailActivity.EXTRA_PLAYLIST_ITEM_TITLES, playlistTitles)
                .putExtra(VideoDetailActivity.EXTRA_PLAYLIST_INDEX, playlistIndex)
        )
    }

    private fun bindHorizontalList(
        recyclerView: RecyclerView,
        adapter: RecyclerView.Adapter<*>
    ) {
        recyclerView.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        recyclerView.adapter = adapter
    }

    companion object {
        const val EXTRA_SERVER_NAME = "extra_server_name"
        const val EXTRA_BASE_URL = "extra_base_url"
        const val EXTRA_USER_ID = "extra_user_id"
        const val EXTRA_ACCESS_TOKEN = "extra_access_token"
    }
}

