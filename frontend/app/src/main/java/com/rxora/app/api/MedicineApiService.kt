package com.rxora.app.api

import com.rxora.app.models.*
import okhttp3.MultipartBody
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.*

interface MedicineApiService {
    
    @GET("/api/search")
    suspend fun searchMedicines(
        @Query("query") query: String,
        @Query("limit") limit: Int = 20
    ): Response<SearchResponse>
    
    @GET("/api/medicines/presets")
    suspend fun getPresetMedicines(): Response<PresetsResponse>
    
    @GET("/api/recent-searches")
    suspend fun getRecentSearches(
        @Query("user_id") userId: String,
        @Query("limit") limit: Int = 10
    ): Response<RecentSearchesResponse>
    
    @POST("/api/track-search")
    suspend fun trackSearch(
        @Body request: TrackSearchRequest
    ): Response<TrackSearchResponse>

    @GET("/api/user-analytics")
    suspend fun getUserAnalytics(
        @Query("user_id") userId: String
    ): Response<UserAnalytics>

    @Multipart
    @POST("/api/voice-search")
    fun uploadVoice(
        @Part file: MultipartBody.Part
    ): Call<Map<String, String>>

    @GET("/api/performance-stats")
    suspend fun getPerformanceStats(): Response<PerformanceStats>
}
