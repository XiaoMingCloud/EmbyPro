package com.liujiaming.embypro

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.PopupWindow
import android.widget.TextView

internal data class PlayerMoreMenuState(
    val isContinuousPlayEnabled: Boolean,
    val currentSpeedIndex: Int
)

internal enum class PlayerMoreMenuAction {
    TOGGLE_CONTINUOUS,
    ROTATE_VIDEO,
    PICTURE_IN_PICTURE,
    SPEED_1X,
    SPEED_1_25X,
    SPEED_1_5X,
    SPEED_2X,
    DELETE_VIDEO
}

internal fun Context.showPlayerMoreMenuPopup(
    anchor: View,
    state: PlayerMoreMenuState,
    onAction: (PlayerMoreMenuAction) -> Unit
) {
    // Reusable popup style note:
    // For lightweight choice cards in this app, prefer a custom PopupWindow with
    // `bg_home_poster_glass_card` plus rounded option rows instead of system PopupMenu,
    // so the overlay stays visually consistent with the Library glass-card language.
    val contentView = LayoutInflater.from(this).inflate(R.layout.popup_player_more_menu, null)
    val popupWindow = PopupWindow(
        contentView,
        android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
        android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
        true
    )

    popupWindow.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    popupWindow.isOutsideTouchable = true
    popupWindow.elevation = popupDp(14).toFloat()

    bindMenuItem(
        contentView.findViewById(R.id.playerMenuContinuousOption),
        getString(if (state.isContinuousPlayEnabled) R.string.continuous_on else R.string.continuous_off),
        state.isContinuousPlayEnabled
    ) {
        popupWindow.dismiss()
        onAction(PlayerMoreMenuAction.TOGGLE_CONTINUOUS)
    }
    bindMenuItem(
        contentView.findViewById(R.id.playerMenuRotateOption),
        getString(R.string.rotate_video),
        false
    ) {
        popupWindow.dismiss()
        onAction(PlayerMoreMenuAction.ROTATE_VIDEO)
    }
    bindMenuItem(
        contentView.findViewById(R.id.playerMenuPipOption),
        getString(R.string.picture_in_picture),
        false
    ) {
        popupWindow.dismiss()
        onAction(PlayerMoreMenuAction.PICTURE_IN_PICTURE)
    }
    bindMenuItem(
        contentView.findViewById(R.id.playerMenuSpeed1Option),
        getString(R.string.speed_1_0),
        state.currentSpeedIndex == 0
    ) {
        popupWindow.dismiss()
        onAction(PlayerMoreMenuAction.SPEED_1X)
    }
    bindMenuItem(
        contentView.findViewById(R.id.playerMenuSpeed125Option),
        getString(R.string.speed_1_25),
        state.currentSpeedIndex == 1
    ) {
        popupWindow.dismiss()
        onAction(PlayerMoreMenuAction.SPEED_1_25X)
    }
    bindMenuItem(
        contentView.findViewById(R.id.playerMenuSpeed15Option),
        getString(R.string.speed_1_5),
        state.currentSpeedIndex == 2
    ) {
        popupWindow.dismiss()
        onAction(PlayerMoreMenuAction.SPEED_1_5X)
    }
    bindMenuItem(
        contentView.findViewById(R.id.playerMenuSpeed2Option),
        getString(R.string.speed_2_0),
        state.currentSpeedIndex == 3
    ) {
        popupWindow.dismiss()
        onAction(PlayerMoreMenuAction.SPEED_2X)
    }
    bindMenuItem(
        contentView.findViewById(R.id.playerMenuDeleteOption),
        getString(R.string.action_delete_video),
        false
    ) {
        popupWindow.dismiss()
        onAction(PlayerMoreMenuAction.DELETE_VIDEO)
    }

    popupWindow.showAsDropDown(anchor, 0, popupDp(10), Gravity.END)
}

private fun bindMenuItem(
    itemView: View,
    title: String,
    activated: Boolean,
    onClick: () -> Unit
) {
    itemView.isActivated = activated
    itemView.findViewById<TextView>(R.id.playerMenuOptionTitle).text = title
    itemView.setDebouncedClickListener { onClick() }
}

private fun Context.popupDp(value: Int): Int {
    return (resources.displayMetrics.density * value).toInt()
}
