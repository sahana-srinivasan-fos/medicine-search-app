package com.rxora.app.models

data class Medicine(
    val id: Int,
    val name: String,
    val category: String,
    val description: String? = null,
    val is_preset: Boolean = false
)
