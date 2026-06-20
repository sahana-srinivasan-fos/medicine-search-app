package com.rxora.app

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.rxora.app.api.RetrofitClient
import com.rxora.app.databinding.ActivityMainBinding
import com.rxora.app.models.Medicine
import com.rxora.app.models.TrackSearchRequest
import com.rxora.app.ui.MedicineAdapter
import com.rxora.app.ui.RecentSearchAdapter
import com.rxora.app.utils.UserIdManager
import kotlinx.coroutines.*
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val medicineAdapter = MedicineAdapter()
    private var recentSearchAdapter: RecentSearchAdapter? = null
    private lateinit var userId: String
    
    private var searchJob: Job? = null
    private val DEBOUNCE_DELAY_MS = 300L

    companion object {
        private const val SPEECH_REQUEST_CODE = 100
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)

        userId = UserIdManager.getUserId(this)

        setupRecyclerViews()
        setupRealTimeSearch()
        setupButtons()

        loadInitialData()
    }

    private fun setupRealTimeSearch() {
        binding.searchEditText.doOnTextChanged { text, _, _, _ ->
            val query = text?.toString()?.trim() ?: ""
            searchJob?.cancel()

            if (query.isEmpty()) {
                showLoading(false)
                binding.statusText.visibility = View.VISIBLE
                binding.statusText.text = "Enter query to begin"
                binding.performanceText.text = ""
                medicineAdapter.submitList(emptyList())
                return@doOnTextChanged
            }

            searchJob = lifecycleScope.launch {
                delay(DEBOUNCE_DELAY_MS)
                performSearch(query)
            }
        }
    }

    private fun loadInitialData() {
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.medicineApi.getRecentSearches(userId)
                }
                if (response.isSuccessful) {
                    response.body()?.searches?.let {
                        if (it.isNotEmpty()) {
                            binding.recentTitle.visibility = View.VISIBLE
                            recentSearchAdapter?.setData(it)
                        }
                    }
                }
                binding.searchEditText.requestFocus()
            } catch (e: Exception) {
                Log.e(TAG, "Init error", e)
            }
        }
    }

    private fun setupRecyclerViews() {
        binding.medicineRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = medicineAdapter
        }

        recentSearchAdapter = RecentSearchAdapter(onSearchClick = { query ->
            binding.searchEditText.setText(query)
            binding.searchEditText.setSelection(query.length)
        })
        binding.recentSearchesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = recentSearchAdapter
        }
    }

    private fun setupButtons() {
        binding.voiceButton.setOnClickListener {
            startVoiceSearch()
        }
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
                
                trackSearch(query, voiceSearch = false)
            } else {
                binding.statusText.visibility = View.VISIBLE
                binding.statusText.text = "Backend services unavailable"
            }
        } catch (e: Exception) {
            if (e !is CancellationException) {
                binding.statusText.visibility = View.VISIBLE
                binding.statusText.text = "Network connection failed"
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
                val spokenText = results[0]
                binding.searchEditText.setText(spokenText)
                binding.searchEditText.setSelection(spokenText.length)
                // Use a separate job for tracking voice search to avoid cancellation issues
                lifecycleScope.launch {
                    trackSearch(spokenText, voiceSearch = true)
                }
            }
        }
    }

    private suspend fun trackSearch(query: String, voiceSearch: Boolean) {
        try {
            val request = TrackSearchRequest(userId, query, voiceSearch)
            val response = withContext(Dispatchers.IO) {
                RetrofitClient.medicineApi.trackSearch(request)
            }
            
            if (response.isSuccessful) {
                val recentResponse = withContext(Dispatchers.IO) {
                    RetrofitClient.medicineApi.getRecentSearches(userId)
                }
                if (recentResponse.isSuccessful) {
                    recentResponse.body()?.searches?.let { searches ->
                        binding.recentTitle.visibility = if (searches.isNotEmpty()) View.VISIBLE else View.GONE
                        recentSearchAdapter?.setData(searches)
                    }
                }
            }
        } catch (e: Exception) {
            if (e !is CancellationException) {
                Log.e(TAG, "Tracking failed", e)
            }
        }
    }

    private fun showLoading(show: Boolean) {
        binding.loadingProgressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        searchJob?.cancel()
    }
}
