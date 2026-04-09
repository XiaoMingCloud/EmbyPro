package com.liujiaming.embypro

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

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

    class MediaLibraryGridViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val coverImage: ImageView = itemView.findViewById(R.id.libraryCover)
        private val titleText: TextView = itemView.findViewById(R.id.libraryTitle)

        fun bind(
            item: MediaLibraryUiModel,
            accessToken: String?,
            onLibraryClick: (MediaLibraryUiModel) -> Unit
        ) {
            val placeholder = LibraryVisualHelper.buildPlaceholder(itemView, item.style.fillColor, 14f)
            coverImage.setImageResource(item.style.iconRes)
            coverImage.background = placeholder
            coverImage.setColorFilter(android.graphics.Color.WHITE)
            EmbyImageLoader.load(
                imageView = coverImage,
                url = item.imageUrl,
                token = accessToken,
                onFailure = {
                    coverImage.setImageResource(item.style.iconRes)
                    coverImage.background = placeholder
                    coverImage.setColorFilter(android.graphics.Color.WHITE)
                }
            )
            titleText.text = item.title
            itemView.setOnClickListener { onLibraryClick(item) }
        }
    }
}
