package com.liujiaming.embypro

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class PlayerQueueEntryUiModel(
    val indexLabel: String,
    val title: String,
    val isCurrent: Boolean
)

class PlayerQueueAdapter(
    private val items: MutableList<PlayerQueueEntryUiModel>
) : RecyclerView.Adapter<PlayerQueueAdapter.PlayerQueueViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlayerQueueViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_player_queue, parent, false)
        return PlayerQueueViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlayerQueueViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun submitItems(newItems: List<PlayerQueueEntryUiModel>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    class PlayerQueueViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val indexText: TextView = itemView.findViewById(R.id.playerQueueIndexText)
        private val titleText: TextView = itemView.findViewById(R.id.playerQueueTitleText)

        fun bind(item: PlayerQueueEntryUiModel) {
            itemView.isActivated = item.isCurrent
            indexText.text = item.indexLabel
            titleText.text = item.title
            titleText.alpha = if (item.isCurrent) 1f else 0.84f
            indexText.alpha = if (item.isCurrent) 1f else 0.72f
        }
    }
}
