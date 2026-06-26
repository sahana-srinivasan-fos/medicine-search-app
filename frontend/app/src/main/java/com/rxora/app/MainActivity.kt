package com.rxora.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.rxora.app.api.RetrofitClient
import com.rxora.app.databinding.ActivityMainBinding
import com.rxora.app.models.CorrectionResponse
import com.rxora.app.models.Medicine
import com.rxora.app.models.RecentSearch
import com.rxora.app.ui.MedicineAdapter
import com.rxora.app.ui.RecentSearchAdapter
import com.rxora.app.utils.CartManager
import com.rxora.app.utils.RecentSearchStore
import com.rxora.app.voice.VoiceTranscriber
import com.rxora.app.voice.recorder.AudioRecorder
import com.rxora.app.voice.whisper.WhisperManager
import kotlinx.coroutines.*
import java.io.File

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

    private val voiceTranscriber: VoiceTranscriber by lazy {
        WhisperManager(this)
    }

    private val whisperManager: WhisperManager by lazy {
        voiceTranscriber as WhisperManager
    }

    private val audioRecorder = AudioRecorder()
    private var currentRecordingFile: File? = null
    private var isRecording = false
    private var isTranscribing = false

    private val recordAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startVoiceRecording()
        } else {
            showVoiceError("Microphone permission denied")
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
            onVoiceButtonClicked()
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

    private fun onVoiceButtonClicked() {
        if (isRecording) {
            stopRecordingAndTranscribe()
        } else if (!isTranscribing) {
            requestMicrophonePermissionAndStart()
        }
    }

    private fun requestMicrophonePermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startVoiceRecording()
        } else {
            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startVoiceRecording() {
        if (isTranscribing) return
        val tempFile = try {
            File.createTempFile("rxora_voice_", ".wav", cacheDir)
        } catch (e: Exception) {
            showVoiceError("Unable to create temporary audio file")
            return
        }

        currentRecordingFile = tempFile
        isRecording = true
        setVoiceUiState("Listening...", true)
        Log.d(TAG, "VOICE_RECORD_STARTED")

        lifecycleScope.launch {
            try {
                audioRecorder.startRecording(tempFile) { error ->
                    runOnUiThread {
                        showVoiceError("Recording failed: ${error.localizedMessage}")
                        resetVoiceState()
                    }
                }
            } catch (e: Exception) {
                showVoiceError("Recording failed: ${e.localizedMessage}")
                resetVoiceState()
            }
        }
    }

    private fun stopRecordingAndTranscribe() {
        if (!isRecording) return
        isRecording = false
        Log.d(TAG, "VOICE_RECORD_STOPPED")
        setVoiceUiState("Transcribing...", true)
        binding.voiceButton.isEnabled = false
        isTranscribing = true

        val audioFile = currentRecordingFile
        currentRecordingFile = null

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    audioRecorder.stopRecording()
                }
                if (audioFile == null || !audioFile.exists()) {
                    showVoiceError("Recorded audio file was not created")
                    return@launch
                }

                val modelReady = withContext(Dispatchers.IO) {
                    Log.d(TAG, "WHISPER_MODEL_LOADING")
                    whisperManager.initModel()
                }

                if (!modelReady) {
                    showVoiceError("Whisper model failed to load. Ensure assets/models/ggml-base.en.bin is available.")
                    return@launch
                }

                Log.d(TAG, "WHISPER_MODEL_READY")
                val startTime = System.currentTimeMillis()
                Log.d(TAG, "TRANSCRIPTION_STARTED")
                val transcription = try {
                    voiceTranscriber.transcribe(audioFile)
                } catch (e: Exception) {
                    throw RuntimeException("Transcription failed", e)
                }
                val duration = System.currentTimeMillis() - startTime
                Log.d(TAG, "TRANSCRIPTION_FINISHED")
                Log.d(TAG, "TRANSCRIPTION_DURATION_MS: $duration")
                Log.d(TAG, "TRANSCRIPTION_RESULT: $transcription")

                if (transcription.isBlank()) {
                    showVoiceError("No speech was detected in the recording")
                } else {
                    setSearchTextAndSearch(transcription)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Voice transcription failed", e)
                showVoiceError(e.localizedMessage ?: "Voice transcription failed")
            } finally {
                audioFile?.delete()
                resetVoiceState()
            }
        }
    }

    private fun resetVoiceState() {
        isRecording = false
        isTranscribing = false
        binding.voiceButton.isEnabled = true
        binding.loadingProgressBar.visibility = View.GONE
        if (binding.statusText.text == "Listening..." || binding.statusText.text == "Transcribing...") {
            binding.statusText.text = "Ready for query"
        }
    }

    private fun setVoiceUiState(message: String, showProgress: Boolean) {
        binding.statusText.visibility = View.VISIBLE
        binding.statusText.text = message
        binding.loadingProgressBar.visibility = if (showProgress) View.VISIBLE else View.GONE
    }

    private fun showVoiceError(message: String) {
        binding.statusText.visibility = View.VISIBLE
        binding.statusText.text = message
        binding.loadingProgressBar.visibility = View.GONE
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
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
