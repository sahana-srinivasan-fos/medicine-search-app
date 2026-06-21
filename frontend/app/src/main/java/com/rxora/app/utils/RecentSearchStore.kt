package com.rxora.app.utils

import android.content.Context
import org.json.JSONArray

object RecentSearchStore {
    private const val PREFS_NAME = "recent_searches"
    private const val KEY_SEARCHES = "recent_searches"
    private const val MAX_RECENT_SEARCHES = 10

    fun loadRecentSearches(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rawJson = prefs.getString(KEY_SEARCHES, "[]") ?: "[]"
        return try {
            val array = JSONArray(rawJson)
            List(array.length()) { index -> array.optString(index).trim() }
                .filter { it.isNotBlank() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addRecentSearch(context: Context, query: String): List<String> {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) return loadRecentSearches(context)

        val recentSearches = loadRecentSearches(context).toMutableList()
        recentSearches.remove(trimmedQuery)
        recentSearches.add(0, trimmedQuery)

        if (recentSearches.size > MAX_RECENT_SEARCHES) {
            recentSearches.subList(MAX_RECENT_SEARCHES, recentSearches.size).clear()
        }

        saveRecentSearches(context, recentSearches)
        return recentSearches
    }

    private fun saveRecentSearches(context: Context, searches: List<String>) {
        val array = JSONArray()
        searches.forEach { array.put(it) }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SEARCHES, array.toString()).apply()
    }
}
