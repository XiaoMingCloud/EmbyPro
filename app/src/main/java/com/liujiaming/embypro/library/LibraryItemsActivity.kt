package com.liujiaming.embypro

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import java.util.concurrent.ExecutorService

/**
 * Activity displaying items in a media library with browsing, filtering, and sorting capabilities.
 * Supports multiple browse modes (all, continue watching, favorites, genres, tags, etc.)
 * and implements pagination for loading items.
 */
class LibraryItemsActivity : AppCompatActivity() {
    private val networkExecutor: ExecutorService = AppExecutors.io
    private val mediaRepository by lazy { MediaRepository(this) }
    private val sessionStore by lazy { ServerSessionStore(this) }
    private val serverRepository by lazy { ServerRepository(this) }
    private val loadedItems = mutableListOf<MediaPosterUiModel>()

    private lateinit var connection: ServerConnection
    private lateinit var libraryId: String

    private lateinit var recyclerView: RecyclerView
    private lateinit var countText: TextView
    private lateinit var filterValueText: TextView
    private lateinit var filterButton: ImageButton
    private lateinit var sortSelector: LinearLayout
    private lateinit var sortText: TextView
    private lateinit var adapter: MediaPosterAdapter

    private var startIndex = 0
    private var totalCount = Int.MAX_VALUE
    private var isLoading = false
    private val pageSize = 30
    private var currentMode = LibraryBrowseMode.ALL
    private var selectedGenre: String? = null
    private var selectedTag: String? = null
    private var filterOptions: LibraryFilterOptionsUiModel? = null
    private var currentSortField = LibrarySortField.TITLE
    private var sortDescending = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdgeHelper.enable(this, lightSystemBars = GlobalThemeStore(this).loadTheme().lightSystemBars)
        setContentView(R.layout.activity_library_items)
        GlobalThemeManager.apply(this)

        supportActionBar?.hide()

        val libraryName = intent.getStringExtra(EXTRA_LIBRARY_NAME).orEmpty().ifBlank {
            getString(R.string.media_library)
        }
        connection = requireServerConnection(sessionStore, serverRepository) ?: return
        libraryId = intent.getStringExtra(EXTRA_LIBRARY_ID).orEmpty()

