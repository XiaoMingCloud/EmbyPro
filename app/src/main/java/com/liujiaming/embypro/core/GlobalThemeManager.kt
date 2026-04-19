package com.liujiaming.embypro

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import com.google.android.material.card.MaterialCardView

object GlobalThemeManager {
    fun primaryTextColor(context: Context): Int {
        return resolvePrimaryTextColor(GlobalThemeStore(context).loadTheme())
    }

    fun secondaryTextColor(context: Context): Int {
        return resolveSecondaryTextColor(GlobalThemeStore(context).loadTheme())
    }

    fun cardBackgroundColor(context: Context): Int {
        return resolveCardBackgroundColor(GlobalThemeStore(context).loadTheme())
    }

    fun cardStrokeColor(context: Context): Int {
        return resolveCardStrokeColor(GlobalThemeStore(context).loadTheme())
    }

    fun apply(activity: Activity) {
        val themeStore = GlobalThemeStore(activity)
        val option = themeStore.loadTheme()
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val root = content.getChildAt(0) ?: return
        val backgroundColor = activity.getColor(option.backgroundRes)
        val backgroundDrawable = resolveBackgroundDrawable(activity, themeStore, backgroundColor)
        root.background = backgroundDrawable
        content.background = backgroundDrawable.constantState?.newDrawable()?.mutate() ?: backgroundDrawable
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = option.lightSystemBars
            isAppearanceLightNavigationBars = option.lightSystemBars
        }
        applyForegroundColors(root, option)
    }

    private fun resolveBackgroundDrawable(
        activity: Activity,
        themeStore: GlobalThemeStore,
        fallbackColor: Int
    ): Drawable {
        val backgroundUri = themeStore.loadBackgroundImageUri()
        if (backgroundUri.isNullOrBlank()) {
            return android.graphics.drawable.ColorDrawable(fallbackColor)
        }

        return runCatching {
            activity.contentResolver.openInputStream(android.net.Uri.parse(backgroundUri)).use { stream ->
                Drawable.createFromStream(stream, backgroundUri)
                    ?: android.graphics.drawable.ColorDrawable(fallbackColor)
            }
        }.getOrElse {
            themeStore.clearBackgroundImageUri()
            android.graphics.drawable.ColorDrawable(fallbackColor)
        }
    }

    fun currentThemeLabel(activity: Activity): String {
        val option = GlobalThemeStore(activity).loadTheme()
        return activity.getString(option.labelRes)
    }

    private fun applyForegroundColors(view: View, option: GlobalThemeOption) {
        val primaryColor = resolvePrimaryTextColor(option)
        val secondaryColor = resolveSecondaryTextColor(option)
        when (view) {
            is MaterialCardView -> {
                view.setCardBackgroundColor(resolveCardBackgroundColor(option))
                view.strokeColor = resolveCardStrokeColor(option)
            }

            is EditText -> {
                view.setTextColor(primaryColor)
                view.setHintTextColor(secondaryColor)
            }

            is Button -> Unit

            is TextView -> {
                val currentColor = view.currentTextColor
                val targetColor = if (isSecondaryTone(currentColor)) secondaryColor else primaryColor
                view.setTextColor(targetColor)
            }

            is ImageButton -> {
                view.imageTintList = ColorStateList.valueOf(primaryColor)
            }

            is ImageView -> {
                if (view.imageTintList != null) {
                    view.imageTintList = ColorStateList.valueOf(primaryColor)
                }
            }
        }

        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                applyForegroundColors(view.getChildAt(index), option)
            }
        }
    }

    private fun resolveCardBackgroundColor(option: GlobalThemeOption): Int {
        return if (option == GlobalThemeOption.BLACK) {
            Color.parseColor("#33FFFFFF")
        } else {
            ColorUtils.setAlphaComponent(Color.WHITE, 0x66)
        }
    }

    private fun resolveCardStrokeColor(option: GlobalThemeOption): Int {
        return if (option == GlobalThemeOption.BLACK) {
            Color.parseColor("#66FFFFFF")
        } else {
            Color.parseColor("#99FFFFFF")
        }
    }

    private fun resolvePrimaryTextColor(option: GlobalThemeOption): Int {
        return if (option.lightSystemBars) Color.parseColor("#FF272336") else Color.WHITE
    }

    private fun resolveSecondaryTextColor(option: GlobalThemeOption): Int {
        return if (option.lightSystemBars) Color.parseColor("#FF7E748E") else Color.parseColor("#CCFFFFFF")
    }

    private fun isSecondaryTone(color: Int): Boolean {
        val secondary = Color.parseColor("#FF7E748E")
        val primary = Color.parseColor("#FF272336")
        val distanceToSecondary = colorDistance(color, secondary)
        val distanceToPrimary = colorDistance(color, primary)
        return distanceToSecondary < distanceToPrimary
    }

    private fun colorDistance(first: Int, second: Int): Double {
        val r = Color.red(first) - Color.red(second)
        val g = Color.green(first) - Color.green(second)
        val b = Color.blue(first) - Color.blue(second)
        return kotlin.math.sqrt((r * r + g * g + b * b).toDouble())
    }

    private object GlobalThemeStoreColorCache {
        fun colorFor(option: GlobalThemeOption): Int {
            return when (option) {
                GlobalThemeOption.LIGHT_GREEN -> Color.parseColor("#FFF1FAEF")
                GlobalThemeOption.PINK -> Color.parseColor("#FFFFF0F6")
                GlobalThemeOption.WHITE -> Color.parseColor("#FFFFFFFF")
                GlobalThemeOption.BLACK -> Color.parseColor("#FF121212")
                GlobalThemeOption.LIGHT_PURPLE -> Color.parseColor("#FFF4EEFF")
                GlobalThemeOption.LIGHT_BLUE -> Color.parseColor("#FFEEF6FF")
                GlobalThemeOption.LIGHT_YELLOW -> Color.parseColor("#FFF7F4FB")
            }
        }
    }
}
