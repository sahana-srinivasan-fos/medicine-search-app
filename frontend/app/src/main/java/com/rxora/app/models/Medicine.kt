package com.rxora.app.models

data class Medicine(
    val id: Int,
    val name: String,
    val description: String = "",
    val is_preset: Boolean = false,
    val stock_quantity: Int = 0,
    val selling_price: Double = 0.0
)
