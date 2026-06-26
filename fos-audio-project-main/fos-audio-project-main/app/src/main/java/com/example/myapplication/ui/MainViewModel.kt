package com.example.myapplication.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.recorder.AudioRecorder
import com.example.myapplication.whisper.Bm25Index
import com.example.myapplication.whisper.CorrectionRegistry
import com.example.myapplication.whisper.HybridRetriever
import com.example.myapplication.whisper.PhoneticIndex
import com.example.myapplication.whisper.WhisperManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File



class MainViewModel(application: Application) : AndroidViewModel(application) {

    private var gemmaManager: com.example.myapplication.whisper.GemmaManager? = null

    private val _isGemmaModelLoaded = MutableStateFlow(false)
    val isGemmaModelLoaded: StateFlow<Boolean> = _isGemmaModelLoaded.asStateFlow()

    private val _isGemmaLoading = MutableStateFlow(false)
    val isGemmaLoading: StateFlow<Boolean> = _isGemmaLoading.asStateFlow()

    private val audioRecorder  = AudioRecorder()
    private val whisperManager = WhisperManager(application)
    private val recordingFile  = File(application.cacheDir, "recording.wav")
    private val correctionRegistry = CorrectionRegistry(application)

    // ── State flows ──────────────────────────────────────────────────────────────
    private val _status = MutableStateFlow("Initializing...")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _timerText = MutableStateFlow("00:00")
    val timerText: StateFlow<String> = _timerText.asStateFlow()

    private val _transcription = MutableStateFlow("")
    val transcription: StateFlow<String> = _transcription.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _isModelLoaded = MutableStateFlow(false)
    val isModelLoaded: StateFlow<Boolean> = _isModelLoaded.asStateFlow()

    private val _isTranscribing = MutableStateFlow(false)
    val isTranscribing: StateFlow<Boolean> = _isTranscribing.asStateFlow()



    /** Holds candidate results for the UI to display as a selection list. */
    data class KeywordSelection(
        val keyword: String,
        val candidates: List<String>,
        val selectedMedicine: String? = null
    )

    enum class Screen { RECORDING, CART }
    private val _currentScreen = MutableStateFlow(Screen.RECORDING)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    data class CartItem(val name: String, val quantity: String)
    private val _cartItems = MutableStateFlow<List<CartItem>>(emptyList())
    val cartItems: StateFlow<List<CartItem>> = _cartItems.asStateFlow()

    private val _medicineSelections = MutableStateFlow<List<KeywordSelection>>(emptyList())
    val medicineSelections: StateFlow<List<KeywordSelection>> = _medicineSelections.asStateFlow()

    private val _finalMedicines = MutableStateFlow("")
    val finalMedicines: StateFlow<String> = _finalMedicines.asStateFlow()

    // Whether the Gemma AI model should run after transcription (false = fuzzy match only)
    private val _isAiMode = MutableStateFlow(false)
    val isAiMode: StateFlow<Boolean> = _isAiMode.asStateFlow()

