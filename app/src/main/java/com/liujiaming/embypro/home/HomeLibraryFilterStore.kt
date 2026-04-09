package com.liujiaming.embypro

import android.content.Context

class HomeLibraryFilterStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun loadExcludedLibraryIds(baseUrl: String, userId: String): Set<String> {
        return preferences.getStringSet(buildKey(baseUrl, userId), emptySet()).orEmpty()
    }

    fun setExcluded(baseUrl: String, userId: String, libraryId: String, excluded: Boolean) {
        val key = buildKey(baseUrl, userId)
        val updated = loadExcludedLibraryIds(baseUrl, userId).toMutableSet()
        if (excluded) {
            updated.add(libraryId)
        } else {
            updated.remove(libraryId)
        }
        preferences.edit().putStringSet(key, updated).apply()
    }

    private fun buildKey(baseUrl: String, userId: String): String {
        return "home_excluded::$baseUrl::$userId"
    }

    companion object {
        private const val PREF_NAME = "home_library_filter_store"
    }
}
