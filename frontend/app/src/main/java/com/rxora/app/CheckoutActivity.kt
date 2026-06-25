package com.rxora.app

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.rxora.app.api.RetrofitClient
import com.rxora.app.databinding.ActivityCheckoutBinding
import com.rxora.app.models.CheckoutItem
import com.rxora.app.models.CheckoutRequest
import com.rxora.app.models.CartItem
import com.rxora.app.utils.CartManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CheckoutActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCheckoutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCheckoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.discountEdit.setText("0")
        val gstPref = getSharedPreferences("rxora_prefs", MODE_PRIVATE)
        val gstDefault = gstPref.getFloat("gst_percent", 0f).toDouble()
        binding.gstEdit.setText(gstDefault.toString())

        refreshTotals()

        binding.cancelButton.setOnClickListener {
            finish()
        }

        binding.completeSaleButton.setOnClickListener {
            confirmCheckout()
        }
    }

    private fun confirmCheckout() {
        val total = CartManager.total()
        if (total <= 0.0) {
            Toast.makeText(this, "Cart is empty", Toast.LENGTH_SHORT).show()
            return
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Complete Sale?")
            .setMessage("Total: ₹${String.format("%.2f", total)}")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Confirm") { _, _ ->
                performCheckout()
            }
            .show()
    }

    override fun onResume() {
        super.onResume()
        refreshTotals()
    }

    private fun refreshTotals() {
        val items = CartManager.getItems()
        val subtotal = items.sumOf { it.quantity * it.price }
        binding.subtotalText.text = "Subtotal: ₹${String.format("%.2f", subtotal)}"
        val discount = binding.discountEdit.text.toString().toDoubleOrNull() ?: 0.0
        val gstPercent = binding.gstEdit.text.toString().toDoubleOrNull() ?: 0.0
        val gstAmount = ((subtotal - discount) * gstPercent) / 100.0
        val total = subtotal - discount + gstAmount
        binding.gstText.text = "GST: ₹${String.format("%.2f", gstAmount)}"
        binding.totalText.text = "Grand Total: ₹${String.format("%.2f", total)}"
    }

    private fun performCheckout() {
        val items = CartManager.getItems()
        if (items.isEmpty()) {
            Toast.makeText(this, "Cart is empty", Toast.LENGTH_SHORT).show()
            return
        }

        val checkoutItems = items.map { CheckoutItem(medicine_id = it.medicineId, quantity = it.quantity) }
        val discount = binding.discountEdit.text.toString().toDoubleOrNull() ?: 0.0
        val gstPercent = binding.gstEdit.text.toString().toDoubleOrNull() ?: 0.0

        val request = CheckoutRequest(items = checkoutItems, discount = discount, gst_percent = gstPercent)

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val resp = withContext(Dispatchers.IO) { RetrofitClient.medicineApi.checkoutOrder(request) }
                if (resp.isSuccessful && resp.body() != null) {
                    val order = resp.body()!!
                    // clear cart and navigate to invoice
                    CartManager.clear()
                    val intent = android.content.Intent(this@CheckoutActivity, InvoiceActivity::class.java)
                    intent.putExtra("order_id", order.id)
                    startActivity(intent)
                    finish()
                } else {
                    val msg = resp.errorBody()?.string() ?: "Checkout failed"
                    Toast.makeText(this@CheckoutActivity, msg, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@CheckoutActivity, "Checkout error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
