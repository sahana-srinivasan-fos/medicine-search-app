package com.rxora.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.rxora.app.databinding.ItemCartBinding
import com.rxora.app.models.CartItem
import com.rxora.app.utils.CartManager

class CartAdapter(
    private val onQuantityChanged: () -> Unit
) : RecyclerView.Adapter<CartAdapter.CartViewHolder>() {

    private val items = mutableListOf<CartItem>()

    fun setData(newItems: List<CartItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CartViewHolder {
        val binding = ItemCartBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CartViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CartViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class CartViewHolder(private val binding: ItemCartBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: CartItem) {
            binding.cartItemName.text = item.medicineName
            binding.cartItemPrice.text = "₹${String.format("%.2f", item.price * item.quantity)}"
            binding.cartItemQuantity.text = "Qty: ${item.quantity}"

            binding.increaseQuantityButton.setOnClickListener {
                CartManager.increaseQuantity(item.medicineId)
                onQuantityChanged()
            }

            binding.decreaseQuantityButton.setOnClickListener {
                CartManager.decreaseQuantity(item.medicineId)
                onQuantityChanged()
            }

            binding.removeItemButton.setOnClickListener {
                CartManager.removeItem(item.medicineId)
                onQuantityChanged()
            }
        }
    }
}