        if (libraryId.isBlank()) {
            Toast.makeText(this, getString(R.string.server_data_missing), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        findViewById<ImageButton>(R.id.libraryBackButton).setDebouncedClickListener { finish() }
        findViewById<TextView>(R.id.libraryTitleText).text = libraryName
        countText = findViewById(R.id.libraryCountText)
        filterValueText = findViewById(R.id.libraryFilterValueText)
        filterButton = findViewById(R.id.libraryFilterButton)
        sortSelector = findViewById(R.id.librarySortSelector)
        sortText = findViewById(R.id.librarySortText)
        recyclerView = findViewById(R.id.libraryItemsRecyclerView)
        val topBar = findViewById<ImageButton>(R.id.libraryBackButton).parent as View

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
                if (dy <= 0) return
                val layoutManager = recyclerView.layoutManager as? GridLayoutManager ?: return
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                if (!isLoading && lastVisible >= loadedItems.size - 6 && loadedItems.size < totalCount) {
                    loadMoreItems()
                }
            }
        })

        EdgeToEdgeHelper.applyInsets(topBar, applyTop = true)
        EdgeToEdgeHelper.applyInsets(recyclerView, applyBottom = true)

        bindTab(findViewById(R.id.tabAll), LibraryBrowseMode.ALL, true)
        bindTab(findViewById(R.id.tabContinueWatching), LibraryBrowseMode.CONTINUE)
        bindTab(findViewById(R.id.tabFavorites), LibraryBrowseMode.FAVORITES)
        bindTab(findViewById(R.id.tabGenres), LibraryBrowseMode.GENRES)
        bindTab(findViewById(R.id.tabTags), LibraryBrowseMode.TAGS)
        bindTab(findViewById(R.id.tabCollections), LibraryBrowseMode.COLLECTIONS)
        bindTab(findViewById(R.id.tabFolders), LibraryBrowseMode.FOLDERS)

        sortSelector.setDebouncedClickListener { showSortMenu() }

        filterButton.setDebouncedClickListener {
            when (currentMode) {
                LibraryBrowseMode.GENRES -> ensureFilterOptions { showValuePicker(it.genres, true) }
                LibraryBrowseMode.TAGS -> ensureFilterOptions { showValuePicker(it.tags, false) }
                else -> Toast.makeText(this, getString(R.string.filter_not_available), Toast.LENGTH_SHORT).show()
            }
        }

        updateFilterUi()
        updateSortUi()
        resetAndLoad()
    }

    private fun loadMoreItems() {
        isLoading = true
        networkExecutor.execute {
            val result = runCatching {
                mediaRepository.fetchLibraryItemsPage(
                    connection = connection,
                    parentId = libraryId,
                    startIndex = startIndex,
                    limit = pageSize,
                    mode = currentMode,
                    filterValue = currentFilterValue(),
                    sortField = currentSortField,
                    sortDescending = sortDescending
                ).getOrThrow()
            }

            runOnUiThread {
                isLoading = false
                result.onSuccess { page ->
                    val previousSize = loadedItems.size
                    loadedItems.addAll(page.items)
                    totalCount = page.totalCount
                    startIndex = loadedItems.size
                    adapter.notifyItemRangeInserted(previousSize, page.items.size)
                    countText.text = getString(R.string.library_total_count, totalCount)
                }.onFailure { error ->
                    Toast.makeText(
                        this,
                        userFriendlyErrorMessage(error, R.string.library_load_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun bindTab(chip: Chip, mode: LibraryBrowseMode, checked: Boolean = false) {
        chip.isChecked = checked
        chip.setDebouncedClickListener {
            switchMode(mode)
        }
    }

    private fun switchMode(mode: LibraryBrowseMode) {
        currentMode = mode
        when (mode) {
            LibraryBrowseMode.ALL,
            LibraryBrowseMode.CONTINUE,
            LibraryBrowseMode.FAVORITES,
            LibraryBrowseMode.COLLECTIONS,
            LibraryBrowseMode.FOLDERS -> {
                updateFilterUi()
                resetAndLoad()
            }

            LibraryBrowseMode.GENRES -> {
                ensureFilterOptions { options ->
                    if (options.genres.isEmpty()) {
                        Toast.makeText(this, getString(R.string.no_genres_found), Toast.LENGTH_SHORT).show()
                        currentMode = LibraryBrowseMode.ALL
                        findViewById<Chip>(R.id.tabAll).isChecked = true
                        updateFilterUi()
                        resetAndLoad()
                    } else {
                        if (selectedGenre.isNullOrBlank() || !options.genres.contains(selectedGenre)) {
                            selectedGenre = options.genres.first()
                        }
                        updateFilterUi()
                        showValuePicker(options.genres, true)
                    }
                }
            }

            LibraryBrowseMode.TAGS -> {
                ensureFilterOptions { options ->
                    if (options.tags.isEmpty()) {
                        Toast.makeText(this, getString(R.string.no_tags_found), Toast.LENGTH_SHORT).show()
                        currentMode = LibraryBrowseMode.ALL
                        findViewById<Chip>(R.id.tabAll).isChecked = true
                        updateFilterUi()
                        resetAndLoad()
                    } else {
                        if (selectedTag.isNullOrBlank() || !options.tags.contains(selectedTag)) {
                            selectedTag = options.tags.first()
                        }
                        updateFilterUi()
                        showValuePicker(options.tags, false)
                    }
                }
            }
        }
    }

    private fun ensureFilterOptions(onReady: (LibraryFilterOptionsUiModel) -> Unit) {
        filterOptions?.let {
            onReady(it)
            return
        }

        networkExecutor.execute {
            val result = mediaRepository.fetchLibraryFilterOptions(connection, libraryId)
            runOnUiThread {
                result.onSuccess {
                    filterOptions = it
                    onReady(it)
                }.onFailure { error ->
                    Toast.makeText(
                        this,
                        userFriendlyErrorMessage(error, R.string.library_filter_load_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun showValuePicker(values: List<String>, isGenre: Boolean) {
        val checkedItem = values.indexOf(if (isGenre) selectedGenre else selectedTag).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(if (isGenre) R.string.tab_genres else R.string.tab_tags)
            .setSingleChoiceItems(values.toTypedArray(), checkedItem) { dialog, which ->
                if (isGenre) {
                    selectedGenre = values[which]
                } else {
                    selectedTag = values[which]
                }
                dialog.dismiss()
                updateFilterUi()
                resetAndLoad()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun resetAndLoad() {
        startIndex = 0
        totalCount = Int.MAX_VALUE
        loadedItems.clear()
        adapter.notifyDataSetChanged()
        countText.text = getString(R.string.library_total_count, 0)
        loadMoreItems()
    }

    private fun currentFilterValue(): String? {
        return when (currentMode) {
            LibraryBrowseMode.GENRES -> selectedGenre
            LibraryBrowseMode.TAGS -> selectedTag
            else -> null
        }
    }

    private fun updateFilterUi() {
        val value = when (currentMode) {
            LibraryBrowseMode.GENRES -> selectedGenre.orEmpty()
            LibraryBrowseMode.TAGS -> selectedTag.orEmpty()
            LibraryBrowseMode.CONTINUE -> getString(R.string.tab_continue)
            LibraryBrowseMode.FAVORITES -> getString(R.string.tab_favorites)
            LibraryBrowseMode.COLLECTIONS -> getString(R.string.tab_collections)
            LibraryBrowseMode.FOLDERS -> getString(R.string.tab_folders)
            else -> ""
        }
        filterValueText.text = value
        filterValueText.visibility = if (value.isBlank()) View.GONE else View.VISIBLE
        filterButton.visibility = if (currentMode == LibraryBrowseMode.GENRES || currentMode == LibraryBrowseMode.TAGS) {
            View.VISIBLE
        } else {
            View.INVISIBLE
        }
    }

    private fun showSortMenu() {
        val popupMenu = PopupMenu(this, sortSelector)
        popupMenu.menuInflater.inflate(R.menu.library_sort_menu, popupMenu.menu)
        popupMenu.setOnMenuItemClickListener { menuItem ->
            val selectedField = when (menuItem.itemId) {
                R.id.sortDateModified -> LibrarySortField.DATE_MODIFIED
                R.id.sortDateCreated -> LibrarySortField.DATE_CREATED
                R.id.sortTitle -> LibrarySortField.TITLE
                R.id.sortImdbRating -> LibrarySortField.IMDB_RATING
                R.id.sortCriticRating -> LibrarySortField.CRITIC_RATING
                R.id.sortProductionYear -> LibrarySortField.PRODUCTION_YEAR
                R.id.sortPremiereDate -> LibrarySortField.PREMIERE_DATE
                R.id.sortOfficialRating -> LibrarySortField.OFFICIAL_RATING
                R.id.sortDatePlayed -> LibrarySortField.DATE_PLAYED
                R.id.sortPlaybackDuration -> LibrarySortField.PLAYBACK_DURATION
                R.id.sortRandom -> LibrarySortField.RANDOM
                else -> return@setOnMenuItemClickListener false
            }
            toggleSort(selectedField)
            true
        }
        popupMenu.show()
    }

    private fun toggleSort(selectedField: LibrarySortField) {
        sortDescending = if (selectedField == currentSortField) {
            !sortDescending
        } else {
            true
        }
        currentSortField = selectedField
        updateSortUi()
        resetAndLoad()
    }

    private fun updateSortUi() {
        val arrow = if (sortDescending) "↓" else "↑"
        sortText.text = getString(currentSortField.labelRes) + " " + arrow
    }

    private fun openItem(item: MediaPosterUiModel) {
        AppNavigator.openPosterItem(this, connection, item, loadedItems)
    }

    companion object {
        const val EXTRA_LIBRARY_ID = "extra_library_id"
        const val EXTRA_LIBRARY_NAME = "extra_library_name"
    }
}
