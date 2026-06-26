package com.example.myapplication.whisper

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * Persists and retrieves user-confirmed medicine mappings for "self-learning".
 * Maps a spoken keyword (e.g. "niner bact") to a confirmed medicine name (e.g. "Nitrobact 100").
 */
class CorrectionRegistry(context: Context) {

    private val file = File(context.filesDir, "user_corrections.json")
    private val corrections = mutableMapOf<String, String>()

    init {
        load()
    }

    /**
     * Records a new mapping. If the keyword already exists, it is updated.
     */
    fun addCorrection(keyword: String, medicineName: String) {
        val kw = keyword.lowercase().trim()
        if (kw.isEmpty()) return
        
        corrections[kw] = medicineName
        save()
        Log.d("CorrectionRegistry", "Learned: '$kw' -> '$medicineName'")
    }

    /**
     * Returns the confirmed medicine name for a keyword, or null if not learned yet.
     */
    fun getCorrection(keyword: String): String? {
        return corrections[keyword.lowercase().trim()]
    }

    private fun load() {
        if (!file.exists()) return
        try {
            val json = JSONObject(file.readText())
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                corrections[key] = json.getString(key)
            }
            Log.d("CorrectionRegistry", "Loaded ${corrections.size} corrections")
        } catch (e: Exception) {
            Log.e("CorrectionRegistry", "Failed to load corrections", e)
        }
    }

    private fun save() {
        try {
            val json = JSONObject()
            for ((k, v) in corrections) {
                json.put(k, v)
            }
            file.writeText(json.toString())
        } catch (e: Exception) {
            Log.e("CorrectionRegistry", "Failed to save corrections", e)
        }
    }
}
