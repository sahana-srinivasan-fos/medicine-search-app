package com.rxora.app

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.rxora.app.api.RetrofitClient
import com.rxora.app.databinding.ActivityMedicineDetailBinding
import com.rxora.app.models.CartItem
import com.rxora.app.models.MedicineDetail
import com.rxora.app.utils.CartManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log
import android.widget.Toast


class MedicineDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMedicineDetailBinding

    private var medicineDetail: MedicineDetail? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMedicineDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val medicineId = intent?.getIntExtra("medicine_id", -1) ?: -1
        if (medicineId <= 0) {
            finish()
            return
        }

        binding.loadingProgressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            loadDetail(medicineId)
        }

        binding.addToCart.setOnClickListener {
            val detail = medicineDetail ?: return@setOnClickListener

            val item = CartItem(
                medicineId = detail.id,
                medicineName = detail.name,
                quantity = 1,
                price = detail.selling_price
            )

            CartManager.addItem(item)
            Toast.makeText(this, "Added to cart", Toast.LENGTH_SHORT).show()
            Log.d("MedicineDetailActivity", "MEDICINE_ADDED_TO_CART: ${detail.name}")
        }
    }

    private suspend fun loadDetail(id: Int) {
        try {
            val response = withContext(Dispatchers.IO) {
                RetrofitClient.medicineApi.getMedicineDetail(id)
            }

            if (response.isSuccessful && response.body() != null) {
                val d = response.body()!!

                medicineDetail = d

                binding.medName.text = d.name
                binding.medManufacturer.text = d.manufacturer
                binding.medCategory.text = d.category
                binding.medStock.text = "Stock: ${d.stock_quantity}"
                binding.medTablets.text = "Tablets/Strip: ${d.tablets_per_strip}"
                binding.medPrice.text = "₹${String.format("%.2f", d.selling_price)}"
                binding.medExpiry.text = "Expiry: ${d.expiry_date ?: "N/A"}"
            } else {
                binding.medName.text = "Not found"
            }
        } catch (e: Exception) {
            binding.medName.text = "Error loading"
        } finally {
            binding.loadingProgressBar.visibility = View.GONE
        }
    }
}
