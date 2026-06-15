package com.rxora.app

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.rxora.app.api.RetrofitClient
import com.rxora.app.databinding.ActivityMainBinding
import com.rxora.app.models.Category
import com.rxora.app.models.Medicine
import com.rxora.app.ui.MedicineAdapter
import com.rxora.app.ui.RecentSearchAdapter
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val medicineAdapter = MedicineAdapter()
    private var recentSearchAdapter: RecentSearchAdapter? = null
    private val userId = "user_${System.currentTimeMillis()}" // Simple user ID
    private var currentQuery = ""
    private var currentCategory: String? = null

    companion object {
        private const val SPEECH_REQUEST_CODE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerViews()
        setupButtons()
        loadPresets()
        loadCategories()
        loadRecentSearches()
    }

    private fun setupRecyclerViews() {
        // Medicine results RecyclerView
        binding.medicineRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = medicineAdapter
        }

        // Recent searches RecyclerView
        recentSearchAdapter = RecentSearchAdapter(onSearchClick = { query ->
            binding.searchEditText.setText(query)
            performSearch(query, currentCategory)
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
                    if (currentQuery.isNotEmpty()) {
                        performSearch(currentQuery, currentCategory)
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

        RetrofitClient.medicineApi.searchMedicines(query, category, limit = 100)
            .enqueue(object : Callback<com.rxora.app.models.SearchResponse> {
                override fun onResponse(
                    call: Call<com.rxora.app.models.SearchResponse>,
                    response: Response<com.rxora.app.models.SearchResponse>
                ) {
                    showLoading(false)
                    if (response.isSuccessful && response.body() != null) {
                        val medicines = response.body()!!.medicines
                        medicineAdapter.setData(medicines)

                        // Track the search
                        trackSearch(query, category, voiceSearch = false)

                        if (medicines.isEmpty()) {
                            Toast.makeText(this@MainActivity, "No medicines found", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this@MainActivity, "Search failed: ${response.code()}", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(
                    call: Call<com.rxora.app.models.SearchResponse>,
                    t: Throwable
                ) {
                    showLoading(false)
                    Toast.makeText(
                        this@MainActivity,
                        "Error: ${t.message}",
                        Toast.LENGTH_SHORT
                    ).show()
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
            Toast.makeText(this, "Voice input not supported on this device", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SPEECH_REQUEST_CODE && resultCode == RESULT_OK) {
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!results.isNullOrEmpty()) {
                val spokenText = results[0]
                binding.searchEditText.setText(spokenText)
                performVoiceSearch(spokenText)
            }
        }
    }

    private fun performVoiceSearch(query: String) {
        currentQuery = query
        showLoading(true)

        RetrofitClient.medicineApi.voiceSearch(query, userId)
            .enqueue(object : Callback<com.rxora.app.models.SearchResponse> {
                override fun onResponse(
                    call: Call<com.rxora.app.models.SearchResponse>,
                    response: Response<com.rxora.app.models.SearchResponse>
                ) {
                    showLoading(false)
                    if (response.isSuccessful && response.body() != null) {
                        val medicines = response.body()!!.medicines
                        medicineAdapter.setData(medicines)
                        trackSearch(query, currentCategory, voiceSearch = true)
                        Toast.makeText(this@MainActivity, "Found ${medicines.size} results", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainActivity, "Voice search failed", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(
                    call: Call<com.rxora.app.models.SearchResponse>,
                    t: Throwable
                ) {
                    showLoading(false)
                    Toast.makeText(this@MainActivity, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun trackSearch(query: String, category: String?, voiceSearch: Boolean) {
        RetrofitClient.medicineApi.trackSearch(userId, query, category, voiceSearch)
            .enqueue(object : Callback<Unit> {
                override fun onResponse(call: Call<Unit>, response: Response<Unit>) {
                    // Silently succeed
                    loadRecentSearches() // Refresh recent searches
                }

                override fun onFailure(call: Call<Unit>, t: Throwable) {
                    // Silently fail (analytics not critical)
                }
            })
    }

    private fun loadPresets() {
        RetrofitClient.medicineApi.getPresetMedicines()
            .enqueue(object : Callback<List<Medicine>> {
                override fun onResponse(call: Call<List<Medicine>>, response: Response<List<Medicine>>) {
                    if (response.isSuccessful && response.body() != null) {
                        medicineAdapter.setData(response.body()!!)
                    }
                }

                override fun onFailure(call: Call<List<Medicine>>, t: Throwable) {
                    Toast.makeText(this@MainActivity, "Failed to load presets", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun loadCategories() {
        RetrofitClient.medicineApi.getCategories()
            .enqueue(object : Callback<List<Category>> {
                override fun onResponse(call: Call<List<Category>>, response: Response<List<Category>>) {
                    if (response.isSuccessful && response.body() != null) {
                        val categories = response.body()!!
                        val categoryNames = mutableListOf("All Categories")
                        categoryNames.addAll(categories.map { it.name })

                        val adapter = ArrayAdapter(
                            this@MainActivity,
                            android.R.layout.simple_spinner_item,
                            categoryNames
                        )
                        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                        binding.categorySpinner.adapter = adapter
                    }
                }

                override fun onFailure(call: Call<List<Category>>, t: Throwable) {
                    Toast.makeText(this@MainActivity, "Failed to load categories", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun loadRecentSearches() {
        RetrofitClient.medicineApi.getRecentSearches(userId)
            .enqueue(object : Callback<List<com.rxora.app.models.RecentSearch>> {
                override fun onResponse(
                    call: Call<List<com.rxora.app.models.RecentSearch>>,
                    response: Response<List<com.rxora.app.models.RecentSearch>>
                ) {
                    if (response.isSuccessful && response.body() != null) {
                        recentSearchAdapter?.setData(response.body()!!)
                    }
                }

                override fun onFailure(
                    call: Call<List<com.rxora.app.models.RecentSearch>>,
                    t: Throwable
                ) {
                    // Silently fail for recent searches
                }
            })
    }

    private fun showLoading(show: Boolean) {
        binding.loadingProgressBar.visibility = if (show) View.VISIBLE else View.GONE
    }
}