package com.example.myapplication.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R

class CartItemAdapter(
    private val onQtyChanged: (name: String, quantity: String) -> Unit,
    private val onRemove: (name: String) -> Unit
) : RecyclerView.Adapter<CartItemAdapter.ViewHolder>() {

    private var items: List<MainViewModel.CartItem> = emptyList()

    fun submitList(newList: List<MainViewModel.CartItem>) {
        items = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_cart_medicine, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvCartMedName: TextView = view.findViewById(R.id.tvCartMedName)
        private val etCartQty: com.google.android.material.textfield.TextInputEditText = view.findViewById(R.id.etCartQty)
        private val btnRemoveCartItem: TextView = view.findViewById(R.id.btnRemoveCartItem)
        private var qtyTextWatcher: android.text.TextWatcher? = null

        fun bind(item: MainViewModel.CartItem) {
            tvCartMedName.text = item.name

            // Prevent duplicate text watchers on reuse
            qtyTextWatcher?.let { etCartQty.removeTextChangedListener(it) }
            etCartQty.setText(item.quantity)

            val watcher = object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    val qty = s?.toString()?.trim() ?: ""
                    if (qty.isNotEmpty() && qty != item.quantity) {
                        onQtyChanged(item.name, qty)
                    }
                }
            }
            etCartQty.addTextChangedListener(watcher)
            qtyTextWatcher = watcher

            btnRemoveCartItem.setOnClickListener {
                onRemove(item.name)
            }
        }
    }
}
