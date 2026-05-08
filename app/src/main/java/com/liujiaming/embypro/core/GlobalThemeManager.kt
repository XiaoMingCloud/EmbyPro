package com.liujiaming.embypro

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import java.io.File
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

/**
 * Manages global theme application across activities.
 * Handles background colors, text colors, card styling, and custom background images.
 */
object GlobalThemeManager {
    /**
     * Returns the primary text color based on the current theme.
     */
    fun primaryTextColor(context: Context): Int {
        return resolvePrimaryTextColor(GlobalThemeStore(context).loadTheme())
    }

    /**
     * Returns the secondary text color based on the current theme.
     */
    fun secondaryTextColor(context: Context): Int {
        return resolveSecondaryTextColor(GlobalThemeStore(context).loadTheme())
    }

    /**
     * Returns the card background color based on the current theme.
     */
    fun cardBackgroundColor(context: Context): Int {
        return resolveCardBackgroundColor(GlobalThemeStore(context).loadTheme())
    }

    /**
     * Returns the card stroke (border) color based on the current theme.
     */
    fun cardStrokeColor(context: Context): Int {
        return resolveCardStrokeColor(GlobalThemeStore(context).loadTheme())
    }

    /**
     * Applies the current theme to the activity's root view hierarchy.
     * Sets background, configures system bars, and applies foreground colors to all child views.
     *
     * @param activity The activity to theme
     */
    fun apply(activity: Activity) {
        val themeStore = GlobalThemeStore(activity)
        val option = themeStore.loadTheme()
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val root = content.getChildAt(0) ?: return
        val backgroundColor = Color.WHITE
        val backgroundDrawable = resolveBackgroundDrawable(activity, themeStore, backgroundColor)
        root.background = backgroundDrawable
        content.background = backgroundDrawable.constantState?.newDrawable()?.mutate() ?: backgroundDrawable
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = option.lightSystemBars
            isAppearanceLightNavigationBars = option.lightSystemBars
        }
        applyForegroundColors(root, option)
    }

    /**
     * Resolves the background drawable for the activity.
     * Returns custom background image if set, otherwise falls back to theme color.
     */
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
            val drawable = if (backgroundUri.startsWith("content://")) {
                activity.contentResolver.openInputStream(android.net.Uri.parse(backgroundUri)).use { stream ->
                    Drawable.createFromStream(stream, backgroundUri)
                }
            } else {
                File(backgroundUri).inputStream().use { stream ->
                    Drawable.createFromStream(stream, backgroundUri)
                }
            }
            drawable ?: android.graphics.drawable.ColorDrawable(fallbackColor)
        }.getOrElse {
            themeStore.clearBackgroundImageUri()
            android.graphics.drawable.ColorDrawable(fallbackColor)
        }
    }

    /**
     * Returns the display name of the current theme.
     */
    fun currentThemeLabel(activity: Activity): String {
        val option = GlobalThemeStore(activity).loadTheme()
        return activity.getString(option.labelRes)
    }

    /**
     * Recursively applies foreground colors (text, tint, card colors) to a view and its children.
     * Intelligently detects secondary text tones to apply appropriate color.
     */
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
                val targetColor = if (isSecondaryTone(currentColor, primaryColor, secondaryColor)) secondaryColor else primaryColor
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

    /**
     * Resolves card background color based on theme option.
     * Black theme uses darker semi-transparent white, others use lighter transparency.
     */
    private fun resolveCardBackgroundColor(@Suppress("UNUSED_PARAMETER") option: GlobalThemeOption): Int {
        return ColorUtils.setAlphaComponent(Color.WHITE, 0x66)
    }

    /**
     * Resolves card stroke (border) color based on theme option.
     */
    private fun resolveCardStrokeColor(@Suppress("UNUSED_PARAMETER") option: GlobalThemeOption): Int {
        return Color.parseColor("#3D1B1B1F")
    }

    /**
     * Resolves primary text color. Dark color for light system bars, white for dark bars.
     */
    private fun resolvePrimaryTextColor(option: GlobalThemeOption): Int {
        return GlobalThemeStoreColorCache.colorFor(option)
    }

    /**
     * Resolves secondary text color. Muted color for light system bars, semi-transparent white for dark.
     */
    private fun resolveSecondaryTextColor(option: GlobalThemeOption): Int {
        val primary = resolvePrimaryTextColor(option)
        return ColorUtils.setAlphaComponent(primary, 0xB8)
    }

    /**
     * Determines if a color is closer to the secondary tone than primary tone.
     * Uses RGB color distance calculation.
     */
    private fun isSecondaryTone(color: Int, primaryColor: Int, secondaryColor: Int): Boolean {
        val distanceToSecondary = colorDistance(color, secondaryColor)
        val distanceToPrimary = colorDistance(color, primaryColor)
        return distanceToSecondary < distanceToPrimary
    }

    /**
     * Calculates Euclidean distance between two RGB colors.
     */
    private fun colorDistance(first: Int, second: Int): Double {
        val r = Color.red(first) - Color.red(second)
        val g = Color.green(first) - Color.green(second)
        val b = Color.blue(first) - Color.blue(second)
        return kotlin.math.sqrt((r * r + g * g + b * b).toDouble())
    }

    /**
     * Cache object for theme-specific background colors.
     * Provides pre-defined color values for each theme option.
     */
    private object GlobalThemeStoreColorCache {
        fun colorFor(option: GlobalThemeOption): Int {
            return when (option) {
                GlobalThemeOption.LIGHT_GREEN -> Color.parseColor("#FF2F7D32")
                GlobalThemeOption.PINK -> Color.parseColor("#FFB03A72")
                GlobalThemeOption.WHITE -> Color.parseColor("#FF6E7385")
                GlobalThemeOption.BLACK -> Color.parseColor("#FF272336")
                GlobalThemeOption.LIGHT_PURPLE -> Color.parseColor("#FF6B4FB3")
                GlobalThemeOption.LIGHT_BLUE -> Color.parseColor("#FF2F6FD6")
                GlobalThemeOption.LIGHT_YELLOW -> Color.parseColor("#FF9A6A00")
            }
        }
    }
}
