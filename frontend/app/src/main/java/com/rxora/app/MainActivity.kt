package com.rxora.app

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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
    private lateinit var recentSearchAdapter: RecentSearchAdapter
    private lateinit var presetSearchAdapter: RecentSearchAdapter

    private var searchJob: Job? = null
    private var searchStartTime: Long = 0L
    private val DEBOUNCE_DELAY_MS = 250L
    private var suppressSearchTextChange = false
    private var lastSearchQuery: String = ""
    private var presetSearchItems: List<RecentSearch> = emptyList()
    private var currentUiState: HomeUiState = HomeUiState.Home

    private val voiceResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!results.isNullOrEmpty()) {
                val spokenText = results[0]?.trim().orEmpty()
                Log.d(TAG, "VOICE_SEARCH_RESULT: $spokenText")
                if (spokenText.isNotEmpty()) {
                    setSearchTextAndSearch(spokenText)
                }
            }
        }
    }

    private sealed class HomeUiState {
        object Home : HomeUiState()
        object Searching : HomeUiState()
        object Results : HomeUiState()
        object EmptyResults : HomeUiState()
        data class NetworkError(val message: String) : HomeUiState()
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val CORRECTION_CONFIDENCE_THRESHOLD = 75
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)

        setupRecyclerViews()
        setupSearchListeners()
        setupButtons()
        loadHomeData()

        // Fire a background /health ping to warm Render backend (no UI impact)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                RetrofitClient.medicineApi.health()
                Log.d(TAG, "Health ping sent")
            } catch (e: Exception) {
                Log.w(TAG, "Health ping failed", e)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateCartBadge()
        if (currentUiState == HomeUiState.Results) {
            updateUiState(HomeUiState.Results)
        }
    }

    private fun setupSearchListeners() {
        binding.searchEditText.doOnTextChanged { text, _, _, _ ->
            if (suppressSearchTextChange) return@doOnTextChanged

            val query = text?.toString()?.trim() ?: ""
            if (query.isEmpty() || query.length < 3) {
                searchJob?.cancel()
                lastSearchQuery = ""
                updateUiState(HomeUiState.Home)
                return@doOnTextChanged
            }

            if (query != lastSearchQuery) {
                scheduleSearch(query)
            }
        }

        binding.searchEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = binding.searchEditText.text?.toString()?.trim() ?: ""
                if (query.isNotEmpty() && query.length >= 3) {
                    searchJob?.cancel()
                    executeSearch(query)
                }
                true
            } else {
                false
            }
        }
    }

    private fun loadHomeData() {
        updateCartBadge()
        binding.presetLoading.visibility = View.VISIBLE
        updateUiState(HomeUiState.Home)

        recentSearchAdapter.submitList(loadRecentSearches())
        lifecycleScope.launch {
            presetSearchItems = loadPresetSearches()
            presetSearchAdapter.submitList(presetSearchItems)
            binding.presetLoading.visibility = View.GONE
            updateUiState(currentUiState)
        }
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
            Log.d(TAG, "RECENT_CLICKED: $query")
            setSearchTextAndSearch(query)
        })

        binding.recentSearchesRecyclerView.apply {
            layoutManager = GridLayoutManager(this@MainActivity, 2)
            adapter = recentSearchAdapter
            setHasFixedSize(true)
        }

        presetSearchAdapter = RecentSearchAdapter(onSearchClick = { query ->
            Log.d(TAG, "PRESET_CLICKED: $query")
            setSearchTextAndSearch(query)
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

        binding.retryButton.setOnClickListener {
            if (lastSearchQuery.isNotBlank()) {
                executeSearch(lastSearchQuery)
            } else {
                updateUiState(HomeUiState.Home)
            }
        }
    }

    private fun setSearchTextAndSearch(query: String) {
        suppressSearchTextChange = true
        binding.searchEditText.setText(query)
        binding.searchEditText.setSelection(query.length)
        suppressSearchTextChange = false
        // Bypass debounce for preset/recent/voice/enter actions
        searchJob?.cancel()
        executeSearch(query)
    }

    private fun scheduleSearch(query: String) {
        searchJob?.cancel()
        searchJob = lifecycleScope.launch {
            delay(DEBOUNCE_DELAY_MS)
            executeSearch(query)
        }
    }

    private fun executeSearch(query: String) {
        if (query.length < 3) {
            updateUiState(HomeUiState.Home)
            return
        }

        lastSearchQuery = query
        updateUiState(HomeUiState.Searching)
        // Cancel any previous running search
        searchJob?.cancel()
        searchJob = lifecycleScope.launch {
            performSearch(query)
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
        Log.d(TAG, "CART_UPDATED: $count")
    }

    private suspend fun performSearch(query: String) {
        lastSearchQuery = query
        Log.d(TAG, "SEARCH_STARTED: $query")
        try {
            val response = withContext(Dispatchers.IO) {
                RetrofitClient.medicineApi.searchMedicines(query, limit = 20)
            }

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val medicines = body.medicines
                medicineAdapter.submitList(medicines)

                if (medicines.isEmpty()) {
                    updateUiState(HomeUiState.EmptyResults)
                } else {
                    updateUiState(HomeUiState.Results)
                }

                binding.performanceText.text = "Source: ${body.source} | Latency: ${body.timing_ms}ms"
                Log.d(TAG, "SEARCH_COMPLETED: $query | count=${medicines.size}")
                saveRecentSearch(query)
            } else {
                updateUiState(HomeUiState.NetworkError("Backend services unavailable"))
            }
        } catch (e: Exception) {
            if (e !is CancellationException) {
                updateUiState(HomeUiState.NetworkError(e.localizedMessage ?: "An unknown error occurred"))
            }
        }
    }

    private fun startVoiceSearch() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Identify medicine...")
        }
        try {
            voiceResultLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Voice capability not found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveRecentSearch(query: String) {
        val searches = RecentSearchStore.addRecentSearch(this, query)
        recentSearchAdapter.submitList(searches.map { RecentSearch(query = it) })
        updateRecentState(searches)
        Log.d(TAG, "RECENT_SEARCH_SAVED: $query")
    }

    private fun updateRecentState(searches: List<String>) {
        binding.recentTitle.visibility = if (searches.isNotEmpty()) View.VISIBLE else View.GONE
        binding.recentEmptyState.visibility = if (searches.isEmpty()) View.VISIBLE else View.GONE
    }

    private suspend fun loadPresetSearches(): List<RecentSearch> {
        return try {
            val response = withContext(Dispatchers.IO) {
                RetrofitClient.medicineApi.getPresetMedicines()
            }
            if (response.isSuccessful && response.body() != null) {
                response.body()!!.medicines.map { RecentSearch(query = it.name, isPreset = true) }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Preset load failed", e)
            emptyList()
        }
    }

    private fun updateUiState(state: HomeUiState) {
        currentUiState = state
        binding.statusText.visibility = View.GONE
        binding.loadingProgressBar.visibility = View.GONE
        binding.retryButton.visibility = View.GONE
        binding.recentSection.visibility = View.GONE
        binding.presetSection.visibility = View.GONE
        binding.medicineRecyclerView.visibility = View.GONE
        binding.performanceText.visibility = View.GONE
        binding.correctionText.visibility = View.GONE

        when (state) {
            HomeUiState.Home -> {
                binding.recentSection.visibility = View.VISIBLE
                if (presetSearchItems.isNotEmpty()) {
                    binding.presetSection.visibility = View.VISIBLE
                }
                binding.statusText.visibility = View.VISIBLE
                binding.statusText.text = "Enter query to begin"
                medicineAdapter.submitList(emptyList())
            }
            HomeUiState.Searching -> {
                binding.loadingProgressBar.visibility = View.VISIBLE
                binding.statusText.visibility = View.VISIBLE
                binding.statusText.text = "Searching..."
            }
            HomeUiState.Results -> {
                binding.medicineRecyclerView.visibility = View.VISIBLE
                binding.performanceText.visibility = View.VISIBLE
            }
            HomeUiState.EmptyResults -> {
                binding.statusText.visibility = View.VISIBLE
                binding.statusText.text = "No medicines found"
            }
            is HomeUiState.NetworkError -> {
                binding.statusText.visibility = View.VISIBLE
                binding.statusText.text = state.message
                binding.retryButton.visibility = View.VISIBLE
            }
        }
    }

    private fun loadRecentSearches(): List<RecentSearch> {
        val recentWords = RecentSearchStore.loadRecentSearches(this)
        updateRecentState(recentWords)
        return recentWords.map { RecentSearch(query = it) }
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
        updateUiState(HomeUiState.Searching)
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
            updateUiState(HomeUiState.NetworkError("Voice correction unavailable"))
            if (spokenText.isNotBlank()) {
                performSearch(spokenText)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        searchJob?.cancel()
    }
}
