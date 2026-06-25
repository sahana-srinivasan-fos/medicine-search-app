package com.rxora.app.ui

import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.rxora.app.databinding.ItemMedicineBinding
import com.rxora.app.models.Medicine
import com.rxora.app.MedicineDetailActivity
import com.rxora.app.models.CartItem
import com.rxora.app.utils.CartManager

class MedicineAdapter(
    private val onCartAdded: () -> Unit = {}
) : ListAdapter<Medicine, MedicineAdapter.MedicineViewHolder>(MedicineDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MedicineViewHolder {
        val binding = ItemMedicineBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MedicineViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MedicineViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class MedicineDiffCallback : DiffUtil.ItemCallback<Medicine>() {
        override fun areItemsTheSame(oldItem: Medicine, newItem: Medicine): Boolean {
            return oldItem.id == newItem.id
        }
        override fun areContentsTheSame(oldItem: Medicine, newItem: Medicine): Boolean {
            return oldItem == newItem
        }
    }

    inner class MedicineViewHolder(private val binding: ItemMedicineBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(medicine: Medicine) {
            binding.medicineName.text = medicine.name
            if (medicine.description.isNullOrEmpty()) {
                binding.medicineDescription.visibility = View.GONE
            } else {
                binding.medicineDescription.text = medicine.description
                binding.medicineDescription.visibility = View.VISIBLE
            }
            binding.medicineStock.text = "Stock: ${medicine.stock_quantity}"
            binding.medicinePrice.text = "₹${String.format("%.2f", medicine.selling_price)}"

            binding.root.setOnClickListener {
                val ctx = binding.root.context
                val intent = Intent(ctx, MedicineDetailActivity::class.java)
                intent.putExtra("medicine_id", medicine.id)
                ctx.startActivity(intent)
            }

            binding.addToCartButton.setOnClickListener {
                val item = CartItem(
                    medicineId = medicine.id,
                    medicineName = medicine.name,
                    quantity = 1,
                    price = medicine.selling_price
                )
                CartManager.addItem(item)
                Toast.makeText(binding.root.context, "Added to cart", Toast.LENGTH_SHORT).show()
                Log.d("MedicineAdapter", "MEDICINE_ADDED_TO_CART: ${medicine.name}")
                onCartAdded()
            }
        }
    }
}
