package com.rxora.app

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.rxora.app.databinding.ActivityCartBinding
import com.rxora.app.ui.CartAdapter
import com.rxora.app.utils.CartManager

class CartActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCartBinding
    private lateinit var cartAdapter: CartAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCartBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cartAdapter = CartAdapter {
            refresh()
        }

        binding.cartItemsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@CartActivity)
            adapter = cartAdapter
        }

        binding.checkoutButton.setOnClickListener {
            val intent = android.content.Intent(this, CheckoutActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val items = CartManager.getItems()
        cartAdapter.setData(items)

        binding.cartEmptyState.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        binding.cartItemsRecyclerView.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        binding.cartTotalText.text = "Total: ₹${String.format("%.2f", CartManager.total())}"
        binding.checkoutButton.isEnabled = items.isNotEmpty()
    }
}
