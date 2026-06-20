package com.rxora.app.models

data class Medicine(
    val id: Int,
    val name: String,
    val description: String = "",
    val is_preset: Boolean = false
)
