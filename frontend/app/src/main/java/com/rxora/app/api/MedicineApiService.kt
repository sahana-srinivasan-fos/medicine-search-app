package com.rxora.app.api

import com.rxora.app.models.*
import retrofit2.Call
import retrofit2.http.*

interface MedicineApiService {
    
    // Search medicines by query
    @GET("/api/search")
    fun searchMedicines(
        @Query("q") query: String,
        @Query("category") category: String? = null,
        @Query("limit") limit: Int = 50
    ): Call<SearchResponse>
    
    // Voice search
    @GET("/api/voice-search")
    fun voiceSearch(
        @Query("text") text: String,
        @Query("user_id") userId: String
    ): Call<SearchResponse>
    
    // Get all categories
    @GET("/api/categories")
    fun getCategories(): Call<List<Category>>
    
    // Get preset medicines
    @GET("/api/presets")
    fun getPresetMedicines(): Call<List<Medicine>>
    
    // Get recent searches
    @GET("/api/recent-searches")
    fun getRecentSearches(
        @Query("user_id") userId: String
    ): Call<List<RecentSearch>>
    
    // Track a search for analytics
    @POST("/api/track-search")
    fun trackSearch(
        @Query("user_id") userId: String,
        @Query("query") query: String,
        @Query("category") category: String? = null,
        @Query("voice_search") voiceSearch: Boolean = false
    ): Call<Unit>
}
