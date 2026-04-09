package com.liujiaming.embypro

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View

object LibraryVisualHelper {
    fun buildPlaceholder(itemView: View, colorHex: String, cornerDp: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = itemView.resources.displayMetrics.density * cornerDp
            orientation = GradientDrawable.Orientation.LEFT_RIGHT
            colors = intArrayOf(
                Color.parseColor(colorHex),
                lightenColor(colorHex)
            )
        }
    }

    private fun lightenColor(colorHex: String): Int {
        val base = Color.parseColor(colorHex)
        val red = (Color.red(base) + 50).coerceAtMost(255)
        val green = (Color.green(base) + 50).coerceAtMost(255)
        val blue = (Color.blue(base) + 50).coerceAtMost(255)
        return Color.rgb(red, green, blue)
    }
}
