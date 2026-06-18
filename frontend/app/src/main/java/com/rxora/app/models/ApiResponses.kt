package com.rxora.app.models

data class CategoryResponse(
    val categories: List<String>,
    val count: Int
)

data class PresetsResponse(
    val count: Int,
    val medicines: List<Medicine>
)

data class RecentSearchesResponse(
    val user_id: String,
    val count: Int,
    val searches: List<RecentSearch>
)

data class TrackSearchRequest(
    val user_id: String,
    val query: String,
    val category: String? = null,
    val voice_search: Boolean = false
)
