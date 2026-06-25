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

    @GET("/api/medicine/{medicine_id}")
    suspend fun getMedicineDetail(@Path("medicine_id") id: Int): Response<com.rxora.app.models.MedicineDetail>

    @GET("/api/correct")
    suspend fun correctMedicine(
        @Query("query") query: String
    ): Response<CorrectionResponse>

    @Multipart
    @POST("/api/voice-search")
    fun uploadVoice(
        @Part file: MultipartBody.Part
    ): Call<Map<String, String>>

    @POST("/api/orders/checkout")
    suspend fun checkoutOrder(@Body request: com.rxora.app.models.CheckoutRequest): Response<com.rxora.app.models.OrderResponse>

    @GET("/api/orders")
    suspend fun listOrders(): Response<List<com.rxora.app.models.OrderResponse>>

    @GET("/api/orders/{order_id}")
    suspend fun getOrder(@Path("order_id") orderId: Int): Response<com.rxora.app.models.OrderResponse>
}
