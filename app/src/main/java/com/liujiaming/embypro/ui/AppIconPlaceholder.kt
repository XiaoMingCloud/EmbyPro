package com.liujiaming.embypro

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.ImageView

object AppIconPlaceholder {
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
