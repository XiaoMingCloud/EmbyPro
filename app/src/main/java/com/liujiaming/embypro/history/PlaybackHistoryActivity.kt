package com.liujiaming.embypro

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import java.util.concurrent.ExecutorService

/**
 * Activity displaying playback history with category filtering.
 * Supports video/live/column categories and batch management operations.
 */
class PlaybackHistoryActivity : AppCompatActivity() {
    private val networkExecutor: ExecutorService = AppExecutors.io
    private val mediaRepository by lazy { MediaRepository(this) }
    private val sessionStore by lazy { ServerSessionStore(this) }
    private val serverRepository by lazy { ServerRepository(this) }

    private lateinit var connection: ServerConnection

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyText: TextView
    private lateinit var topBar: View
    private lateinit var videoButton: MaterialButton
    private lateinit var actionBar: View
    private lateinit var manageButton: TextView
    private lateinit var titleText: TextView
    private lateinit var selectionText: TextView
    private lateinit var selectAllButton: TextView
    private lateinit var clearButton: MaterialButton

    private val items = mutableListOf<PlaybackHistoryItemUiModel>()
    private lateinit var adapter: PlaybackHistoryAdapter

    private var currentCategory = PlaybackHistoryCategory.VIDEO
    private var isLoading = false
    private var isClearing = false
    private var totalCount = Int.MAX_VALUE
    private var startIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        EdgeToEdgeHelper.enable(this, lightSystemBars = GlobalThemeStore(this).loadTheme().lightSystemBars)
        setContentView(R.layout.activity_playback_history)
        supportActionBar?.hide()
        GlobalThemeManager.apply(this)

        connection = requireServerConnection(sessionStore, serverRepository) ?: return

        topBar = findViewById(R.id.playbackHistoryTopBar)
        recyclerView = findViewById(R.id.playbackHistoryRecyclerView)
        progressBar = findViewById(R.id.playbackHistoryProgressBar)
        emptyText = findViewById(R.id.playbackHistoryEmptyText)
        videoButton = findViewById(R.id.playbackHistoryVideoButton)
        actionBar = findViewById(R.id.playbackHistoryActionBar)
        manageButton = findViewById(R.id.playbackHistoryManageButton)
        titleText = findViewById(R.id.playbackHistoryTitleText)
        selectionText = findViewById(R.id.playbackHistorySelectionText)
        selectAllButton = findViewById(R.id.playbackHistorySelectAllButton)
        clearButton = findViewById(R.id.playbackHistoryClearButton)

        findViewById<ImageButton>(R.id.playbackHistoryBackButton).setDebouncedClickListener { finish() }
        videoButton.setDebouncedClickListener { switchCategory(PlaybackHistoryCategory.VIDEO) }
        manageButton.setDebouncedClickListener {
            if (adapter.isSelectionMode()) {
                exitSelectionMode()
            } else {
                enterSelectionMode()
            }
        }
        selectAllButton.setDebouncedClickListener {
            if (adapter.areAllSelected()) {
                adapter.clearSelection()
            } else {
                adapter.selectAll()
            }
            updateSelectionUi()
        }
        clearButton.setDebouncedClickListener { confirmClearPlayedState() }

        adapter = PlaybackHistoryAdapter(
            items = items,
            accessToken = connection.accessToken,
            onItemClick = { item ->
                if (adapter.isSelectionMode()) {
                    adapter.toggleSelection(item.itemId)
                    updateSelectionUi()
                } else {
                    openVideoDirectly(item)
                }
            },
            onItemLongClick = { item ->
                if (!adapter.isSelectionMode()) {
                    enterSelectionMode()
                }
                adapter.toggleSelection(item.itemId)
                updateSelectionUi()
            }
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
        EdgeToEdgeHelper.applyInsets(actionBar, applyBottom = true)
        updateCategorySelection()
        updateSelectionUi()
        loadFirstPage()
    }

    /**
     * Switches to a different playback history category.
     * @param category The category to switch to
     */
    private fun switchCategory(category: PlaybackHistoryCategory) {
        if (currentCategory == category) return
        currentCategory = category
        updateCategorySelection()
        loadFirstPage()
    }

    /**
     * Updates the visual state of category selection buttons.
     */
    private fun updateCategorySelection() {
        updateCategoryButton(videoButton, currentCategory == PlaybackHistoryCategory.VIDEO)
    }

    /**
     * Updates the appearance of a category button based on selection state.
     * @param button The button to update
     * @param selected Whether the button is selected
     */
    private fun updateCategoryButton(button: MaterialButton, selected: Boolean) {
        button.isChecked = selected
        button.alpha = if (selected) 1f else 0.72f
    }

