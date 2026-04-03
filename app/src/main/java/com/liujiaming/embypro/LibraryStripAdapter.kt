package com.liujiaming.embypro

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
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
            itemView.setOnClickListener { onLibraryClick(item) }
        }

        private fun applyPlaceholder(item: MediaLibraryUiModel) {
            val background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = itemView.resources.displayMetrics.density * 10
                orientation = GradientDrawable.Orientation.LEFT_RIGHT
                colors = intArrayOf(
                    Color.parseColor(item.style.fillColor),
                    lightenColor(item.style.fillColor)
                )
            }

            coverImage.background = background
            coverImage.setImageResource(item.style.iconRes)
            coverImage.setColorFilter(Color.WHITE)
        }

        private fun lightenColor(colorHex: String): Int {
            val base = Color.parseColor(colorHex)
            val red = (Color.red(base) + 50).coerceAtMost(255)
            val green = (Color.green(base) + 50).coerceAtMost(255)
            val blue = (Color.blue(base) + 50).coerceAtMost(255)
            return Color.rgb(red, green, blue)
        }
    }
}
