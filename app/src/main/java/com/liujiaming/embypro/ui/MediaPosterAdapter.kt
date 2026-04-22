package com.liujiaming.embypro

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * RecyclerView adapter for displaying media items as posters.
 * Supports custom card layouts and click callbacks.
 */
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

    /**
     * ViewHolder for media poster items.
     * Binds poster image, title, and optional subtitle.
     */
    class MediaPosterViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val posterImage: ImageView = itemView.findViewById(R.id.posterImage)
        private val titleText: TextView = itemView.findViewById(R.id.posterTitle)
        private val subtitleText: TextView? = itemView.findViewById(R.id.posterSubtitle)

        /**
         * Binds media item data to the view.
         * Loads poster image with placeholder fallback.
         */
        fun bind(
            item: MediaPosterUiModel,
            accessToken: String?,
            onItemClick: ((MediaPosterUiModel) -> Unit)?
        ) {
            applyPlaceholder()
            EmbyImageLoader.load(
                imageView = posterImage,
                url = item.imageUrl,
                token = accessToken,
                onFailure = { applyPlaceholder() }
            )
            titleText.text = item.title
            titleText.setTextColor(GlobalThemeManager.primaryTextColor(itemView.context))
            subtitleText?.text = item.subtitle
            subtitleText?.setTextColor(GlobalThemeManager.secondaryTextColor(itemView.context))
            subtitleText?.visibility = if (item.subtitle.isBlank()) View.GONE else View.VISIBLE
            itemView.setDebouncedClickListener { onItemClick?.invoke(item) }
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
    }
}
