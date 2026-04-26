package com.liujiaming.embypro

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.content.res.ColorStateList
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

/**
 * RecyclerView adapter for displaying theme options.
 * Shows theme swatches with selection indicators.
 */
class ThemeOptionAdapter(
    private val items: List<GlobalThemeOption>,
    private var selectedTheme: GlobalThemeOption,
    private val onThemeSelected: (GlobalThemeOption) -> Unit
) : RecyclerView.Adapter<ThemeOptionAdapter.ThemeOptionViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThemeOptionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_theme_option, parent, false)
        return ThemeOptionViewHolder(view)
    }

    override fun onBindViewHolder(holder: ThemeOptionViewHolder, position: Int) {
        holder.bind(items[position], items[position] == selectedTheme)
    }

    override fun getItemCount(): Int = items.size

    fun updateSelectedTheme(theme: GlobalThemeOption) {
        selectedTheme = theme
        notifyDataSetChanged()
    }

    inner class ThemeOptionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val card = itemView as MaterialCardView
        private val swatch = itemView.findViewById<View>(R.id.themeOptionSwatch)
        private val title = itemView.findViewById<TextView>(R.id.themeOptionTitle)
        private val check = itemView.findViewById<ImageView>(R.id.themeOptionCheck)

        fun bind(item: GlobalThemeOption, selected: Boolean) {
            val context = itemView.context
            card.setCardBackgroundColor(GlobalThemeManager.cardBackgroundColor(context))
            swatch.backgroundTintList = ContextCompat.getColorStateList(context, item.fontColorRes)
            title.text = context.getString(item.labelRes)
            title.setTextColor(GlobalThemeManager.primaryTextColor(context))
            check.visibility = if (selected) View.VISIBLE else View.INVISIBLE
            check.imageTintList = ColorStateList.valueOf(GlobalThemeManager.primaryTextColor(context))
            card.strokeWidth = if (selected) 2 else 1
            card.strokeColor = if (selected) {
                GlobalThemeManager.primaryTextColor(context)
            } else {
                GlobalThemeManager.cardStrokeColor(context)
            }
            card.setDebouncedClickListener { onThemeSelected(item) }
        }
    }
}
