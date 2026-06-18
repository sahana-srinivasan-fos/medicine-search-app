package com.rxora.app.utils

import android.content.Context
import java.util.*

/**
 * Manages persistent user ID storage
 * User ID is generated once and persists across app launches
 */
object UserIdManager {
    
    private const val PREFS_NAME = "user_prefs"
    private const val KEY_USER_ID = "user_id"
    
    /**
     * Get or create a persistent user ID
     * @param context Android context
     * @return Unique user ID that persists across app restarts
     */
    fun getUserId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        var userId = prefs.getString(KEY_USER_ID, null)
        
        if (userId == null) {
            // Generate new UUID-based user ID
            userId = "user_${UUID.randomUUID()}"
            
            // Store it for future use
            prefs.edit().putString(KEY_USER_ID, userId).apply()
        }
        
        return userId
    }
    
    /**
     * Reset user ID (for testing or logout)
     * @param context Android context
     */
    fun resetUserId(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_USER_ID).apply()
    }
    
    /**
     * Clear all user preferences
     * @param context Android context
     */
    fun clearAll(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }
}
