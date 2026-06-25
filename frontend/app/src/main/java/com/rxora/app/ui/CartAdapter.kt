package com.rxora.app.ui

import android.app.AlertDialog
import android.text.InputType
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.EditText
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.rxora.app.databinding.ItemCartBinding
import com.rxora.app.models.CartItem
import com.rxora.app.utils.CartManager

class CartAdapter(
    private val onQuantityChanged: () -> Unit
) : ListAdapter<CartItem, CartAdapter.CartViewHolder>(CartDiffCallback()) {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).medicineId.toLong()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CartViewHolder {
        val binding = ItemCartBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CartViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CartViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

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

            binding.cartItemQuantity.setOnClickListener {
                // allow typing a number directly
                val ctx = binding.root.context
                val input = EditText(ctx)
                input.inputType = InputType.TYPE_CLASS_NUMBER
                input.setText(item.quantity.toString())

                AlertDialog.Builder(ctx)
                    .setTitle("Set quantity")
                    .setView(input)
                    .setPositiveButton("OK") { _, _ ->
                        val text = input.text?.toString()?.trim().orEmpty()
                        val newQty = text.toIntOrNull() ?: item.quantity
                        if (newQty <= 0) {
                            CartManager.removeItem(item.medicineId)
                        } else {
                            CartManager.setQuantity(item.medicineId, newQty)
                        }
                        onQuantityChanged()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    fun setData(newItems: List<CartItem>) {
        submitList(newItems)
    }

    class CartDiffCallback : DiffUtil.ItemCallback<CartItem>() {
        override fun areItemsTheSame(oldItem: CartItem, newItem: CartItem): Boolean {
            return oldItem.medicineId == newItem.medicineId
        }

        override fun areContentsTheSame(oldItem: CartItem, newItem: CartItem): Boolean {
            return oldItem == newItem
        }
    }
}
