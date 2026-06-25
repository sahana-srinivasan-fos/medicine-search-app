package com.rxora.app

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.rxora.app.api.RetrofitClient
import com.rxora.app.databinding.ActivityInvoiceBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class InvoiceActivity : AppCompatActivity() {
    private lateinit var binding: ActivityInvoiceBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInvoiceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val orderId = intent.getIntExtra("order_id", -1)
        if (orderId <= 0) {
            finish()
            return
        }

        loadOrder(orderId)

        binding.doneButton.setOnClickListener {
            finish()
        }
    }

    private fun loadOrder(orderId: Int) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val resp = withContext(Dispatchers.IO) { RetrofitClient.medicineApi.getOrder(orderId) }
                if (resp.isSuccessful && resp.body() != null) {
                    val order = resp.body()!!
                    binding.invoiceNumber.text = "Invoice #${order.id}"
                    binding.invoiceDate.text = order.created_at
                    binding.subtotalText.text = "Subtotal: ₹${String.format("%.2f", order.subtotal)}"
                    binding.gstText.text = "GST: ₹${String.format("%.2f", order.gst_amount)}"
                    binding.totalText.text = "Grand Total: ₹${String.format("%.2f", order.total_amount)}"

                    binding.itemsContainer.removeAllViews()
                    for (it in order.items) {
                        val row = TextView(this@InvoiceActivity)
                        row.setTextColor(resources.getColor(com.rxora.app.R.color.text_primary))
                        row.text = "${it.medicine_name} x${it.quantity} @ ₹${String.format("%.2f", it.unit_price)} = ₹${String.format("%.2f", it.line_total)}"
                        binding.itemsContainer.addView(row)
                    }

                } else {
                    Toast.makeText(this@InvoiceActivity, "Failed to load invoice", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@InvoiceActivity, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
