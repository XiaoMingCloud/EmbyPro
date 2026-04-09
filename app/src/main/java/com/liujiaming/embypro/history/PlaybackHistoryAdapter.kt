package com.liujiaming.embypro

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.imageview.ShapeableImageView

class PlaybackHistoryAdapter(
    private val items: List<PlaybackHistoryItemUiModel>,
    private val accessToken: String,
    private val onItemClick: (PlaybackHistoryItemUiModel) -> Unit,
    private val onItemLongClick: (PlaybackHistoryItemUiModel) -> Unit
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
            onItemLongClick = onItemLongClick
        )
    }

    override fun getItemCount(): Int = items.size

    fun setSelectionMode(enabled: Boolean) {
        if (selectionMode == enabled) return
        selectionMode = enabled
        if (!enabled) {
            selectedItemIds.clear()
        }
        notifyDataSetChanged()
    }

    fun isSelectionMode(): Boolean = selectionMode

    fun toggleSelection(itemId: String) {
        if (selectedItemIds.contains(itemId)) {
            selectedItemIds.remove(itemId)
        } else {
            selectedItemIds.add(itemId)
        }
        val index = items.indexOfFirst { it.itemId == itemId }
        if (index >= 0) notifyItemChanged(index)
    }

    fun selectedIds(): Set<String> = selectedItemIds.toSet()

    fun clearSelection() {
        selectedItemIds.clear()
        notifyDataSetChanged()
    }

    fun selectAll() {
        selectedItemIds.clear()
        selectedItemIds.addAll(items.map { it.itemId })
        notifyDataSetChanged()
    }

    fun areAllSelected(): Boolean = items.isNotEmpty() && selectedItemIds.size == items.size

    fun removeItems(itemIds: Set<String>) {
        if (itemIds.isEmpty()) return
        selectedItemIds.removeAll(itemIds)
        notifyDataSetChanged()
    }

    class PlaybackHistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val posterImage: ShapeableImageView = itemView.findViewById(R.id.playbackHistoryPosterImage)
        private val titleText: TextView = itemView.findViewById(R.id.playbackHistoryTitleText)
        private val libraryText: TextView = itemView.findViewById(R.id.playbackHistoryLibraryText)
        private val timeText: TextView = itemView.findViewById(R.id.playbackHistoryTimeText)
        private val checkBox: CheckBox = itemView.findViewById(R.id.playbackHistoryCheckBox)

        fun bind(
            item: PlaybackHistoryItemUiModel,
            accessToken: String,
            selectionMode: Boolean,
            checked: Boolean,
            onItemClick: (PlaybackHistoryItemUiModel) -> Unit,
            onItemLongClick: (PlaybackHistoryItemUiModel) -> Unit
        ) {
            titleText.text = item.title
            libraryText.text = item.libraryName
            timeText.text = item.playedTimeLabel
            checkBox.visibility = if (selectionMode) View.VISIBLE else View.GONE
            checkBox.isChecked = checked

            applyPlaceholder()
            EmbyImageLoader.load(
                imageView = posterImage,
                url = item.imageUrl,
                token = accessToken,
                onFailure = { applyPlaceholder() }
            )

            itemView.setDebouncedClickListener { onItemClick(item) }
            itemView.setOnLongClickListener {
                onItemLongClick(item)
                true
            }
        }

        private fun applyPlaceholder() {
            posterImage.setImageResource(R.drawable.ic_launcher_foreground)
            posterImage.clearColorFilter()
        }
    }
}
