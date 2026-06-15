package com.rxora.app.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    
    // For emulator testing: use 10.0.2.2 (special IP that refers to host machine)
    // For physical device: use your machine's IP address (192.168.X.X)
    // After deployment: use your Render URL (https://your-app.onrender.com/)
    private const val BASE_URL = "http://10.0.2.2:8000/"
    
    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    
    val medicineApi: MedicineApiService by lazy {
        retrofit.create(MedicineApiService::class.java)
    }
}
