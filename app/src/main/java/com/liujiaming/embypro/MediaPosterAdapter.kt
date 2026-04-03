package com.liujiaming.embypro

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class MediaPosterAdapter(
    private val items: List<MediaPosterUiModel>,
    private val cardLayout: Int,
    private val accessToken: String?,
    private val onItemClick: ((MediaPosterUiModel) -> Unit)? = null
) : RecyclerView.Adapter<MediaPosterAdapter.MediaPosterViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaPosterViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(cardLayout, parent, false)
        return MediaPosterViewHolder(view)
    }

    override fun onBindViewHolder(holder: MediaPosterViewHolder, position: Int) {
        holder.bind(items[position], accessToken, onItemClick)
    }

    override fun getItemCount(): Int = items.size

    class MediaPosterViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val posterImage: ImageView = itemView.findViewById(R.id.posterImage)
        private val titleText: TextView = itemView.findViewById(R.id.posterTitle)
        private val subtitleText: TextView? = itemView.findViewById(R.id.posterSubtitle)

        fun bind(
            item: MediaPosterUiModel,
            accessToken: String?,
            onItemClick: ((MediaPosterUiModel) -> Unit)?
        ) {
            applyPlaceholder(item)
            EmbyImageLoader.load(
                imageView = posterImage,
                url = item.imageUrl,
                token = accessToken,
                onFailure = { applyPlaceholder(item) }
            )
            titleText.text = item.title
            subtitleText?.text = item.subtitle
            subtitleText?.visibility = if (item.subtitle.isBlank()) View.GONE else View.VISIBLE
            itemView.setOnClickListener { onItemClick?.invoke(item) }
        }

        private fun applyPlaceholder(item: MediaPosterUiModel) {
            val background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = itemView.resources.displayMetrics.density * 14
                orientation = GradientDrawable.Orientation.BL_TR
                colors = intArrayOf(
                    Color.parseColor(item.style.fillColor),
                    lightenColor(item.style.fillColor)
                )
            }
            posterImage.setImageResource(item.style.iconRes)
            posterImage.background = background
            posterImage.setColorFilter(Color.WHITE)
        }

        private fun lightenColor(colorHex: String): Int {
            val base = Color.parseColor(colorHex)
            val red = (Color.red(base) + 55).coerceAtMost(255)
            val green = (Color.green(base) + 55).coerceAtMost(255)
            val blue = (Color.blue(base) + 55).coerceAtMost(255)
            return Color.rgb(red, green, blue)
        }
    }
}
