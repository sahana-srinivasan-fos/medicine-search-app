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
    val query: String,
    val isPreset: Boolean = false
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

data class CheckoutItem(
    val medicine_id: Int,
    val quantity: Int
)

data class CheckoutRequest(
    val items: List<CheckoutItem>,
    val discount: Double = 0.0,
    val gst_percent: Double = 0.0
)

data class OrderItemResponse(
    val medicine_id: Int,
    val medicine_name: String,
    val quantity: Int,
    val unit_price: Double,
    val line_total: Double
)

data class OrderResponse(
    val id: Int,
    val subtotal: Double,
    val discount: Double,
    val gst_percent: Double,
    val gst_amount: Double,
    val total_amount: Double,
    val created_at: String,
    val items: List<OrderItemResponse>
)

data class HealthResponse(
    val status: String,
    val medicines_loaded: Int,
    val timestamp: String
)
