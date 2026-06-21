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
import androidx.recyclerview.widget.LinearLayoutManager
import com.rxora.app.api.RetrofitClient
import com.rxora.app.databinding.ActivityMainBinding
import com.rxora.app.models.Medicine
import com.rxora.app.models.RecentSearch
import com.rxora.app.ui.MedicineAdapter
import com.rxora.app.ui.RecentSearchAdapter
import com.rxora.app.utils.RecentSearchStore
import kotlinx.coroutines.*
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val medicineAdapter = MedicineAdapter()
    private var recentSearchAdapter: RecentSearchAdapter? = null
    private var presetSearchAdapter: RecentSearchAdapter? = null

    private var searchJob: Job? = null
    private var searchStartTime: Long = 0L
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
        val searches = RecentSearchStore.loadRecentSearches(this)
        if (searches.isNotEmpty()) {
            binding.recentTitle.visibility = View.VISIBLE
            recentSearchAdapter?.setData(searches.map { RecentSearch(query = it) })
        }

        lifecycleScope.launch {
            loadPresetMedicines()
        }

        binding.searchEditText.requestFocus()
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

        presetSearchAdapter = RecentSearchAdapter(onSearchClick = { query ->
            binding.searchEditText.setText(query)
            binding.searchEditText.setSelection(query.length)
        })
        binding.presetRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = presetSearchAdapter
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

                saveRecentSearch(query)
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
                saveRecentSearch(spokenText)
            }
        }
    }

    private fun saveRecentSearch(query: String) {
        val searches = RecentSearchStore.addRecentSearch(this, query)
        binding.recentTitle.visibility = if (searches.isNotEmpty()) View.VISIBLE else View.GONE
        recentSearchAdapter?.setData(searches.map { RecentSearch(query = it) })
    }

    private suspend fun loadPresetMedicines() {
        try {
            val response = withContext(Dispatchers.IO) {
                RetrofitClient.medicineApi.getPresetMedicines()
            }
            if (response.isSuccessful && response.body() != null) {
                val items = response.body()!!.medicines.map { RecentSearch(query = it.name) }
                if (items.isNotEmpty()) {
                    presetSearchAdapter?.setData(items)
                    if (binding.searchEditText.text.isNullOrEmpty()) {
                        binding.presetSection.visibility = View.VISIBLE
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Preset load failed", e)
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
