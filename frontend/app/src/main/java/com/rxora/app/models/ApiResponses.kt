package com.rxora.app.models

data class SearchResponse(
    val query: String,
    val count: Int,
    val medicines: List<Medicine>,
    val source: String,
    val timing_ms: Float
)

data class PresetsResponse(
    val count: Int,
    val medicines: List<Medicine>,
    val source: String? = null
)

data class CorrectionResponse(
    val original: String,
    val corrected: String,
    val confidence: Int
)

data class RecentSearch(
    val query: String
)

data class MedicineDetail(
    val id: Int,
    val name: String,
    val manufacturer: String = "",
    val category: String = "",
    val stock_quantity: Int = 0,
    val tablets_per_strip: Int = 10,
    val selling_price: Double = 0.0,
    val expiry_date: String? = null
)

data class CartItem(
    val medicineId: Int,
    val medicineName: String,
    var quantity: Int,
    val price: Double
)
