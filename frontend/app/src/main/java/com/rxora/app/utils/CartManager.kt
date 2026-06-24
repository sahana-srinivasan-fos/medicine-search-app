package com.rxora.app.utils

import com.rxora.app.models.CartItem

object CartManager {
    private val items = mutableListOf<CartItem>()

    fun addItem(item: CartItem) {
        val existing = items.find { it.medicineId == item.medicineId }
        if (existing != null) {
            existing.quantity += item.quantity
        } else {
            items.add(item)
        }
    }

    fun removeItem(medicineId: Int) {
        items.removeAll { it.medicineId == medicineId }
    }

    fun increaseQuantity(medicineId: Int) {
        items.find { it.medicineId == medicineId }?.let { it.quantity += 1 }
    }

    fun decreaseQuantity(medicineId: Int) {
        items.find { it.medicineId == medicineId }?.let { if (it.quantity > 1) it.quantity -= 1 else removeItem(medicineId) }
    }

    fun getItems(): List<CartItem> = items.toList()

    fun clear() {
        items.clear()
    }

    fun total(): Double = items.sumOf { it.quantity * it.price }
}