    private val _isGemmaAvailable = MutableStateFlow(false)
    val isGemmaAvailable: StateFlow<Boolean> = _isGemmaAvailable.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0)
    val downloadProgress: StateFlow<Int> = _downloadProgress.asStateFlow()

    private val _downloadStatusText = MutableStateFlow("")
    val downloadStatusText: StateFlow<String> = _downloadStatusText.asStateFlow()

    private val _serverIp = MutableStateFlow("")
    val serverIp: StateFlow<String> = _serverIp.asStateFlow()

    private val _serverPort = MutableStateFlow("")
    val serverPort: StateFlow<String> = _serverPort.asStateFlow()

    private val _connectionStatus = MutableStateFlow("DISCONNECTED")
    val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()

    private val _typingFormat = MutableStateFlow<List<String>>(listOf("NAME", "TAB", "QTY", "ENTER"))
    val typingFormat: StateFlow<List<String>> = _typingFormat.asStateFlow()

    private var healthCheckJob: Job? = null
    private val sharedPrefs = application.getSharedPreferences("config", android.content.Context.MODE_PRIVATE)

    private var downloadJob: Job? = null
    private var searchJob: Job? = null

    private val _runningTasks = MutableStateFlow<List<String>>(emptyList())
    val runningTasks: StateFlow<List<String>> = _runningTasks.asStateFlow()

    fun addTask(taskName: String)    { _runningTasks.update { it + taskName } }
    fun removeTask(taskName: String) { _runningTasks.update { it - taskName } }

    // ── Medicine data + search indices ───────────────────────────────────────────
    val medicineList   = mutableListOf<String>()
    private val _isMedicinesLoaded = MutableStateFlow(false)
    val isMedicinesLoaded: StateFlow<Boolean> = _isMedicinesLoaded.asStateFlow()

    private var bm25Index:     Bm25Index?      = null
    private var phoneticIndex: PhoneticIndex?  = null
    private var hybridRetriever: HybridRetriever? = null

    private val _errorEvent = MutableSharedFlow<String>()
    val errorEvent: SharedFlow<String> = _errorEvent.asSharedFlow()

    private var timerJob: Job? = null
    private var secondsElapsed = 0

    // ── Init ─────────────────────────────────────────────────────────────────────
    init {
        loadModel()
        loadMedicinesAndBuildIndices()
        checkGemmaAvailability()
        val savedIp = sharedPrefs.getString("server_ip", "") ?: ""
        val savedPort = sharedPrefs.getString("server_port", "5001") ?: "5001"
        if (savedIp.contains(":")) {
            val colonIndex = savedIp.lastIndexOf(':')
            val ipOnly = savedIp.substring(0, colonIndex).trim()
            val portOnly = savedIp.substring(colonIndex + 1).trim()
            _serverIp.value = ipOnly
            _serverPort.value = if (portOnly.isNotEmpty()) portOnly else "5001"
            sharedPrefs.edit()
                .putString("server_ip", _serverIp.value)
                .putString("server_port", _serverPort.value)
                .apply()
        } else {
            _serverIp.value = savedIp
            _serverPort.value = savedPort
        }
        val savedFormatJson = sharedPrefs.getString("typing_format", "") ?: ""
        if (savedFormatJson.isNotEmpty()) {
            try {
                val array = org.json.JSONArray(savedFormatJson)
                val list = mutableListOf<String>()
                for (i in 0 until array.length()) {
                    list.add(array.getString(i))
                }
                _typingFormat.value = list
            } catch (_: Exception) {}
        }
        startHealthCheckLoop()
    }

    // ── Model loading ────────────────────────────────────────────────────────────
    private fun loadModel() {
        viewModelScope.launch {
            addTask("Loading Whisper Model")
            _status.value = "Loading model..."
            val success = whisperManager.initModel()
            if (success) {
                _status.value = "Ready"
                _isModelLoaded.value = true
            } else {
                _status.value = "Error loading model"
                _errorEvent.emit("Failed to load model from storage. Ensure the model file is available in assets.")
            }
            removeTask("Loading Whisper Model")
        }
    }

    // ── Medicine DB + index build ─────────────────────────────────────────────────
    private fun loadMedicinesAndBuildIndices() {
        viewModelScope.launch(Dispatchers.IO) {
            // Step 1: Load medicines.json
            addTask("Loading Medicines Database")
            try {
                val jsonString = getApplication<Application>().assets.open("medicines.json").use { it.bufferedReader().readText() }
                val jsonArray = org.json.JSONArray(jsonString)
                val list = mutableListOf<String>()
                for (i in 0 until jsonArray.length()) list.add(jsonArray.getString(i))
                medicineList.clear()
                medicineList.addAll(list)
                _isMedicinesLoaded.value = true
                Log.d(TAG, "Loaded ${medicineList.size} medicines from medicines.json")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load medicines.json", e)
                _errorEvent.emit("Failed to load medicines database: ${e.localizedMessage}")
                return@launch
            } finally {
                removeTask("Loading Medicines Database")
            }

            // Step 2: Build BM25 index
            addTask("Building Search Index")
            try {
                bm25Index = Bm25Index(medicineList)
                Log.d(TAG, "BM25 index ready")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to build BM25 index", e)
            }

            // Step 3: Build phonetic index
            try {
                phoneticIndex = PhoneticIndex(medicineList)
                Log.d(TAG, "Phonetic index ready")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to build phonetic index", e)
            }

            // Step 4: Wire hybrid retriever (only if both indices built successfully)
            val bm25 = bm25Index
            val phonetic = phoneticIndex
            if (bm25 != null && phonetic != null) {
                hybridRetriever = HybridRetriever(medicineList, bm25, phonetic, correctionRegistry)
                Log.d(TAG, "HybridRetriever ready")
            } else {
                Log.w(TAG, "HybridRetriever NOT built (one or more indices failed)")
            }

            removeTask("Building Search Index")
        }
    }

    private fun validateModelFile(file: File): String? {
        if (!file.exists()) return "File does not exist."
        val length = file.length()
        if (length < 100_000_000L) {
            return "Selected file is too small ($length bytes). Please ensure you selected the correct 3 GB model file."
        }
        return try {
            file.inputStream().use { input ->
                val header = ByteArray(16)
                val read = input.read(header)
                if (read < 8) return "Could not read file header."

                // Check for all zeros - highly indicative of a partial/sparse download
                if (header.take(read).all { it == 0.toByte() }) {
                    return "The selected file contains only zeros. This usually happens when selecting a file that is still being downloaded by the browser. Please wait for the download to complete and try again."
                }

                // Check for common non-binary headers (HTML, JSON)
                val asString = String(header, 0, read).uppercase()
                if (asString.contains("<!DOC") || asString.contains("<HTML") || asString.startsWith("{")) {
                    return "The selected file appears to be a web page or JSON error response instead of a binary model. Please ensure you downloaded the actual file (around 3GB) and not a link or error page."
                }

                // Check ZIP (PK\x03\x04) at index 0 or index 4
                val isZip = (header[0] == 0x50.toByte() &&
                             header[1] == 0x4b.toByte() &&
                             header[2] == 0x03.toByte() &&
                             header[3] == 0x04.toByte()) ||
                            (header[4] == 0x50.toByte() &&
                             header[5] == 0x4b.toByte() &&
                             header[6] == 0x03.toByte() &&
                             header[7] == 0x04.toByte())
                
                // Check TFLite (TFL3 at index 4 or index 0)
                val isTfLite = (header[4] == 0x54.toByte() && // T
                                header[5] == 0x46.toByte() && // F
                                header[6] == 0x4C.toByte() && // L
                                header[7] == 0x33.toByte()) || // 3
                               (header[0] == 0x54.toByte() && // T
                                header[1] == 0x46.toByte() && // F
                                header[2] == 0x4C.toByte() && // L
                                header[3] == 0x33.toByte())   // 3

                if (isZip || isTfLite) {
                    null // Valid
                } else {
                    val hexString = header.take(8.coerceAtMost(read)).joinToString(" ") { String.format("%02X", it) }
                    Log.w(TAG, "Validation failed: file size=$length, first $read bytes (hex): $hexString")
                    "The selected file is not a valid MediaPipe .task bundle (missing ZIP or FlatBuffer signature). Please ensure you selected the correct model file (e.g. gemma-2b-it-cpu-int4.bin or .task)."
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Validation failed: ${e.localizedMessage ?: e.message}", e)
            "Error validating file: ${e.message}"
        }
    }

    fun initializeGemma() {
        if (_isGemmaModelLoaded.value || _isGemmaLoading.value) return
        val destFile = File(getApplication<Application>().getExternalFilesDir(null), "gemma3n.task")
        if (!destFile.exists()) {
            Log.d(TAG, "Gemma model file does not exist, cannot initialize.")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _isGemmaLoading.value = true
            addTask("Initializing Gemma Model")
            try {
                Log.d(TAG, "Starting Gemma model initialization...")
                val manager = com.example.myapplication.whisper.GemmaManager(getApplication())
                if (com.example.myapplication.whisper.GemmaManager.lastInitError != null) {
                    throw Exception(com.example.myapplication.whisper.GemmaManager.lastInitError)
                }
                gemmaManager = manager
                _isGemmaModelLoaded.value = true
                Log.d(TAG, "Gemma model initialization complete.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize Gemma model", e)
                _errorEvent.emit("Failed to initialize Gemma: ${e.localizedMessage}")
            } finally {
                _isGemmaLoading.value = false
                removeTask("Initializing Gemma Model")
            }
        }
    }

    private suspend fun getOrInitializeGemma(): com.example.myapplication.whisper.GemmaManager? {
        val current = gemmaManager
        if (current != null) return current

        // If it's already loading, wait for it
        if (_isGemmaLoading.value) {
            addTask("Waiting for Gemma Model to Load")
            try {
                // Wait up to 10 seconds
                for (i in 1..50) {
                    delay(200)
                    val manager = gemmaManager
                    if (manager != null) return manager
                }
            } finally {
                removeTask("Waiting for Gemma Model to Load")
            }
        }

        // If not loaded and not loading, try to initialize it now
        if (!_isGemmaModelLoaded.value && !_isGemmaLoading.value) {
            initializeGemma()
            // Wait for it
            addTask("Waiting for Gemma Model to Load")
            try {
                for (i in 1..50) {
                    delay(200)
                    val manager = gemmaManager
                    if (manager != null) return manager
                }
            } finally {
                removeTask("Waiting for Gemma Model to Load")
            }
        }

        return gemmaManager
    }

    fun checkGemmaAvailability() {
        val destFile = File(getApplication<Application>().getExternalFilesDir(null), "gemma3n.task")
        if (destFile.exists()) {
            val validationError = validateModelFile(destFile)
            if (validationError != null) {
                Log.w(TAG, "gemma3n.task exists but is invalid: $validationError. Deleting invalid file.")
                destFile.delete()
                _isGemmaAvailable.value = false
            } else {
                _isGemmaAvailable.value = true
                if (!_isGemmaModelLoaded.value && !_isGemmaLoading.value) {
                    initializeGemma()
                }
            }
        } else {
            _isGemmaAvailable.value = false
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _isDownloading.value = false
        _downloadStatusText.value = "Download cancelled."
        removeTask("Downloading Gemma Model")
        removeTask("Importing Gemma Model")
    }

    /**
     * Toggle AI Mode on/off.
     * When AI Mode is ON, Gemma runs after each transcription.
     * When OFF, only BM25+phonetic fuzzy match runs (fast, no model inference).
     * Clears the current result so stale output from the previous mode is not shown.
     */
    fun toggleAiMode() {
        val newValue = !_isAiMode.value
        _isAiMode.value = newValue
        _finalMedicines.value = ""   // clear stale result on mode switch

        if (newValue) {
            // Eagerly check / load Gemma if not already loaded
            if (!_isGemmaModelLoaded.value && !_isGemmaLoading.value) {
                initializeGemma()
            }
            
            // If transcription exists, run the medicine search using Gemma!
            val currentTranscription = _transcription.value
            if (currentTranscription.isNotEmpty()) {
                runMedicineSearch(currentTranscription)
            }
        } else {
            // If toggle is turned off, and there is a transcription, run search in fuzzy mode!
            val currentTranscription = _transcription.value
            if (currentTranscription.isNotEmpty()) {
                runMedicineSearch(currentTranscription)
            }
        }
    }

    fun downloadGemmaModel(downloadUrl: String) {
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch(Dispatchers.IO) {
            _isDownloading.value = true
            _downloadStatusText.value = "Connecting to download server..."
            _downloadProgress.value = 0
            addTask("Downloading Gemma Model")

            try {
                val destFile = File(getApplication<Application>().getExternalFilesDir(null), "gemma3n.task")
                val tempFile = File(destFile.absolutePath + ".tmp")
                if (tempFile.exists()) tempFile.delete()

                val url = java.net.URL(downloadUrl)
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.connect()

                if (connection.responseCode != java.net.HttpURLConnection.HTTP_OK) {
                    if (connection.responseCode == java.net.HttpURLConnection.HTTP_UNAUTHORIZED || 
                        connection.responseCode == java.net.HttpURLConnection.HTTP_FORBIDDEN) {
                        throw java.io.IOException("This repository is gated. Direct in-app download is unauthorized. Please use 'Get Link' to download via browser, then 'Select File' to import.")
                    }
                    throw java.io.IOException("Server returned HTTP ${connection.responseCode} ${connection.responseMessage}")
                }

                val contentLength = connection.contentLengthLong
                val fileSize = if (contentLength > 0) contentLength else 3136226711L

                connection.inputStream.use { input ->
                    tempFile.outputStream().use { output ->
                        val buffer = ByteArray(256 * 1024) // 256KB buffer
                        var bytesRead: Int
                        var totalBytesDownloaded = 0L
                        var lastLogTime = System.currentTimeMillis()

                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalBytesDownloaded += bytesRead

                            val pct = ((totalBytesDownloaded * 100) / fileSize).toInt().coerceIn(0, 100)
                            _downloadProgress.value = pct

                            val now = System.currentTimeMillis()
                            if (now - lastLogTime > 500) {
                                val mb = totalBytesDownloaded / (1024 * 1024)
                                val totalMb = fileSize / (1024 * 1024)
                                _downloadStatusText.value = "Downloading: $mb MB / $totalMb MB ($pct%)"
                                lastLogTime = now
                            }
                        }
                    }
                }

                val validationError = validateModelFile(tempFile)
                if (validationError != null) {
                    tempFile.delete()
                    throw java.io.IOException(validationError)
                }

                if (destFile.exists()) destFile.delete()
                if (tempFile.renameTo(destFile)) {
                    Log.d(TAG, "Downloaded gemma3n.task successfully.")
                    _downloadStatusText.value = "Model downloaded successfully."
                    checkGemmaAvailability()
                } else {
                    tempFile.delete()
                    throw java.io.IOException("Failed to rename downloaded file.")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Download failed", e)
                viewModelScope.launch(Dispatchers.Main) {
                    _errorEvent.emit("Download failed: ${e.localizedMessage ?: e.message}")
                }
            } finally {
                _isDownloading.value = false
                removeTask("Downloading Gemma Model")
            }
        }
    }

    fun importModelFromUri(uri: android.net.Uri) {
        downloadJob?.cancel()
        downloadJob = viewModelScope.launch(Dispatchers.IO) {
            _isDownloading.value = true
            _downloadProgress.value = 0
            addTask("Importing Gemma Model")

            try {
                val resolver = getApplication<Application>().contentResolver
                val destFile = File(getApplication<Application>().getExternalFilesDir(null), "gemma3n.task")

                _downloadStatusText.value = "Copying model file from storage..."
                val tempFile = File(destFile.absolutePath + ".tmp")
                if (tempFile.exists()) tempFile.delete()

                var fileSize = 3136226711L // Default estimate
                resolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    fileSize = pfd.statSize
                }

                resolver.openInputStream(uri)?.use { input ->
                    val bis = java.io.BufferedInputStream(input)
                    tempFile.outputStream().use { output ->
                        val buffer = ByteArray(1024 * 1024) // 1MB buffer
                        var bytesRead: Int
                        var totalBytesCopied = 0L
                        var lastLogTime = System.currentTimeMillis()

                        while (bis.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalBytesCopied += bytesRead

                            val pct = ((totalBytesCopied * 100) / fileSize).toInt().coerceIn(0, 100)
                            _downloadProgress.value = pct

                            val now = System.currentTimeMillis()
                            if (now - lastLogTime > 500) {
                                val mb = totalBytesCopied / (1024 * 1024)
                                val totalMb = fileSize / (1024 * 1024)
                                _downloadStatusText.value = "Copying: $mb MB / $totalMb MB ($pct%)"
                                lastLogTime = now
                            }
                        }
                    }
                }

                val validationError = validateModelFile(tempFile)
                if (validationError != null) {
                    tempFile.delete()
                    throw java.io.IOException(validationError)
                }

                if (destFile.exists()) destFile.delete()
                if (tempFile.renameTo(destFile)) {
                    Log.d(TAG, "Imported gemma3n.task successfully.")
                    _downloadStatusText.value = "Model imported successfully."
                    checkGemmaAvailability()
                } else {
                    tempFile.delete()
                    throw java.io.IOException("Failed to rename imported file.")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Import failed", e)
                viewModelScope.launch(Dispatchers.Main) {
                    _errorEvent.emit("Import failed: ${e.localizedMessage ?: e.message}")
                }
            } finally {
                _isDownloading.value = false
                removeTask("Importing Gemma Model")
            }
        }
    }

    // ── Recording ────────────────────────────────────────────────────────────────
    fun startRecording() {
        if (!_isModelLoaded.value) return
        if (_isRecording.value || _isTranscribing.value) return

        _isRecording.value = true
        _status.value = "Recording..."
        addTask("Recording Audio")
        viewModelScope.launch {
            try {
                audioRecorder.startRecording(recordingFile) { exception ->
                    viewModelScope.launch { handleRecordingError(exception) }
                }
                startTimer()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start recording", e)
                _status.value = "Ready"
                _isRecording.value = false
                removeTask("Recording Audio")
                _errorEvent.emit("Failed to start recording: ${e.localizedMessage}")
            }
        }
    }

    private suspend fun handleRecordingError(exception: Exception) {
        Log.e(TAG, "Recording background thread error", exception)
        stopTimer()
        _status.value = "Ready"
        _isRecording.value = false
        removeTask("Recording Audio")
        _errorEvent.emit("Recording failed: ${exception.localizedMessage}")
    }

    fun stopRecording() {
        if (!_isRecording.value) return

        _isRecording.value = false
        stopTimer()
        removeTask("Recording Audio")
        viewModelScope.launch {
            try {
                _status.value = "Transcribing..."
                audioRecorder.stopRecording()
                transcribeAudio()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop recording", e)
                _status.value = "Ready"
                _errorEvent.emit("Failed to stop recording: ${e.localizedMessage}")
            }
        }
    }

    private fun transcribeAudio() {
        viewModelScope.launch {
            _isTranscribing.value = true
            addTask("Transcribing Audio")
            try {
                if (!recordingFile.exists() || recordingFile.length() == 0L) {
                    _status.value = "Completed"
                    _errorEvent.emit("No audio was recorded.")
                    return@launch
                }

                val result = withContext(Dispatchers.Default) {
                    whisperManager.transcribe(recordingFile)
                }

                _transcription.value = result
                _status.value = "Completed"
                runMedicineSearch(result)
            } catch (e: Exception) {
                Log.e(TAG, "Transcription failed", e)
                _status.value = "Completed"
                _errorEvent.emit("Transcription failed: ${e.localizedMessage}")
            } finally {
                _isTranscribing.value = false
                removeTask("Transcribing Audio")
            }
        }
    }

    // ── Phonetic normalization ────────────────────────────────────────────────────
    // DO NOT MODIFY — pre-processing step applied before tokenization/BM25/phonetic indexing

    @Suppress("unused")
    private val splitRegex = Regex("[,;:]+|\\n+|\\r+|\\.(?!\\d)|\\.{3,}|\\s+-\\s+|\\s{2,}|\\band\\b", RegexOption.IGNORE_CASE)



    // ── Main medicine search pipeline ─────────────────────────────────────────────
    private fun runMedicineSearch(query: String) {


        if (query.isEmpty() || medicineList.isEmpty()) {
            _finalMedicines.value = ""
            return
        }

        searchJob?.cancel()
        searchJob = viewModelScope.launch(Dispatchers.Default) {
            addTask("Medicine Search")
            try {
                val normalizedQuery = normalizeSegment(query)

                // Keyword extraction (unchanged from previous logic)
                val keywords = normalizedQuery.lowercase()
                    .split(Regex("[^a-zA-Z0-9]+"))
                    .flatMap { token ->
                        val m = Regex("([a-zA-Z]+)(\\d+)").matchEntire(token)
                        if (m != null) listOf(m.groupValues[1], m.groupValues[2])
                        else listOf(token)
                    }
                    .filter { it.length >= 3 && !it.all { char -> char.isDigit() } }

                Log.d(TAG, "Whisper transcription: $query")
                Log.d(TAG, "Normalized: $normalizedQuery | Keywords: $keywords")

                if (keywords.isEmpty()) {
                    Log.d(TAG, "No keywords found in transcription.")
                    _finalMedicines.value = ""
                    return@launch
                }

                val retriever = hybridRetriever

                if (retriever == null) {
                    // Indices not ready — fall back to simple substring matching (legacy path)
                    Log.w(TAG, "HybridRetriever not ready — using legacy substring fallback")
                    legacySubstringFallback(query, normalizedQuery, keywords)
                    return@launch
                }

                // ── Step 1: Hybrid retrieval ──────────────────────────────────────
                val retrieval = retriever.retrieve(
                    rawTranscript    = query,
                    normalizedQuery  = normalizedQuery,
                    keywords         = keywords
                )

                // Populate selections for the UI list
                val newSelections = keywords.map { kw ->
                    val cands = (retrieval.perKeywordDebug[kw] ?: emptyList()).map { it.first }.take(5)
                    // Auto-select if high confidence or user pref bonus was applied
                    val topScore = retrieval.perKeywordDebug[kw]?.firstOrNull()?.second ?: 0.0
                    val topMed = cands.firstOrNull()
                    val autoSelected = if (topScore >= 20.0) topMed else null // Threshold for auto-selection

                    KeywordSelection(kw, cands, autoSelected)
                }
                _medicineSelections.value = newSelections

                // High-confidence medicines — direct output, no Gemma needed
                val directOutput = retrieval.highConfResolved.values.toMutableList()

                // ── Step 2: Branch on AI Mode ──────────────────────────────────────────
                if (!_isAiMode.value) {
                    // ── AI OFF: Fuzzy match (no Gemma) ────────────────────────────────
                    val fuzzyMatched = mutableListOf<String>()
                    for (kw in retrieval.gemmaKeywords) {
                        val candidates = retrieval.perKeywordDebug[kw] ?: continue
                        var bestCand: String? = null
                        var bestScore = 0.0
                        for ((candName, _) in candidates) {
                            val score = com.example.myapplication.whisper.StringSimilarity.partialRatio(kw, candName)
                            if (score > bestScore) {
                                bestScore = score
                                bestCand = candName
                            }
                        }
                        if (bestCand != null && bestCand !in directOutput && bestCand !in fuzzyMatched) {
                            fuzzyMatched.add(bestCand)
                            Log.d(TAG, "FUZZY MATCH [$kw] -> $bestCand (score=$bestScore)")
                        }
                    }
                    val fuzzyOutput = (directOutput + fuzzyMatched).distinct()
                    val fuzzyDeduplicated = deduplicateByBrand(fuzzyOutput, query, retrieval.gemmaCandidates)
                    Log.d(TAG, "Fuzzy match output: $fuzzyDeduplicated")
                    _finalMedicines.value = fuzzyDeduplicated.joinToString("\n")

                } else {
                    // ── AI ON: Gemma only ─────────────────────────────────────────────
                    if (retrieval.gemmaCandidates.isNotEmpty()) {
                        val manager = getOrInitializeGemma()
                        if (manager == null) {
                            _errorEvent.emit("Gemma model is not ready.")
                            _finalMedicines.value = "Error: Gemma model not ready"
                            return@launch
                        }
                        val gemmaOutput = try {
                            manager.reRankMedicines(normalizedQuery, retrieval.gemmaCandidates)
                        } catch (t: Throwable) {
                            Log.e(TAG, "Failed to initialize or run Gemma", t)
                            _errorEvent.emit("Gemma error: ${t.localizedMessage ?: t.message}")
                            null
                        }
                        Log.d(TAG, "Gemma raw output: $gemmaOutput")
                        _finalMedicines.value = gemmaOutput ?: "Gemma returned null"
                    } else {
                        // All direct matches — nothing for Gemma to do
                        _finalMedicines.value = directOutput.joinToString("\n")
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Medicine search failed", e)
                _finalMedicines.value = ""
            } finally {
                removeTask("Medicine Search")
            }
        }
    }

    /**
     * Legacy substring-based fallback used when the hybrid retriever is not yet ready.
     * Branches on isAiMode: fuzzy match if OFF, Gemma if ON.
     */
    private suspend fun legacySubstringFallback(
        query: String,
        normalizedQuery: String,
        keywords: List<String>
    ) {
        val taskLabel = if (_isAiMode.value) "Gemma Medicine Search" else "Fuzzy Medicine Search"
        addTask(taskLabel)
        try {
            val keywordMatches = keywords.map { kw ->
                medicineList.filter { med -> med.lowercase().contains(kw) }
            }

            val filteredMedicines = mutableListOf<String>()
            val addedSet = mutableSetOf<String>()
            var index = 0
            var addedInLastPass = true

            while (filteredMedicines.size < 50 && addedInLastPass) {
                addedInLastPass = false
                for (matches in keywordMatches) {
                    if (index < matches.size) {
                        val candidate = matches[index]
                        if (candidate !in addedSet) {
                            filteredMedicines.add(candidate)
                            addedSet.add(candidate)
                            if (filteredMedicines.size >= 50) break
                        }
                        addedInLastPass = true
                    }
                }
                index++
            }

            if (filteredMedicines.isEmpty()) {
                _finalMedicines.value = ""
                return
            }

            if (!_isAiMode.value) {
                // ── AI OFF: Fuzzy match per keyword ──────────────────────────────────
                val fuzzyMatched = mutableListOf<String>()
                val usedCandidates = mutableSetOf<String>()
                for (kw in keywords) {
                    var bestCand: String? = null
                    var bestScore = 0.0
                    for (cand in filteredMedicines) {
                        val score = com.example.myapplication.whisper.StringSimilarity.partialRatio(kw, cand)
                        if (score > bestScore) { bestScore = score; bestCand = cand }
                    }
                    if (bestCand != null && usedCandidates.add(bestCand)) {
                        fuzzyMatched.add(bestCand)
                        Log.d(TAG, "LEGACY FUZZY [$kw] -> $bestCand (score=$bestScore)")
                    }
                }
                _finalMedicines.value = fuzzyMatched.joinToString("\n")
            } else {
                // ── AI ON: Gemma only ─────────────────────────────────────────────────
                val manager = getOrInitializeGemma()
                if (manager == null) {
                    _errorEvent.emit("Gemma model is not ready.")
                    _finalMedicines.value = "Error: Gemma model not ready"
                    return
                }
                val gemmaOutput = try {
                    manager.reRankMedicines(normalizedQuery, filteredMedicines)
                } catch (t: Throwable) {
                    Log.e(TAG, "Gemma error (legacy path)", t)
                    null
                }
                _finalMedicines.value = gemmaOutput ?: "Gemma returned null"
            }

        } finally {
            removeTask(taskLabel)
        }
    }

    // ── Utility ──────────────────────────────────────────────────────────────────
    fun clearTranscription() {
        if (_isTranscribing.value || _isRecording.value) return
        _transcription.value = ""
        _status.value = "Ready"
        _timerText.value = "00:00"
        _finalMedicines.value = ""
        _medicineSelections.value = emptyList()
    }

    /**
     * User selects a medicine from the candidate list for a keyword.
     */
    fun selectMedicine(keyword: String, medicineName: String) {
        _medicineSelections.update { list ->
            list.map { 
                if (it.keyword == keyword) it.copy(selectedMedicine = medicineName.ifEmpty { null }) 
                else it 
            }
        }
    }

    fun updateCartItemQuantity(name: String, quantity: String) {
        _cartItems.update { list ->
            list.map {
                if (it.name == name) it.copy(quantity = quantity)
                else it
            }
        }
    }

    fun removeCartItem(name: String) {
        _cartItems.update { list ->
            list.filterNot { it.name == name }
        }
    }

    fun navigateToRecording() {
        _currentScreen.value = Screen.RECORDING
    }

    fun navigateToCart() {
        _currentScreen.value = Screen.CART
    }

    fun confirmSelections() {
        val selections = _medicineSelections.value
        val newCartItems = _cartItems.value.toMutableList()

        for (sel in selections) {
            val med = sel.selectedMedicine ?: continue
            
            if (newCartItems.none { it.name == med }) {
                newCartItems.add(CartItem(med, "1"))
            }

            // SELF-LEARNING: Record that this keyword maps to this medicine
            correctionRegistry.addCorrection(sel.keyword, med)
        }

        _cartItems.value = newCartItems
        _currentScreen.value = Screen.CART
    }

    fun sendCartToPc() {
        val items = _cartItems.value
        if (items.isEmpty()) return

        val confirmed = items.map { ConfirmedMedicine(it.name, it.quantity) }
        sendConfirmedMedicines(confirmed)

        val resultText = items.map { "${it.name} (${it.quantity})" }.joinToString("\n")
        _finalMedicines.value = resultText
    }

    private fun startHealthCheckLoop() {
        healthCheckJob?.cancel()
        healthCheckJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                val ip = _serverIp.value
                val port = _serverPort.value
                if (ip.isNotEmpty()) {
                    val success = performHealthCheck(ip, port)
                    _connectionStatus.value = if (success) "CONNECTED" else "DISCONNECTED"
                } else {
                    _connectionStatus.value = "DISCONNECTED"
                }
                delay(5000)
            }
        }
    }

    private fun performHealthCheck(ip: String, portStr: String): Boolean {
        if (ip.isEmpty()) return false
        val port = portStr.toIntOrNull() ?: 5001
        return try {
            java.net.Socket().use { socket ->
                socket.connect(java.net.InetSocketAddress(ip, port), 1000)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun updateServerIp(ip: String) {
        _serverIp.value = ip
        sharedPrefs.edit().putString("server_ip", ip).apply()
        triggerHealthCheck()
    }

    fun updateServerPort(port: String) {
        _serverPort.value = port
        sharedPrefs.edit().putString("server_port", port).apply()
        triggerHealthCheck()
    }

    private fun triggerHealthCheck() {
        viewModelScope.launch(Dispatchers.IO) {
            val ip = _serverIp.value
            val port = _serverPort.value
            val success = performHealthCheck(ip, port)
            _connectionStatus.value = if (success) "CONNECTED" else "DISCONNECTED"
        }
    }

    fun updateTypingFormat(newFormat: List<String>) {
        _typingFormat.value = newFormat
        val json = org.json.JSONArray(newFormat).toString()
        sharedPrefs.edit().putString("typing_format", json).apply()
    }

    data class ConfirmedMedicine(val name: String, val quantity: String)

    fun sendConfirmedMedicines(items: List<ConfirmedMedicine>) {
        val ip = _serverIp.value
        val portStr = _serverPort.value
        if (ip.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            addTask("Sending Data to PC")
            try {
                val port = portStr.toIntOrNull() ?: 5001
                
                val legacyItems = items.map { it.name }
                val instructions = org.json.JSONArray()
                val currentFormat = _typingFormat.value
                
                for (item in items) {
                    for (tag in currentFormat) {
                        when (tag) {
                            "NAME" -> {
                                instructions.put(org.json.JSONObject().apply {
                                    put("action", "type")
                                    put("text", item.name)
                                })
                            }
                            "TAB" -> {
                                instructions.put(org.json.JSONObject().apply {
                                    put("action", "press")
                                    put("key", "tab")
                                })
                            }
                            "QTY" -> {
                                instructions.put(org.json.JSONObject().apply {
                                    put("action", "type")
                                    put("text", item.quantity)
                                })
                            }
                            "ENTER" -> {
                                instructions.put(org.json.JSONObject().apply {
                                    put("action", "press")
                                    put("key", "enter")
                                })
                            }
                            "SPACE" -> {
                                instructions.put(org.json.JSONObject().apply {
                                    put("action", "press")
                                    put("key", "space")
                                })
                            }
                            "COMMA" -> {
                                instructions.put(org.json.JSONObject().apply {
                                    put("action", "type")
                                    put("text", ",")
                                })
                            }
                        }
                    }
                    instructions.put(org.json.JSONObject().apply {
                        put("action", "wait")
                        put("seconds", 0.2)
                    })
                }

                val json = org.json.JSONObject().apply {
                    put("items", org.json.JSONArray(legacyItems))
                    put("instructions", instructions)
                    put("quantityEnabled", true)
                    put("action", "scan")
                }
                
                java.net.Socket().use { socket ->
                    socket.connect(java.net.InetSocketAddress(ip, port), 5000)
                    socket.getOutputStream().write(json.toString().toByteArray(Charsets.UTF_8))
                    socket.getOutputStream().flush()
                }
                Log.d("MainViewModel", "Data sent successfully to PC")
                _status.value = "Sent to PC successfully"
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to send data to PC", e)
                _errorEvent.emit("PC Send Failed: ${e.localizedMessage ?: e.message}")
            } finally {
                removeTask("Sending Data to PC")
            }
        }
    }

    private fun startTimer() {
        secondsElapsed = 0
        _timerText.value = "00:00"
        timerJob = viewModelScope.launch(Dispatchers.Main) {
            while (true) {
                delay(1000)
                secondsElapsed++
                val minutes = secondsElapsed / 60
                val seconds = secondsElapsed % 60
                _timerText.value = String.format("%02d:%02d", minutes, seconds)
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopTimer()
        healthCheckJob?.cancel()
        viewModelScope.launch { whisperManager.release() }
        gemmaManager?.let {
            try { it.close() }
            catch (e: Exception) { Log.e(TAG, "Error closing gemmaManager: ${e.message}") }
        }
    }

    companion object {
        private const val TAG = "MainViewModel"

        fun normalizeSegment(segment: String): String {
            var s = segment.lowercase()

            // General phonetic corrections for common Whisper mis-transcriptions
            s = s.replace(Regex("nitro\\s*(bag|back|backed|bact|pack|patched)"), "nitrobact")
                 .replace("9-r-bact",   "nitrobact")
                 .replace("9 r bact",   "nitrobact")
                 .replace("9rbact",     "nitrobact")
                 .replace("nine r bact","nitrobact")
                 .replace("ninerbact",  "nitrobact")
                 .replace("1625",       "625")
                 .replace("dolor",      "dolo")
                 .replace("dota",       "dolo")
                 .replace("zolo",       "dolo")
                 .replace(Regex("\\b(augment\\s*in|augmentin)\\b"), "augmentin")
                 .replace(Regex("\\baug\\b\\.?"), "augmentin")
                 .replace("augmenting", "augmentin")
                 .replace("ogmentin",   "augmentin")
                 .replace("augumentin", "augmentin")

            if (s.contains("$") || s.contains("£") || s.contains("€") || s.contains("₹") || s.contains("dollar")) {
                s = s.replace("$",      "dolo ")
                     .replace("£",      "dolo ")
                     .replace("€",      "dolo ")
                     .replace("₹",      "dolo ")
                     .replace("dollar", "dolo ")
            }

            s = s.replace("dodo",        "dolo")
                 .replace("dollo",       "dolo")
                 .replace("dola",        "dolo")
                 .replace("dolla",       "dolo")
                 .replace("augmentine",  "augmentin")
                 .replace("t-backed",    "t-bact")
                 .replace("t backed",    "t-bact")
                 .replace("tbact",       "t-bact")
                 .replace("tea bag",     "t-bact")
                 .replace("t bag",       "t-bact")
                 .replace("trombafog",   "nitrobact 100")
                 .replace("tromba fog",  "nitrobact 100")
                 .replace(Regex("\\b(trombo\\s*fobe|thrombo\\s*fobe|trombo\\s*4|thrombo\\s*4|trombo|thrombo)\\b"), "thrombophob")
                 .replace("rajo",        "razo")
                 .replace("raso",        "razo")
                 .replace("crocine",     "crocin")

            if (s.contains("dolo")) {
                s = s.replace(Regex("(\\d+)\\.(\\d+)")) { mr -> mr.groupValues[1] + mr.groupValues[2] }
                s = s.replace(Regex("\\b1\\s*650\\b"), "650")
                     .replace(Regex("\\b1\\s*500\\b"), "500")
            }
            return s.replace(Regex("\\s+"), " ").trim()
        }

        fun getBrandName(name: String): String {
            val clean = name.uppercase().replace("-", " ").trim()
            val words = clean.split(Regex("\\s+"))
            if (words.isEmpty()) return ""
            if (words[0].length == 1 && words.size > 1) {
                return words[0] + " " + words[1]
            }
            return words[0]
        }

        fun deduplicateByBrand(
            medicines: List<String>,
            rawTranscript: String,
            allCandidates: List<String>
        ): List<String> {
            val transcriptNumbers = Regex("\\d+").findAll(rawTranscript).map { it.value }.toList()

            val grouped = medicines.groupBy { getBrandName(it) }
            val result = mutableListOf<String>()

            for ((brand, list) in grouped) {
                if (list.size == 1) {
                    result.add(list[0])
                    continue
                }

                // Try to find the one matching a transcript number
                var bestMatch: String? = null
                for (cand in list) {
                    val candTokens = cand.split(Regex("[^a-zA-Z0-9]+"))
                    if (candTokens.any { it in transcriptNumbers }) {
                        bestMatch = cand
                        break
                    }
                }

                if (bestMatch != null) {
                    result.add(bestMatch)
                } else {
                    // Fall back to the one that appears first in allCandidates (highest retrieval rank)
                    val sorted = list.sortedBy { cand ->
                        val idx = allCandidates.indexOf(cand)
                        if (idx != -1) idx else Int.MAX_VALUE
                    }
                    result.add(sorted.first())
                }
            }
            return result
        }
    }
}
