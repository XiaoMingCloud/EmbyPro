package com.liujiaming.embypro

import android.content.Context
import org.json.JSONArray

class SearchHistoryStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun loadHistory(): List<String> {
        val raw = preferences.getString(KEY_HISTORY, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        val jsonArray = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return buildList {
            for (index in 0 until jsonArray.length()) {
                val value = jsonArray.optString(index).trim()
                if (value.isNotBlank()) add(value)
            }
        }
    }

    fun saveQuery(query: String) {
        val normalized = query.trim()
        if (normalized.isBlank()) return
        val updated = buildList {
            add(normalized)
            loadHistory()
                .filterNot { it.equals(normalized, ignoreCase = true) }
                .take(MAX_ITEMS - 1)
                .forEach { add(it) }
        }
        persist(updated)
    }

    fun deleteQuery(query: String) {
        persist(loadHistory().filterNot { it == query })
    }

    fun clearAll() {
        persist(emptyList())
    }

    private fun persist(items: List<String>) {
        preferences.edit()
            .putString(KEY_HISTORY, JSONArray(items).toString())
            .apply()
    }

    companion object {
        private const val PREF_NAME = "search_history_store"
        private const val KEY_HISTORY = "history"
        private const val MAX_ITEMS = 12
    }
}
