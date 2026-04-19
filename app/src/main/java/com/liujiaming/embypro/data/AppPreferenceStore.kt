package com.liujiaming.embypro

import android.content.Context
import org.json.JSONArray

class AppPreferenceStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun loadSearchHistory(): List<String> {
        val raw = preferences.getString(KEY_SEARCH_HISTORY, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        val jsonArray = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return buildList {
            for (index in 0 until jsonArray.length()) {
                val value = jsonArray.optString(index).trim()
                if (value.isNotBlank()) add(value)
            }
        }
    }

    fun saveSearchQuery(query: String) {
        val normalized = query.trim()
        if (normalized.isBlank()) return
        val updated = buildList {
            add(normalized)
            loadSearchHistory()
                .filterNot { it.equals(normalized, ignoreCase = true) }
                .take(MAX_SEARCH_HISTORY_ITEMS - 1)
                .forEach { add(it) }
        }
        preferences.edit()
            .putString(KEY_SEARCH_HISTORY, JSONArray(updated).toString())
            .apply()
    }

    fun deleteSearchQuery(query: String) {
        preferences.edit()
            .putString(
                KEY_SEARCH_HISTORY,
                JSONArray(loadSearchHistory().filterNot { it == query }).toString()
            )
            .apply()
    }

    fun clearSearchHistory() {
        preferences.edit()
            .putString(KEY_SEARCH_HISTORY, JSONArray().toString())
            .apply()
    }

    fun loadExcludedHomeLibraryIds(baseUrl: String, userId: String): Set<String> {
        return preferences.getStringSet(buildScopedKey(KEY_HOME_EXCLUDED_LIBRARIES, baseUrl, userId), emptySet())
            .orEmpty()
    }

    fun setHomeLibraryExcluded(baseUrl: String, userId: String, libraryId: String, excluded: Boolean) {
        val key = buildScopedKey(KEY_HOME_EXCLUDED_LIBRARIES, baseUrl, userId)
        val updated = loadExcludedHomeLibraryIds(baseUrl, userId).toMutableSet()
        if (excluded) {
            updated.add(libraryId)
        } else {
            updated.remove(libraryId)
        }
        preferences.edit().putStringSet(key, updated).apply()
    }

    fun loadMusicLibraryAlias(baseUrl: String, userId: String, libraryId: String): String? {
        return preferences.getString(
            buildScopedKey(KEY_MUSIC_LIBRARY_ALIAS, baseUrl, userId, libraryId),
            null
        )?.trim()?.ifBlank { null }
    }

    fun saveMusicLibraryAlias(baseUrl: String, userId: String, libraryId: String, alias: String) {
        preferences.edit()
            .putString(buildScopedKey(KEY_MUSIC_LIBRARY_ALIAS, baseUrl, userId, libraryId), alias.trim())
            .apply()
    }

    fun clearMusicLibraryAlias(baseUrl: String, userId: String, libraryId: String) {
        preferences.edit()
            .remove(buildScopedKey(KEY_MUSIC_LIBRARY_ALIAS, baseUrl, userId, libraryId))
            .apply()
    }

    fun loadSelectedMusicLibraryId(baseUrl: String, userId: String): String? {
        return preferences.getString(
            buildScopedKey(KEY_SELECTED_MUSIC_LIBRARY, baseUrl, userId),
            null
        )?.ifBlank { null }
    }

    fun saveSelectedMusicLibraryId(baseUrl: String, userId: String, libraryId: String) {
        preferences.edit()
            .putString(buildScopedKey(KEY_SELECTED_MUSIC_LIBRARY, baseUrl, userId), libraryId)
            .apply()
    }

    private fun buildScopedKey(prefix: String, vararg parts: String): String {
        return buildString {
            append(prefix)
            parts.forEach { part ->
                append("::")
                append(part)
            }
        }
    }

    companion object {
        private const val PREF_NAME = "emby_app_preferences"
        private const val KEY_SEARCH_HISTORY = "search_history"
        private const val KEY_HOME_EXCLUDED_LIBRARIES = "home_excluded_libraries"
        private const val KEY_MUSIC_LIBRARY_ALIAS = "music_library_alias"
        private const val KEY_SELECTED_MUSIC_LIBRARY = "selected_music_library"
        private const val MAX_SEARCH_HISTORY_ITEMS = 12
    }
}
