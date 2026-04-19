package com.liujiaming.embypro

import android.content.Context

class MusicLibraryAliasStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun loadAlias(baseUrl: String, userId: String, libraryId: String): String? {
        return preferences.getString(buildKey(baseUrl, userId, libraryId), null)?.trim()?.ifBlank { null }
    }

    fun saveAlias(baseUrl: String, userId: String, libraryId: String, alias: String) {
        preferences.edit().putString(buildKey(baseUrl, userId, libraryId), alias.trim()).apply()
    }

    fun clearAlias(baseUrl: String, userId: String, libraryId: String) {
        preferences.edit().remove(buildKey(baseUrl, userId, libraryId)).apply()
    }

    private fun buildKey(baseUrl: String, userId: String, libraryId: String): String {
        return "music_alias::$baseUrl::$userId::$libraryId"
    }

    companion object {
        private const val PREF_NAME = "music_library_alias_store"
    }
}
