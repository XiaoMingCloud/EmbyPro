package com.liujiaming.embypro

import android.content.Context
import androidx.annotation.ColorRes
import androidx.annotation.StringRes

/**
 * Enum representing all available theme options in the application.
 * Each theme defines a key, label, background color resource, and system bar appearance.
 */
enum class GlobalThemeOption(
    val key: String,
    @StringRes val labelRes: Int,
    @ColorRes val backgroundRes: Int,
    val lightSystemBars: Boolean
) {
    LIGHT_GREEN("light_green", R.string.theme_light_green, R.color.theme_bg_light_green, true),
    PINK("pink", R.string.theme_pink, R.color.theme_bg_pink, true),
    WHITE("white", R.string.theme_white, R.color.theme_bg_white, true),
    BLACK("black", R.string.theme_black, R.color.theme_bg_black, false),
    LIGHT_PURPLE("light_purple", R.string.theme_light_purple, R.color.theme_bg_light_purple, true),
    LIGHT_BLUE("light_blue", R.string.theme_light_blue, R.color.theme_bg_light_blue, true),
    LIGHT_YELLOW("light_yellow", R.string.theme_light_yellow, R.color.theme_bg_light_yellow, true);

    companion object {
        /**
         * Creates a theme option from its key string.
         * Falls back to LIGHT_YELLOW if key is invalid or null.
         */
        fun fromKey(key: String?): GlobalThemeOption {
            return values().firstOrNull { it.key == key } ?: LIGHT_YELLOW
        }
    }
}

/**
 * Persistent storage for theme preferences.
 * Saves and loads the selected theme and custom background image URI.
 */
class GlobalThemeStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Loads the currently selected theme option.
     */
    fun loadTheme(): GlobalThemeOption {
        return GlobalThemeOption.fromKey(preferences.getString(KEY_THEME, GlobalThemeOption.LIGHT_YELLOW.key))
    }

    /**
     * Saves the selected theme option.
     */
    fun saveTheme(option: GlobalThemeOption) {
        preferences.edit().putString(KEY_THEME, option.key).apply()
    }

    /**
     * Loads the custom background image URI if set.
     */
    fun loadBackgroundImageUri(): String? {
        return preferences.getString(KEY_BACKGROUND_IMAGE_URI, null)
    }

    /**
     * Saves a custom background image URI.
     */
    fun saveBackgroundImageUri(uri: String) {
        preferences.edit().putString(KEY_BACKGROUND_IMAGE_URI, uri).apply()
    }

    /**
     * Clears the custom background image URI.
     */
    fun clearBackgroundImageUri() {
        preferences.edit().remove(KEY_BACKGROUND_IMAGE_URI).apply()
    }

    companion object {
        private const val PREFS_NAME = "global_theme_preferences"
        private const val KEY_THEME = "selected_theme"
        private const val KEY_BACKGROUND_IMAGE_URI = "background_image_uri"
    }
}
