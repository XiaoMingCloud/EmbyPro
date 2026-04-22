package com.liujiaming.embypro

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View

/**
 * Utility for creating visual elements for library items.
 * Provides gradient placeholders and color manipulation helpers.
 */
object LibraryVisualHelper {
    /**
     * Builds a gradient placeholder drawable for library items.
     * Creates a left-to-right gradient from base color to lightened version.
     *
     * @param itemView Parent view for resource access
     * @param colorHex Base color in hex format
     * @param cornerDp Corner radius in dp
     * @return GradientDrawable with rounded corners
     */
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

    /**
     * Lightens a color by adding 50 to each RGB component.
     * Caps values at 255 to prevent overflow.
     */
    private fun lightenColor(colorHex: String): Int {
        val base = Color.parseColor(colorHex)
        val red = (Color.red(base) + 50).coerceAtMost(255)
        val green = (Color.green(base) + 50).coerceAtMost(255)
        val blue = (Color.blue(base) + 50).coerceAtMost(255)
        return Color.rgb(red, green, blue)
    }
}