    /**
     * Loads the first page of playback history, clearing existing data and exiting selection mode.
     */
    private fun loadFirstPage() {
        exitSelectionMode()
        items.clear()
        adapter.notifyDataSetChanged()
        startIndex = 0
        totalCount = Int.MAX_VALUE
        emptyText.visibility = View.GONE
        loadNextPage(showLoading = true)
    }

    /**
     * Loads the next page of playback history with pagination support.
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
            val result = mediaRepository.fetchPlaybackHistoryPage(connection, startIndex, PAGE_SIZE, currentCategory)
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
                    val message = userFriendlyErrorMessage(error, R.string.playback_history_load_failed)
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
     * Opens video player directly for a history item with resume position.
     * @param item The playback history item to play
     */
    private fun openVideoDirectly(item: PlaybackHistoryItemUiModel) {
        val preferredStartPositionMs = if (item.played) 0L else item.playbackPositionTicks / 10_000L
        playVideoDirectly(
            connection = connection,
            mediaRepository = mediaRepository,
            itemId = item.itemId,
            queue = AppNavigator.buildHistoryVideoQueue(items, item.itemId),
            preferredStartPositionMs = preferredStartPositionMs
        )
    }

    /**
     * Enters selection mode for batch operations.
     */
    private fun enterSelectionMode() {
        if (items.isEmpty()) return
        adapter.setSelectionMode(true)
        updateSelectionUi()
    }

    /**
     * Exits selection mode and clears all selections.
     */
    private fun exitSelectionMode() {
        adapter.setSelectionMode(false)
        updateSelectionUi()
    }

    /**
     * Updates the UI elements based on current selection mode state.
     */
    private fun updateSelectionUi() {
        val inSelectionMode = adapter.isSelectionMode()
        val selectedCount = adapter.selectedIds().size
        titleText.visibility = if (inSelectionMode) View.GONE else View.VISIBLE
        selectionText.visibility = if (inSelectionMode) View.VISIBLE else View.GONE
        selectionText.text = if (selectedCount > 0) {
            getString(R.string.playback_history_selected_count, selectedCount)
        } else {
            getString(R.string.playback_history_select_hint)
        }
        manageButton.text = getString(if (inSelectionMode) R.string.done else R.string.manage)
        actionBar.visibility = if (inSelectionMode) View.VISIBLE else View.GONE
        selectAllButton.text = getString(
            if (adapter.areAllSelected()) R.string.deselect_all else R.string.select_all
        )
        clearButton.isEnabled = selectedCount > 0 && !isClearing
        clearButton.alpha = if (clearButton.isEnabled) 1f else 0.45f
        videoButton.visibility = if (inSelectionMode) View.GONE else View.VISIBLE
    }

    /**
     * Shows confirmation dialog before clearing played state.
     */
    private fun confirmClearPlayedState() {
        val selectedIds = adapter.selectedIds()
        if (selectedIds.isEmpty() || isClearing) return

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_clear_played_state, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        dialog.window?.attributes = dialog.window?.attributes?.apply {
            dimAmount = 0.22f
        }

        dialogView.findViewById<TextView>(R.id.clearPlayedStateDialogCancelButton)
            .setDebouncedClickListener { dialog.dismiss() }
        dialogView.findViewById<TextView>(R.id.clearPlayedStateDialogConfirmButton)
            .setDebouncedClickListener {
                dialog.dismiss()
                clearPlayedState(selectedIds)
            }
        dialog.show()
    }

    /**
     * Clears played state for selected items and removes them from the list.
     * @param selectedIds Set of item IDs to clear played state for
     */
    private fun clearPlayedState(selectedIds: Set<String>) {
        if (selectedIds.isEmpty()) return
        isClearing = true
        updateSelectionUi()

        networkExecutor.execute {
            var successCount = 0
            var firstError: Throwable? = null
            selectedIds.forEach { itemId ->
                val result = mediaRepository.clearPlayedState(connection, itemId)
                result.onSuccess { successCount++ }
                    .onFailure { error ->
                        if (firstError == null) firstError = error
                    }
            }

            runOnUiThread {
                isClearing = false
                if (successCount > 0) {
                    items.removeAll { it.itemId in selectedIds }
                    adapter.removeItems(selectedIds)
                    adapter.notifyDataSetChanged()
                    Toast.makeText(
                        this,
                        getString(R.string.clear_played_state_success, successCount),
                        Toast.LENGTH_SHORT
                    ).show()
                }

                if (items.isEmpty()) {
                    emptyText.visibility = View.VISIBLE
                    recyclerView.visibility = View.INVISIBLE
                    exitSelectionMode()
                } else {
                    emptyText.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                    if (successCount > 0) {
                        exitSelectionMode()
                    } else {
                        updateSelectionUi()
                    }
                }

                if (firstError != null && successCount == 0) {
                    Toast.makeText(
                        this,
                        userFriendlyErrorMessage(firstError, R.string.playback_history_load_failed),
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
