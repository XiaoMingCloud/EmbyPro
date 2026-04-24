package com.liujiaming.embypro

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.min

fun Context.showMusicPartitionPickerDialog(
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit
) {
    val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_music_partition_picker, null)
    val recyclerView = dialogView.findViewById<RecyclerView>(R.id.partitionPickerRecyclerView)
    val cancelButton = dialogView.findViewById<TextView>(R.id.partitionPickerCancelButton)
    val subtitleView = dialogView.findViewById<TextView>(R.id.partitionPickerSubtitle)

    subtitleView.text = if (labels.size <= 1) {
        getString(R.string.music_library_partition_fallback)
    } else {
        getString(R.string.music_settings_count_value, labels.size)
    }

    recyclerView.layoutManager = LinearLayoutManager(this)
    recyclerView.adapter = MusicPartitionPickerAdapter(labels, selectedIndex) { which ->
        onSelected(which)
    }
    recyclerView.layoutParams = recyclerView.layoutParams.apply {
        height = min(dp(360), labels.size.coerceAtLeast(1) * dp(68))
    }

    val dialog = createMusicGlassDialog(dialogView)

    cancelButton.setDebouncedClickListener { dialog.dismiss() }

    dialog.applyMusicGlassWindow(this)

    (recyclerView.adapter as? MusicPartitionPickerAdapter)?.attachDialog(dialog)
}

private class MusicPartitionPickerAdapter(
    private val labels: List<String>,
    private val selectedIndex: Int,
    private val onSelected: (Int) -> Unit
) : RecyclerView.Adapter<MusicPartitionPickerAdapter.PartitionViewHolder>() {

    private var dialog: Dialog? = null

    fun attachDialog(dialog: Dialog) {
        this.dialog = dialog
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PartitionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_music_partition_option, parent, false)
        return PartitionViewHolder(view)
    }

    override fun onBindViewHolder(holder: PartitionViewHolder, position: Int) {
        holder.bind(
            title = labels[position],
            selected = position == selectedIndex,
            onClick = {
                dialog?.dismiss()
                onSelected(position)
            }
        )
    }

    override fun getItemCount(): Int = labels.size

    class PartitionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val card = itemView.findViewById<View>(R.id.partitionOptionCard)
        private val titleView = itemView.findViewById<TextView>(R.id.partitionOptionTitle)
        private val radioButton = itemView.findViewById<RadioButton>(R.id.partitionOptionRadio)

        fun bind(title: String, selected: Boolean, onClick: () -> Unit) {
            titleView.text = title
            card.isActivated = selected
            itemView.isActivated = selected
            radioButton.isChecked = selected
            itemView.setDebouncedClickListener { onClick() }
        }
    }
}

private fun Context.dp(value: Int): Int {
    return (resources.displayMetrics.density * value).toInt()
}
