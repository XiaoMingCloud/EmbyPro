package com.liujiaming.embypro

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * RecyclerView adapter for displaying media libraries in a grid layout.
 * Loads library cover images and handles click events.
 */
class MediaLibraryGridAdapter(
    private val items: List<MediaLibraryUiModel>,
    private val accessToken: String?,
    private val onLibraryClick: (MediaLibraryUiModel) -> Unit
) : RecyclerView.Adapter<MediaLibraryGridAdapter.MediaLibraryGridViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaLibraryGridViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_media_library_grid, parent, false)
        return MediaLibraryGridViewHolder(view)
    }

    override fun onBindViewHolder(holder: MediaLibraryGridViewHolder, position: Int) {
        holder.bind(items[position], accessToken, onLibraryClick)
    }

    override fun getItemCount(): Int = items.size

    /**
     * ViewHolder for media library grid items.
     * Binds library data to cover image and title views.
     */
    class MediaLibraryGridViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val coverImage: ImageView = itemView.findViewById(R.id.libraryCover)
        private val titleText: TextView = itemView.findViewById(R.id.libraryTitle)

        /**
         * Binds library item data to the view.
         * Loads cover image with fallback to placeholder.
         */
        fun bind(
            item: MediaLibraryUiModel,
            accessToken: String?,
            onLibraryClick: (MediaLibraryUiModel) -> Unit
        ) {
            applyPlaceholder()
            EmbyImageLoader.load(
                imageView = coverImage,
                url = item.imageUrl,
                token = accessToken,
                onFailure = {
                    applyPlaceholder()
                }
            )
            titleText.text = item.title
            titleText.setTextColor(GlobalThemeManager.primaryTextColor(itemView.context))
            itemView.setDebouncedClickListener { onLibraryClick(item) }
        }

        /**
         * Applies a placeholder drawable to the cover image.
         */
        private fun applyPlaceholder() {
            AppIconPlaceholder.apply(
                imageView = coverImage,
                cornerRadiusDp = 14f
            )
        }
    }
}
