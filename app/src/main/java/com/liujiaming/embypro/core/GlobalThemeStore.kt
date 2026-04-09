package com.liujiaming.embypro

import android.content.Context
import androidx.annotation.ColorRes
import androidx.annotation.StringRes

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
        fun fromKey(key: String?): GlobalThemeOption {
            return values().firstOrNull { it.key == key } ?: LIGHT_YELLOW
        }
    }
}

class GlobalThemeStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadTheme(): GlobalThemeOption {
        return GlobalThemeOption.fromKey(preferences.getString(KEY_THEME, GlobalThemeOption.LIGHT_YELLOW.key))
    }

    fun saveTheme(option: GlobalThemeOption) {
        preferences.edit().putString(KEY_THEME, option.key).apply()
    }

    companion object {
        private const val PREFS_NAME = "global_theme_preferences"
        private const val KEY_THEME = "selected_theme"
    }
}
