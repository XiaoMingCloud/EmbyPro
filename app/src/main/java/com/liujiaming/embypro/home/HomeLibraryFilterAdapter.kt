package com.liujiaming.embypro

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.RecyclerView

class HomeLibraryFilterAdapter(
    private val items: List<MediaLibraryUiModel>,
    private val excludedIds: MutableSet<String>,
    private val onToggleExcluded: (MediaLibraryUiModel, Boolean) -> Unit
) : RecyclerView.Adapter<HomeLibraryFilterAdapter.HomeLibraryFilterViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HomeLibraryFilterViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_home_library_filter, parent, false)
        return HomeLibraryFilterViewHolder(view)
    }

    override fun onBindViewHolder(holder: HomeLibraryFilterViewHolder, position: Int) {
        holder.bind(items[position], excludedIds.contains(items[position].id), onToggleExcluded)
    }

    override fun getItemCount(): Int = items.size

    fun updateExcluded(libraryId: String, excluded: Boolean) {
        if (excluded) {
            excludedIds.add(libraryId)
        } else {
            excludedIds.remove(libraryId)
        }
        val index = items.indexOfFirst { it.id == libraryId }
        if (index != -1) notifyItemChanged(index)
    }

    class HomeLibraryFilterViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleText: TextView = itemView.findViewById(R.id.homeLibraryFilterTitle)
        private val subtitleText: TextView = itemView.findViewById(R.id.homeLibraryFilterSubtitle)
        private val toggleSwitch: SwitchCompat = itemView.findViewById(R.id.homeLibraryFilterSwitch)

        fun bind(
            item: MediaLibraryUiModel,
            excluded: Boolean,
            onToggleExcluded: (MediaLibraryUiModel, Boolean) -> Unit
        ) {
            titleText.text = item.title
            subtitleText.text = itemView.context.getString(
                if (excluded) R.string.home_library_excluded else R.string.home_library_included
            )
            toggleSwitch.setOnCheckedChangeListener(null)
            toggleSwitch.isChecked = excluded
            toggleSwitch.setOnCheckedChangeListener { _, isChecked ->
                subtitleText.text = itemView.context.getString(
                    if (isChecked) R.string.home_library_excluded else R.string.home_library_included
                )
                onToggleExcluded(item, isChecked)
            }
            itemView.setDebouncedClickListener {
                toggleSwitch.isChecked = !toggleSwitch.isChecked
            }
        }
    }
}
