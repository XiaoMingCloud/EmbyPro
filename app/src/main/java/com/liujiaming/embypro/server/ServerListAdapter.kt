package com.liujiaming.embypro

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView

data class ServerUiModel(
    val id: Long,
    val name: String,
    val username: String,
    val status: String,
    val address: String,
    val port: String,
    val password: String,
    val iconStyle: ServerIconStyle,
    val avatarUrl: String,
    val customAvatarUri: String,
    val accessToken: String,
    val userId: String
)

enum class ServerIconStyle(
    val fillColor: String,
    val iconRes: Int
) {
    INDIGO("#FF7C83D8", android.R.drawable.ic_media_play),
    ORANGE("#FFE29968", android.R.drawable.ic_menu_slideshow),
    GREEN("#FF78B88D", android.R.drawable.ic_menu_gallery),
    SLATE("#FF8D91A8", android.R.drawable.ic_menu_view)
}

interface ServerActionListener {
    fun onOpen(server: ServerUiModel)
    fun onRelogin(server: ServerUiModel)
    fun onChangeIcon(server: ServerUiModel)
    fun onChangePassword(server: ServerUiModel)
    fun onEdit(server: ServerUiModel)
    fun onDelete(server: ServerUiModel)
}

class ServerListAdapter(
    items: List<ServerUiModel>,
    private val actionListener: ServerActionListener
) : RecyclerView.Adapter<ServerListAdapter.ServerViewHolder>() {

    private val items = items.toMutableList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_server, parent, false)
        return ServerViewHolder(view)
    }

    override fun onBindViewHolder(holder: ServerViewHolder, position: Int) {
        holder.bind(items[position], actionListener)
    }

    override fun getItemCount(): Int = items.size

    fun prependItem(item: ServerUiModel) {
        items.add(0, item)
        notifyItemInserted(0)
    }

    fun updateItem(item: ServerUiModel) {
        val index = items.indexOfFirst { it.id == item.id }
        if (index == -1) return
        items[index] = item
        notifyItemChanged(index)
    }

    fun removeItem(item: ServerUiModel) {
        val index = items.indexOfFirst { it.id == item.id }
        if (index == -1) return
        items.removeAt(index)
        notifyItemRemoved(index)
    }

    class ServerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val coverImage: ImageView = itemView.findViewById(R.id.serverCoverImage)
        private val nameText: TextView = itemView.findViewById(R.id.serverNameText)
        private val userText: TextView = itemView.findViewById(R.id.serverUserText)
        private val statusText: TextView = itemView.findViewById(R.id.serverStatusText)
        private val menuButton: ImageButton = itemView.findViewById(R.id.serverMenuButton)

        fun bind(item: ServerUiModel, actionListener: ServerActionListener) {
            bindServerIcon(itemView.context, item)
            nameText.text = item.name
            userText.text = item.username
            statusText.text = item.status
            itemView.setDebouncedClickListener {
                actionListener.onOpen(item)
            }

            menuButton.setDebouncedClickListener { anchor ->
                val popupMenu = PopupMenu(anchor.context, anchor)
                popupMenu.menuInflater.inflate(R.menu.server_item_menu, popupMenu.menu)
                popupMenu.setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        R.id.action_relogin -> actionListener.onRelogin(item)
                        R.id.action_change_icon -> actionListener.onChangeIcon(item)
                        R.id.action_change_password -> actionListener.onChangePassword(item)
                        R.id.action_edit -> actionListener.onEdit(item)
                        R.id.action_delete -> actionListener.onDelete(item)
                    }
                    true
                }
                popupMenu.show()
            }
        }

        private fun bindServerIcon(context: Context, item: ServerUiModel) {
            val placeholder = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = context.resources.displayMetrics.density * 12
                setColor(Color.parseColor(item.iconStyle.fillColor))
            }

            coverImage.tag = null
            coverImage.background = placeholder
            coverImage.setImageDrawable(null)
            coverImage.clearColorFilter()

            val imageSource = item.customAvatarUri.ifBlank { item.avatarUrl }
            if (imageSource.isBlank()) {
                return
            }

            EmbyImageLoader.load(
                imageView = coverImage,
                url = imageSource,
                token = item.accessToken,
                onFailure = {
                    coverImage.background = placeholder
                    coverImage.setImageDrawable(null)
                }
            )
        }
    }
}
