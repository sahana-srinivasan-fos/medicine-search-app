package com.rxora.app.models

data class SearchResponse(
    val medicines: List<Medicine>,
    val count: Int,
    val query: String,
    val category: String?,
    val source: String? = null,  // "database" or "redis_cache"
    val timing: Map<String, String>? = null  // performance metrics
)

data class PresetsResponse(
    val medicines: List<Medicine>,
    val count: Int
)

data class RecentSearch(
    val id: Int,
    val query: String,
    val category: String?,
    val searched_at: String,
    val voice_search: Boolean
)

data class RecentSearchesResponse(
    val user_id: String,
    val searches: List<RecentSearch>,
    val count: Int
)

data class TrackSearchRequest(
    val user_id: String,
    val query: String,
    val category: String? = null,
    val voice_search: Boolean = false
)

data class TrackSearchResponse(
    val status: String,
    val user_id: String,
    val query: String,
    val timestamp: String
)

data class PerformanceStats(
    val redis: RedisStats?,
    val database: DatabaseStats?,
    val error: String? = null
)

data class RedisStats(
    val connected: Boolean,
    val used_memory: String,
    val hit_rate: Long,
    val total_commands: Long
)

data class DatabaseStats(
    val total_medicines: Int,
    val cached_categories: Boolean,
    val cached_presets: Boolean
)
