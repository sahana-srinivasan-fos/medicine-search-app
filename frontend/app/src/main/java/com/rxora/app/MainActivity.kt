package com.rxora.app

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.rxora.app.api.RetrofitClient
import com.rxora.app.databinding.ActivityMainBinding
import com.rxora.app.models.CorrectionResponse
import com.rxora.app.models.Medicine
import com.rxora.app.models.RecentSearch
import com.rxora.app.ui.MedicineAdapter
import com.rxora.app.ui.RecentSearchAdapter
import com.rxora.app.utils.CartManager
import com.rxora.app.utils.RecentSearchStore
import kotlinx.coroutines.*
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var medicineAdapter: MedicineAdapter
    private var recentSearchAdapter: RecentSearchAdapter? = null
    private var presetSearchAdapter: RecentSearchAdapter? = null

    private var searchJob: Job? = null
    private var searchStartTime: Long = 0L
    private val DEBOUNCE_DELAY_MS = 300L
    private var suppressSearchTextChange = false

    companion object {
        private const val SPEECH_REQUEST_CODE = 100
        private const val TAG = "MainActivity"
        private const val CORRECTION_CONFIDENCE_THRESHOLD = 75
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)

        setupRecyclerViews()
        setupRealTimeSearch()
        setupButtons()

        loadInitialData()
    }

    override fun onResume() {
        super.onResume()
        updateCartBadge()
    }

    private fun setupRealTimeSearch() {
        binding.searchEditText.doOnTextChanged { text, _, _, _ ->
            if (suppressSearchTextChange) return@doOnTextChanged

            val query = text?.toString()?.trim() ?: ""
            searchJob?.cancel()

            if (query.isEmpty()) {
                showLoading(false)
                binding.statusText.visibility = View.VISIBLE
                binding.statusText.text = "Enter query to begin"
                binding.performanceText.text = ""
                medicineAdapter.submitList(emptyList())
                binding.presetSection.visibility = View.VISIBLE
                binding.recentTitle.visibility = if (recentSearchAdapter?.itemCount ?: 0 > 0) View.VISIBLE else View.GONE
                return@doOnTextChanged
            }

            binding.presetSection.visibility = View.GONE
            binding.statusText.visibility = View.VISIBLE
            binding.statusText.text = "Searching..."
            binding.performanceText.text = ""
            searchStartTime = System.nanoTime()

            searchJob = lifecycleScope.launch {
                delay(DEBOUNCE_DELAY_MS)
                performSearch(query)
            }
        }

        binding.searchEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = binding.searchEditText.text?.toString()?.trim() ?: ""
                if (query.isNotEmpty()) {
                    searchJob?.cancel()
                    searchStartTime = System.nanoTime()
                    lifecycleScope.launch {
                        performSearch(query)
                    }
                }
                true
            } else {
                false
            }
        }
    }

    private fun loadInitialData() {
        updateCartBadge()

        val searches = RecentSearchStore.loadRecentSearches(this)
        binding.recentTitle.visibility = View.VISIBLE
        if (searches.isNotEmpty()) {
            binding.recentEmptyState.visibility = View.GONE
            recentSearchAdapter?.setData(searches.map { RecentSearch(query = it) })
        } else {
            binding.recentEmptyState.visibility = View.VISIBLE
            recentSearchAdapter?.setData(emptyList())
        }

        binding.presetLoading.visibility = View.VISIBLE
        binding.presetSection.visibility = View.VISIBLE

        lifecycleScope.launch {
            loadPresetMedicines()
        }

        binding.searchEditText.requestFocus()
    }

    private fun setupRecyclerViews() {
        medicineAdapter = MedicineAdapter(onCartAdded = {
            updateCartBadge()
        })

        binding.medicineRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = medicineAdapter
        }

        recentSearchAdapter = RecentSearchAdapter(onSearchClick = { query ->
            val trimmed = query.trim()
            suppressSearchTextChange = true
            binding.searchEditText.setText(trimmed)
            binding.searchEditText.setSelection(trimmed.length)
            suppressSearchTextChange = false
            lifecycleScope.launch {
                performSearch(trimmed)
            }
        })
        binding.recentSearchesRecyclerView.apply {
            layoutManager = GridLayoutManager(this@MainActivity, 2)
            adapter = recentSearchAdapter
            setHasFixedSize(true)
        }

        presetSearchAdapter = RecentSearchAdapter(onSearchClick = { query ->
            val trimmed = query.trim()
            Log.d(TAG, "PRESET_CLICKED: $trimmed")
            suppressSearchTextChange = true
            binding.searchEditText.setText(trimmed)
            binding.searchEditText.setSelection(trimmed.length)
            suppressSearchTextChange = false
            lifecycleScope.launch {
                performSearch(trimmed)
            }
        })
        binding.presetRecyclerView.apply {
            layoutManager = GridLayoutManager(this@MainActivity, 2)
            adapter = presetSearchAdapter
            setHasFixedSize(true)
        }
    }

    private fun setupButtons() {
        binding.voiceButton.setOnClickListener {
            startVoiceSearch()
        }

        binding.cartButton.setOnClickListener {
            startActivity(Intent(this, CartActivity::class.java))
        }
    }

    private fun updateCartBadge() {
        val count = CartManager.getItems().sumOf { it.quantity }
        if (count > 0) {
            binding.cartBadge.visibility = View.VISIBLE
            binding.cartBadge.text = count.toString()
        } else {
            binding.cartBadge.visibility = View.GONE
        }
        Log.d(TAG, "CART_TOTAL_UPDATED: $count")
    }

    private suspend fun performSearch(query: String) {
        showLoading(true)
        binding.statusText.visibility = View.GONE

        try {
            val response = withContext(Dispatchers.IO) {
                RetrofitClient.medicineApi.searchMedicines(query, limit = 20)
            }

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val medicines = body.medicines

                medicineAdapter.submitList(medicines)

                if (medicines.isEmpty()) {
                    binding.statusText.visibility = View.VISIBLE
                    binding.statusText.text = "No records found for '$query'"
                } else {
                    binding.statusText.visibility = View.GONE
                }

                binding.performanceText.text = "Source: ${body.source} | Latency: ${body.timing_ms}ms"

                Log.d(TAG, "SEARCH_EXECUTED: $query")
                saveRecentSearch(query)
            } else {
                binding.statusText.visibility = View.VISIBLE
                binding.statusText.text = "Backend services unavailable"
            }
        } catch (e: Exception) {
            if (e !is CancellationException) {
                binding.statusText.visibility = View.VISIBLE
                binding.statusText.text = e.localizedMessage ?: "An unknown error occurred"
            }
        } finally {
            showLoading(false)
        }
    }

    private fun startVoiceSearch() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Identify medicine...")
        }
        try {
            startActivityForResult(intent, SPEECH_REQUEST_CODE)
        } catch (e: Exception) {
            Toast.makeText(this, "Voice capability not found", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SPEECH_REQUEST_CODE && resultCode == RESULT_OK) {
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!results.isNullOrEmpty()) {
                val spokenText = results[0]?.trim().orEmpty()
                if (spokenText.isNotEmpty()) {
                    binding.searchEditText.setText(spokenText)
                    binding.searchEditText.setSelection(spokenText.length)
                    lifecycleScope.launch {
                        performVoiceSearch(spokenText)
                    }
                }
            }
        }
    }

    private fun saveRecentSearch(query: String) {
        val searches = RecentSearchStore.addRecentSearch(this, query)
        binding.recentTitle.visibility = if (searches.isNotEmpty()) View.VISIBLE else View.GONE
        binding.recentEmptyState.visibility = if (searches.isEmpty()) View.VISIBLE else View.GONE
        recentSearchAdapter?.setData(searches.map { RecentSearch(query = it) })
        Log.d(TAG, "RECENT_SEARCH_SAVED: $query")
    }

    private suspend fun loadPresetMedicines() {
        try {
            val response = withContext(Dispatchers.IO) {
                RetrofitClient.medicineApi.getPresetMedicines()
            }
            if (response.isSuccessful && response.body() != null) {
                val items = response.body()!!.medicines.map { RecentSearch(query = it.name, isPreset = true) }
                if (items.isNotEmpty()) {
                    presetSearchAdapter?.setData(items)
                    binding.presetSection.visibility = View.VISIBLE
                } else {
                    binding.presetSection.visibility = View.GONE
                }
            } else {
                binding.presetSection.visibility = View.GONE
            }
        } catch (e: Exception) {
            Log.w(TAG, "Preset load failed", e)
            binding.presetSection.visibility = View.GONE
        } finally {
            binding.presetLoading.visibility = View.GONE
        }
    }

    private fun showLoading(show: Boolean) {
        binding.loadingProgressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showCorrectionMessage(message: String?) {
        if (!message.isNullOrEmpty()) {
            binding.correctionText.visibility = View.VISIBLE
            binding.correctionText.text = message
        } else {
            binding.correctionText.visibility = View.GONE
            binding.correctionText.text = ""
        }
    }

    private suspend fun performVoiceSearch(spokenText: String) {
        showLoading(true)
        binding.statusText.visibility = View.VISIBLE
        binding.statusText.text = "Correcting voice input..."
        showCorrectionMessage(null)

        try {
            val response = withContext(Dispatchers.IO) {
                RetrofitClient.medicineApi.correctMedicine(spokenText)
            }

            val searchQuery = if (response.isSuccessful && response.body() != null) {
                val correction = response.body()!!
                if (correction.confidence > CORRECTION_CONFIDENCE_THRESHOLD && correction.corrected != spokenText) {
                    binding.searchEditText.setText(correction.corrected)
                    binding.searchEditText.setSelection(correction.corrected.length)
                    showCorrectionMessage("Corrected '${correction.original}' → '${correction.corrected}'")
                    correction.corrected
                } else {
                    showCorrectionMessage(null)
                    spokenText
                }
            } else {
                showCorrectionMessage(null)
                spokenText
            }

            performSearch(searchQuery)
        } catch (e: Exception) {
            showCorrectionMessage(null)
            binding.statusText.visibility = View.VISIBLE
            binding.statusText.text = "Voice correction unavailable"
            performSearch(spokenText)
        } finally {
            showLoading(false)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        searchJob?.cancel()
    }
}
