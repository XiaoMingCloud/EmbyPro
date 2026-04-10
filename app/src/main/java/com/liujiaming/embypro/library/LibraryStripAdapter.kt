package com.liujiaming.embypro

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
            applyPlaceholder()
            EmbyImageLoader.load(
                imageView = coverImage,
                url = item.imageUrl,
                token = accessToken,
                onFailure = { applyPlaceholder() }
            )
            titleText.text = item.title
            itemView.setDebouncedClickListener { onLibraryClick(item) }
        }

        private fun applyPlaceholder() {
            AppIconPlaceholder.apply(
                imageView = coverImage,
                cornerRadiusDp = 10f
            )
        }
    }
}
