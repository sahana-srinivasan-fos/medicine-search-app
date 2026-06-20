package com.rxora.app.api

import com.rxora.app.models.*
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.*

interface MedicineApiService {

    // ✅ CRITICAL FIX: Changed from @POST to @GET
    // ✅ Changed parameter from "q" to "query"
    // ✅ Changed limit default from 50 to 20
    @GET("/api/search")
    fun searchMedicines(
        @Query("query") query: String,
        @Query("limit") limit: Int = 20
    ): Call<SearchResponse>

    @GET("/api/voice-search")
    fun voiceSearch(
        @Query("text") text: String,
        @Query("user_id") userId: String
    ): Call<SearchResponse>

    @GET("/api/medicines/presets")
    suspend fun getPresetMedicines(): Response<PresetsResponse>

    @GET("/api/recent-searches")
    suspend fun getRecentSearches(
        @Query("user_id") userId: String
    ): Response<RecentSearchesResponse>

    @POST("/api/track-search")
    fun trackSearch(
        @Query("user_id") userId: String,
        @Query("query") query: String,
        @Query("voice_search") voiceSearch: Boolean = false
    ): Call<TrackSearchResponse>
}
