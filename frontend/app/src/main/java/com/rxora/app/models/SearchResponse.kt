package com.rxora.app.models

data class SearchResponse(
    val medicines: List<Medicine>,
    val total: Int,
    val query: String,
    val category: String?
)
