package com.liujiaming.embypro

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * RecyclerView adapter for displaying music items in a list.
 * Shows artwork, title, subtitle, and detail information for each music entry.
 */
class MusicListAdapter(
    private val items: MutableList<MusicListEntryUiModel>,
    private val accessToken: String?,
    private val onItemClick: (MusicListEntryUiModel) -> Unit
) : RecyclerView.Adapter<MusicListAdapter.MusicListViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MusicListViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_music_list, parent, false)
        return MusicListViewHolder(view)
    }

    override fun onBindViewHolder(holder: MusicListViewHolder, position: Int) {
        holder.bind(items[position], accessToken, onItemClick)
    }

    override fun getItemCount(): Int = items.size

    /**
     * Replaces all items in the adapter and refreshes the display.
     */
    fun submitItems(newItems: List<MusicListEntryUiModel>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    /**
     * ViewHolder for music list items.
     * Binds music entry data to artwork, title, subtitle, and detail views.
     */
    class MusicListViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val artworkView: ImageView = itemView.findViewById(R.id.musicListArtwork)
        private val titleView: TextView = itemView.findViewById(R.id.musicListTitle)
        private val subtitleView: TextView = itemView.findViewById(R.id.musicListSubtitle)
        private val detailView: TextView = itemView.findViewById(R.id.musicListDetail)
        private val actionIconView: ImageView = itemView.findViewById(R.id.musicListActionIcon)

        /**
         * Binds music entry data to the view.
         * Shows play icon for songs, next icon for containers.
         */
        fun bind(
            item: MusicListEntryUiModel,
            accessToken: String?,
            onItemClick: (MusicListEntryUiModel) -> Unit
        ) {
            applyPlaceholder()
            EmbyImageLoader.load(
                imageView = artworkView,
                url = item.imageUrl,
                token = accessToken,
                onFailure = { applyPlaceholder() }
            )

            titleView.text = item.title
            subtitleView.text = item.subtitle
            subtitleView.visibility = if (item.subtitle.isBlank()) View.GONE else View.VISIBLE
            detailView.text = item.detail
            detailView.visibility = if (item.detail.isBlank()) View.GONE else View.VISIBLE

            actionIconView.setImageResource(
                if (item.kind == MusicEntryKind.SONG) {
                    android.R.drawable.ic_media_play
                } else {
                    android.R.drawable.ic_media_next
                }
            )
            itemView.setDebouncedClickListener { onItemClick(item) }
        }

        /**
         * Applies placeholder drawable to artwork image.
         */
        private fun applyPlaceholder() {
            AppIconPlaceholder.apply(
                imageView = artworkView,
                cornerRadiusDp = 12f
            )
        }
    }
}
