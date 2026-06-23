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
