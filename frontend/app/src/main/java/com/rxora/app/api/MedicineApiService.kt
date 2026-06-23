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

    @GET("/api/correct")
    suspend fun correctMedicine(
        @Query("query") query: String
    ): Response<CorrectionResponse>

    @Multipart
    @POST("/api/voice-search")
    fun uploadVoice(
        @Part file: MultipartBody.Part
    ): Call<Map<String, String>>
}
