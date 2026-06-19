package com.rxora.app

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.rxora.app.api.RetrofitClient
import com.rxora.app.databinding.ActivityMainBinding
import com.rxora.app.models.Medicine
import com.rxora.app.ui.MedicineAdapter
import com.rxora.app.ui.RecentSearchAdapter
import com.rxora.app.utils.UserIdManager
import kotlinx.coroutines.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val medicineAdapter = MedicineAdapter()
    private var recentSearchAdapter: RecentSearchAdapter? = null
    private lateinit var userId: String
    private var currentQuery = ""
    private var currentCategory: String? = null
    
    // ✅ DEBOUNCE MECHANISM
    private var searchJob: Job? = null
    private val DEBOUNCE_DELAY_MS = 300L

    companion object {
        private const val SPEECH_REQUEST_CODE = 100
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate started")
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ✅ FIX #1: INSTANT KEYBOARD AUTO-FOCUS
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        
        userId = UserIdManager.getUserId(this)
        Log.d(TAG, "User ID: $userId")

        setupRecyclerViews()
        setupRealTimeSearch()  // ✅ NEW: Real-time search with debounce
        setupButtons()
        
        loadInitialData()
    }

    // ✅ FIX #2: REAL-TIME DEBOUNCED SEARCH (300ms)
    private fun setupRealTimeSearch() {
        binding.searchEditText.doOnTextChanged { text, _, _, _ ->
            val query = text?.toString()?.trim() ?: ""
            
            // Cancel previous search job
            searchJob?.cancel()
            
            if (query.isEmpty()) {
                showLoading(false)
                lifecycleScope.launch {
                    medicineAdapter.setData(emptyList())
                }
                return@doOnTextChanged
            }
            
            // Start debounced search
            searchJob = lifecycleScope.launch {
                try {
                    delay(DEBOUNCE_DELAY_MS)  // Wait 300ms
                    
                    val currentText = binding.searchEditText.text.toString().trim()
                    if (currentText != query) {
                        Log.d(TAG, "Query changed, skipping search")
                        return@launch
                    }
                    
                    Log.d(TAG, "Executing search for: '$query'")
                    performSearch(query, currentCategory)
                    
                } catch (e: CancellationException) {
                    Log.d(TAG, "Search cancelled (user typing)")
                }
            }
        }
    }

    private fun loadInitialData() {
        lifecycleScope.launch {
            Log.d(TAG, "Data loading started")
            
            try {
                supervisorScope {
                    val presetsDeferred = async(Dispatchers.IO) { 
                        try { RetrofitClient.medicineApi.getPresetMedicines() } 
                        catch (e: Exception) { 
                            Log.e(TAG, "Error fetching presets", e)
                            null 
                        }
                    }
                    val categoriesDeferred = async(Dispatchers.IO) { 
                        try { RetrofitClient.medicineApi.getCategories() } 
                        catch (e: Exception) { 
                            Log.e(TAG, "Error fetching categories", e)
                            null 
                        }
                    }
                    val recentDeferred = async(Dispatchers.IO) { 
                        try { RetrofitClient.medicineApi.getRecentSearches(userId) } 
                        catch (e: Exception) { 
                            Log.e(TAG, "Error fetching recent searches", e)
                            null 
                        }
                    }
                    
                    val presetsResponse = presetsDeferred.await()
                    val categoriesResponse = categoriesDeferred.await()
                    val recentResponse = recentDeferred.await()
                    
                    withContext(Dispatchers.Main) {
                        presetsResponse?.body()?.medicines?.let { 
                            medicineAdapter.setData(it) 
                        }
                        
                        categoriesResponse?.body()?.let { categories ->
                            val categoryNames = mutableListOf("All Categories")
                            categoryNames.addAll(categories)
                            val adapter = ArrayAdapter(
                                this@MainActivity, 
                                android.R.layout.simple_spinner_item, 
                                categoryNames
                            )
                            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                            binding.categorySpinner.adapter = adapter
                        }
                        
                        recentResponse?.body()?.searches?.let { 
                            recentSearchAdapter?.setData(it) 
                        }
                        
                        // ✅ REQUEST FOCUS ON SEARCH
                        binding.searchEditText.requestFocus()
                    }
                    
                    Log.d(TAG, "Initial data update complete")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Global error in loadInitialData", e)
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
        })
        binding.recentSearchesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = recentSearchAdapter
        }
    }

    private fun setupButtons() {
        binding.searchButton.setOnClickListener {
            val query = binding.searchEditText.text.toString().trim()
            if (query.isNotEmpty()) {
                performSearch(query, currentCategory)
            } else {
                Toast.makeText(this, "Please enter a medicine name", Toast.LENGTH_SHORT).show()
            }
        }

        binding.voiceButton.setOnClickListener {
            startVoiceSearch()
        }

        binding.categorySpinner.setOnItemSelectedListener(
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    currentCategory = if (position == 0) null else parent?.getItemAtPosition(position).toString()
                    val query = binding.searchEditText.text.toString().trim()
                    if (query.isNotEmpty()) {
                        performSearch(query, currentCategory)
                    }
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {
                    currentCategory = null
                }
            }
        )
    }

    private fun performSearch(query: String, category: String?) {
        currentQuery = query
        showLoading(true)
        val startTime = System.currentTimeMillis()

        RetrofitClient.medicineApi.searchMedicines(query, category, limit = 20)
            .enqueue(object : Callback<com.rxora.app.models.SearchResponse> {
                override fun onResponse(
                    call: Call<com.rxora.app.models.SearchResponse>,
                    response: Response<com.rxora.app.models.SearchResponse>
                ) {
                    val elapsed = System.currentTimeMillis() - startTime
                    Log.d(TAG, "Search completed in ${elapsed}ms")
                    showLoading(false)
                    
                    if (response.isSuccessful && response.body() != null) {
                        val medicines = response.body()!!.medicines
                        lifecycleScope.launch {
                            medicineAdapter.setData(medicines)
                        }
                        trackSearch(query, category, voiceSearch = false)
                    } else {
                        Toast.makeText(this@MainActivity, "Search failed", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<com.rxora.app.models.SearchResponse>, t: Throwable) {
                    showLoading(false)
                    Toast.makeText(this@MainActivity, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun startVoiceSearch() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say medicine name...")
        }
        try {
            startActivityForResult(intent, SPEECH_REQUEST_CODE)
        } catch (e: Exception) {
            Toast.makeText(this, "Voice input not supported", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SPEECH_REQUEST_CODE && resultCode == RESULT_OK) {
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!results.isNullOrEmpty()) {
                binding.searchEditText.setText(results[0])
            }
        }
    }

    private fun trackSearch(query: String, category: String?, voiceSearch: Boolean) {
        RetrofitClient.medicineApi.trackSearch(userId, query, category, voiceSearch)
            .enqueue(object : Callback<com.rxora.app.models.TrackSearchResponse> {
                override fun onResponse(call: Call<com.rxora.app.models.TrackSearchResponse>, response: Response<com.rxora.app.models.TrackSearchResponse>) {
                    if (response.isSuccessful) {
                        lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                val recentResponse = RetrofitClient.medicineApi.getRecentSearches(userId)
                                if (recentResponse.isSuccessful) {
                                    recentResponse.body()?.searches?.let { searches ->
                                        withContext(Dispatchers.Main) {
                                            recentSearchAdapter?.setData(searches)
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error fetching recent", e)
                            }
                        }
                    }
                }

                override fun onFailure(call: Call<com.rxora.app.models.TrackSearchResponse>, t: Throwable) {
                    // Silent fail for analytics
                }
            })
    }

    private fun showLoading(show: Boolean) {
        binding.loadingProgressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        searchJob?.cancel()
    }
}
