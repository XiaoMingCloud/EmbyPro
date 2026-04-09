package com.liujiaming.embypro

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class LibraryStripAdapter(
    private val items: List<MediaLibraryUiModel>,
    private val accessToken: String?,
    private val onLibraryClick: (MediaLibraryUiModel) -> Unit
) : RecyclerView.Adapter<LibraryStripAdapter.LibraryViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LibraryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_library_chip, parent, false)
        return LibraryViewHolder(view)
    }

    override fun onBindViewHolder(holder: LibraryViewHolder, position: Int) {
        holder.bind(items[position], accessToken, onLibraryClick)
    }

    override fun getItemCount(): Int = items.size

    class LibraryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val coverImage: ImageView = itemView.findViewById(R.id.libraryCover)
        private val titleText: TextView = itemView.findViewById(R.id.libraryTitle)

        fun bind(
            item: MediaLibraryUiModel,
            accessToken: String?,
            onLibraryClick: (MediaLibraryUiModel) -> Unit
        ) {
            applyPlaceholder(item)
            EmbyImageLoader.load(
                imageView = coverImage,
                url = item.imageUrl,
                token = accessToken,
                onFailure = { applyPlaceholder(item) }
            )
            titleText.text = item.title
            itemView.setDebouncedClickListener { onLibraryClick(item) }
        }

        private fun applyPlaceholder(item: MediaLibraryUiModel) {
            val background = LibraryVisualHelper.buildPlaceholder(itemView, item.style.fillColor, 10f)

            coverImage.background = background
            coverImage.setImageResource(item.style.iconRes)
            coverImage.setColorFilter(Color.WHITE)
        }
    }
}
