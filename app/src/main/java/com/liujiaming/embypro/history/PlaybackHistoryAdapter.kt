package com.liujiaming.embypro

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.imageview.ShapeableImageView
import kotlin.math.roundToInt

/**
 * RecyclerView adapter for displaying playback history items.
 * Supports selection mode for batch operations and shows playback progress.
 */
class PlaybackHistoryAdapter(
    private val items: List<PlaybackHistoryItemUiModel>,
    private val accessToken: String,
    private val onItemClick: (PlaybackHistoryItemUiModel) -> Unit,
    private val onItemLongClick: (PlaybackHistoryItemUiModel) -> Unit,
    private val onFavoriteClick: ((PlaybackHistoryItemUiModel) -> Unit)? = null
) : RecyclerView.Adapter<PlaybackHistoryAdapter.PlaybackHistoryViewHolder>() {

    private val selectedItemIds = linkedSetOf<String>()
    private var selectionMode = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaybackHistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_playback_history, parent, false)
        return PlaybackHistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlaybackHistoryViewHolder, position: Int) {
        holder.bind(
            item = items[position],
            accessToken = accessToken,
            selectionMode = selectionMode,
            checked = selectedItemIds.contains(items[position].itemId),
            onItemClick = onItemClick,
            onItemLongClick = onItemLongClick,
            onFavoriteClick = onFavoriteClick
        )
    }

    override fun getItemCount(): Int = items.size

    /**
     * Enables or disables selection mode for batch operations.
     * Clears selections when disabled.
     */
    fun setSelectionMode(enabled: Boolean) {
        if (selectionMode == enabled) return
        selectionMode = enabled
        if (!enabled) {
            selectedItemIds.clear()
        }
        notifyDataSetChanged()
    }

    /**
     * Returns whether selection mode is currently active.
     */
    fun isSelectionMode(): Boolean = selectionMode

    /**
     * Toggles selection state for a specific item.
     */
    fun toggleSelection(itemId: String) {
        if (selectedItemIds.contains(itemId)) {
            selectedItemIds.remove(itemId)
        } else {
            selectedItemIds.add(itemId)
        }
        val index = items.indexOfFirst { it.itemId == itemId }
        if (index >= 0) notifyItemChanged(index)
    }

    /**
     * Returns set of currently selected item IDs.
     */
    fun selectedIds(): Set<String> = selectedItemIds.toSet()

    /**
     * Clears all selections.
     */
    fun clearSelection() {
        selectedItemIds.clear()
        notifyDataSetChanged()
    }

    /**
     * Selects all items in the list.
     */
    fun selectAll() {
        selectedItemIds.clear()
        selectedItemIds.addAll(items.map { it.itemId })
        notifyDataSetChanged()
    }

    /**
     * Checks if all items are currently selected.
     */
    fun areAllSelected(): Boolean = items.isNotEmpty() && selectedItemIds.size == items.size

    /**
     * Removes items from the adapter by their IDs.
     */
    fun removeItems(itemIds: Set<String>) {
        if (itemIds.isEmpty()) return
        selectedItemIds.removeAll(itemIds)
        notifyDataSetChanged()
    }

    /**
     * ViewHolder for playback history items.
     * Displays poster, title, library name, time, and playback progress.
     */
    class PlaybackHistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val posterImage: ShapeableImageView = itemView.findViewById(R.id.playbackHistoryPosterImage)
        private val titleText: TextView = itemView.findViewById(R.id.playbackHistoryTitleText)
        private val libraryText: TextView = itemView.findViewById(R.id.playbackHistoryLibraryText)
        private val timeText: TextView = itemView.findViewById(R.id.playbackHistoryTimeText)
        private val checkBox: CheckBox = itemView.findViewById(R.id.playbackHistoryCheckBox)
        private val favoriteButton: ImageButton = itemView.findViewById(R.id.playbackHistoryFavoriteButton)
        private val progressTrack: View = itemView.findViewById(R.id.playbackHistoryProgressTrack)
        private val progressFill: View = itemView.findViewById(R.id.playbackHistoryProgressFill)
        private val durationBadge: TextView = itemView.findViewById(R.id.playbackHistoryDurationBadge)

        /**
         * Binds playback history item data to the view.
         * Handles selection mode, favorites, and playback progress display.
         */
        fun bind(
            item: PlaybackHistoryItemUiModel,
            accessToken: String,
            selectionMode: Boolean,
            checked: Boolean,
            onItemClick: (PlaybackHistoryItemUiModel) -> Unit,
            onItemLongClick: (PlaybackHistoryItemUiModel) -> Unit,
            onFavoriteClick: ((PlaybackHistoryItemUiModel) -> Unit)?
        ) {
            titleText.text = item.title
            titleText.setTextColor(GlobalThemeManager.primaryTextColor(itemView.context))
            libraryText.text = item.libraryName
            libraryText.setTextColor(GlobalThemeManager.secondaryTextColor(itemView.context))
            timeText.text = item.playedTimeLabel
            timeText.setTextColor(GlobalThemeManager.secondaryTextColor(itemView.context))
            timeText.visibility = if (item.playedTimeLabel.isBlank()) View.GONE else View.VISIBLE
            libraryText.maxLines = if (item.playedTimeLabel.isBlank()) 2 else 1
            checkBox.visibility = if (selectionMode) View.VISIBLE else View.GONE
            checkBox.isChecked = checked
            favoriteButton.visibility = if (!selectionMode && onFavoriteClick != null) View.VISIBLE else View.GONE
            bindPlaybackProgress(item)

            applyPlaceholder()
            EmbyImageLoader.load(
                imageView = posterImage,
                url = item.imageUrl,
                token = accessToken,
                onFailure = { applyPlaceholder() }
            )

            itemView.setDebouncedClickListener { onItemClick(item) }
            favoriteButton.setDebouncedClickListener { onFavoriteClick?.invoke(item) }
            itemView.setOnLongClickListener {
                onItemLongClick(item)
                true
            }
        }

        /**
         * Applies placeholder drawable to poster image.
         */
        private fun applyPlaceholder() {
            AppIconPlaceholder.apply(
                imageView = posterImage,
                cornerRadiusDp = 14f
            )
        }

        /**
         * Binds playback progress information to the progress bar and duration badge.
         * Shows played/total time and visual progress indicator.
         */
        private fun bindPlaybackProgress(item: PlaybackHistoryItemUiModel) {
            val runtimeTicks = item.runtimeTicks.coerceAtLeast(0L)
            val playedTicks = when {
                item.played && runtimeTicks > 0L -> runtimeTicks
                else -> item.playbackPositionTicks.coerceAtLeast(0L)
            }
            val shouldShowDuration = runtimeTicks > 0L
            durationBadge.visibility = if (shouldShowDuration) View.VISIBLE else View.GONE
            progressTrack.visibility = if (shouldShowDuration) View.VISIBLE else View.INVISIBLE
            progressFill.visibility = if (shouldShowDuration) View.VISIBLE else View.INVISIBLE

            if (!shouldShowDuration) return

            durationBadge.text = if (item.played || playedTicks >= runtimeTicks) {
                formatTicks(runtimeTicks)
            } else {
                "${formatTicks(playedTicks)}/${formatTicks(runtimeTicks)}"
            }

            progressTrack.post {
                val progressRatio = if (runtimeTicks <= 0L) 0f else {
                    (playedTicks.toFloat() / runtimeTicks.toFloat()).coerceIn(0f, 1f)
                }
                val targetWidth = (progressTrack.width * progressRatio).roundToInt()
                progressFill.layoutParams = progressFill.layoutParams.apply {
                    width = targetWidth
                }
                progressFill.requestLayout()
            }
        }

        /**
         * Formats ticks (100-nanosecond units) to HH:MM:SS or MM:SS string.
         */
        private fun formatTicks(ticks: Long): String {
            if (ticks <= 0L) return "00:00"
            val totalSeconds = ticks / 10_000_000L
            val hours = totalSeconds / 3600L
            val minutes = (totalSeconds % 3600L) / 60L
            val seconds = totalSeconds % 60L
            return if (hours > 0) {
                String.format("%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }
    }
}
