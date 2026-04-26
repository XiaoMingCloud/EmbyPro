package com.liujiaming.embypro

import android.graphics.BitmapFactory
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class GlobalBackgroundAdapter(
    private val items: MutableList<GlobalBackgroundImage>,
    private val onItemClick: (GlobalBackgroundImage) -> Unit,
    private val onDeleteClick: (GlobalBackgroundImage) -> Unit
) : RecyclerView.Adapter<GlobalBackgroundAdapter.GlobalBackgroundViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GlobalBackgroundViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_global_background, parent, false)
        return GlobalBackgroundViewHolder(view)
    }

    override fun onBindViewHolder(holder: GlobalBackgroundViewHolder, position: Int) {
        holder.bind(items[position], onItemClick, onDeleteClick)
    }

    override fun getItemCount(): Int = items.size

    fun submitItems(newItems: List<GlobalBackgroundImage>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    class GlobalBackgroundViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val card = itemView as MaterialCardView
        private val previewImage = itemView.findViewById<ImageView>(R.id.globalBackgroundPreviewImage)
        private val selectedBadge = itemView.findViewById<View>(R.id.globalBackgroundSelectedBadge)
        private val deleteButton = itemView.findViewById<ImageButton>(R.id.globalBackgroundDeleteButton)

        fun bind(
            item: GlobalBackgroundImage,
            onItemClick: (GlobalBackgroundImage) -> Unit,
            onDeleteClick: (GlobalBackgroundImage) -> Unit
        ) {
            val bitmap = if (item.builtIn) null else BitmapFactory.decodeFile(item.absolutePath)
            if (item.builtIn) {
                previewImage.setImageDrawable(null)
                previewImage.scaleType = ImageView.ScaleType.CENTER_CROP
                previewImage.setBackgroundColor(Color.WHITE)
            } else if (bitmap != null) {
                previewImage.setImageBitmap(bitmap)
                previewImage.scaleType = ImageView.ScaleType.CENTER_CROP
                previewImage.background = null
            } else {
                AppIconPlaceholder.apply(previewImage, cornerRadiusDp = 18f)
            }
            card.strokeWidth = if (item.selected) 2 else 1
            card.strokeColor = if (item.selected) {
                GlobalThemeManager.primaryTextColor(itemView.context)
            } else {
                GlobalThemeManager.cardStrokeColor(itemView.context)
            }
            selectedBadge.visibility = if (item.selected) View.VISIBLE else View.GONE
            deleteButton.visibility = if (item.builtIn) View.GONE else View.VISIBLE
            itemView.setDebouncedClickListener { onItemClick(item) }
            deleteButton.setDebouncedClickListener { onDeleteClick(item) }
        }
    }
}
