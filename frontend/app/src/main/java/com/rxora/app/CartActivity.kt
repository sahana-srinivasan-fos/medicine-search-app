package com.rxora.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.rxora.app.databinding.ActivityCartBinding
import com.rxora.app.utils.CartManager

class CartActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCartBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCartBinding.inflate(layoutInflater)
        setContentView(binding.root)

        refresh()
    }

    private fun refresh() {
        val items = CartManager.getItems()
        val sb = StringBuilder()
        for (i in items) {
            sb.append("${i.medicineName} x${i.quantity} - ₹${String.format("%.2f", i.price)}\n")
        }
        sb.append("\nTotal: ₹${String.format("%.2f", CartManager.total())}")
        binding.cartContents.text = sb.toString()
    }
}
