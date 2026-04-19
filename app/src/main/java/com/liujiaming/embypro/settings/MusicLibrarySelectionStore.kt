package com.liujiaming.embypro

import android.content.Context

class MusicLibrarySelectionStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun loadSelectedLibraryId(baseUrl: String, userId: String): String? {
        return preferences.getString(buildKey(baseUrl, userId), null)?.ifBlank { null }
    }

    fun saveSelectedLibraryId(baseUrl: String, userId: String, libraryId: String) {
        preferences.edit().putString(buildKey(baseUrl, userId), libraryId).apply()
    }

    private fun buildKey(baseUrl: String, userId: String): String {
        return "music_selected::$baseUrl::$userId"
    }

    companion object {
        private const val PREF_NAME = "music_library_selection_store"
    }
}
