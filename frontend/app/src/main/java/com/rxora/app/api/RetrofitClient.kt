package com.rxora.app.api

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    
    // For emulator testing: use 10.0.2.2 (special IP that refers to host machine)
    // For physical device: use your machine's IP address (192.168.X.X)
    // After deployment: use your Render URL (https://your-app.onrender.com/)
    private const val BASE_URL = "https://rxora.onrender.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS) // Short timeout for local dev
        .readTimeout(3, TimeUnit.SECONDS)
        .build()
    
    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    
    val medicineApi: MedicineApiService by lazy {
        retrofit.create(MedicineApiService::class.java)
    }
}
