package com.rxora.app.models

data class RecentSearch(
    val id: Int,
    val query: String,
    val category: String?,
    val searched_at: String,
    val voice_search: Boolean
)
