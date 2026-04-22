package com.liujiaming.embypro

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.ImageView

/**
 * Utility for applying placeholder drawable to ImageViews.
 * Shows app icon with rounded corner background when image fails to load.
 */
object AppIconPlaceholder {
    /**
     * Applies placeholder to an ImageView with rounded corners.
     *
     * @param imageView Target ImageView
     * @param cornerRadiusDp Corner radius in dp (default 14dp)
     * @param backgroundColor Background color (default semi-transparent gray)
     */
    fun apply(
        imageView: ImageView,
        cornerRadiusDp: Float = 14f,
        backgroundColor: Int = Color.parseColor("#1F6B7A90")
    ) {
        imageView.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = imageView.resources.displayMetrics.density * cornerRadiusDp
            setColor(backgroundColor)
        }
        imageView.setImageResource(R.drawable.ic_launcher_foreground)
        imageView.clearColorFilter()
        imageView.imageTintList = null
    }
}
