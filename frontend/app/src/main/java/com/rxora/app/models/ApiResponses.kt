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

data class RecentSearch(
    val query: String,
    val timestamp: String,
    val voice_search: Boolean
)

data class RecentSearchesResponse(
    val user_id: String,
    val count: Int,
    val searches: List<RecentSearch>
)

data class TrackSearchRequest(
    val user_id: String,
    val query: String,
    val voice_search: Boolean = false
)

data class TrackSearchResponse(
    val status: String,
    val user_id: String,
    val query: String,
    val timestamp: String
)

data class UserAnalytics(
    val user_id: String,
    val search_count: Int,
    val searches_today: Int,
    val last_search_at: String?
)

data class PerformanceStats(
    val redis: RedisStats? = null,
    val error: String? = null
)

data class RedisStats(
    val connected: Boolean,
    val used_memory: String,
    val hit_rate: Long,
    val total_commands: Long
)
