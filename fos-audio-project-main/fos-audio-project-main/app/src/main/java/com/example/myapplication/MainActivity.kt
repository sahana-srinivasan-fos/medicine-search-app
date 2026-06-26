package com.example.myapplication

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.myapplication.ui.MainViewModel
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    private lateinit var tvStatus: TextView
    private lateinit var statusDot: View
    private lateinit var tvTimer: TextView
    private lateinit var tvTranscription: TextView
    private lateinit var btnRecord: MaterialButton
    private lateinit var btnStop: MaterialButton
    private lateinit var btnClear: MaterialButton
    private lateinit var scrollView: View
    private lateinit var tvFinalMedicine: TextView
    private lateinit var tvResultSectionHeader: TextView
    private lateinit var btnConfirm: MaterialButton
    private lateinit var rvSelections: androidx.recyclerview.widget.RecyclerView
    private lateinit var resultScrollView: View
    private lateinit var selectionAdapter: com.example.myapplication.ui.MedicineSelectionAdapter

    // AI Mode toggle views
    private lateinit var switchAiMode: com.google.android.material.materialswitch.MaterialSwitch
    private lateinit var tvAiModeStatus: TextView
    private lateinit var aiModeDot: android.view.View

    // PC Connection views
    private lateinit var etServerIp: com.google.android.material.textfield.TextInputEditText
    private lateinit var etServerPort: com.google.android.material.textfield.TextInputEditText
    private lateinit var tvPcConnectionStatus: TextView
    private lateinit var pcStatusDot: View

    // PC Connection Formatting views
    private lateinit var rvFormatTags: androidx.recyclerview.widget.RecyclerView
    private lateinit var tvResetFormat: TextView
    private lateinit var cgAvailableTags: com.google.android.material.chip.ChipGroup
    private lateinit var formatTagAdapter: com.example.myapplication.ui.FormatTagAdapter

    // Model Download UI bindings
    // Layout groups for screens
    private lateinit var recordingScreenGroup: androidx.constraintlayout.widget.Group
    private lateinit var cartScreenGroup: androidx.constraintlayout.widget.Group

    // Cart Review Screen views
    private lateinit var rvCartItems: androidx.recyclerview.widget.RecyclerView
    private lateinit var btnBackToRecording: MaterialButton
    private lateinit var btnSendToPc: MaterialButton
    private lateinit var cartItemAdapter: com.example.myapplication.ui.CartItemAdapter
    private lateinit var modelDownloadCard: View
    private lateinit var downloadProgressContainer: View
    private lateinit var downloadProgressBar: ProgressBar
    private lateinit var tvDownloadStatus: TextView
    private lateinit var btnDownloadInApp: MaterialButton
    private lateinit var btnDownloadLink: MaterialButton
    private lateinit var btnSelectModelFile: MaterialButton
    private lateinit var btnCancelAiMode: MaterialButton

    private val selectModelLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importModelFromUri(uri)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startRecording()
        } else {
            Toast.makeText(
                this,
                "Microphone permission is required to record audio.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize views
        tvStatus = findViewById(R.id.tvStatus)
        statusDot = findViewById(R.id.statusDot)
        tvTimer = findViewById(R.id.tvTimer)
        tvTranscription = findViewById(R.id.tvTranscription)
        btnRecord = findViewById(R.id.btnRecord)
        btnStop = findViewById(R.id.btnStop)
        btnClear = findViewById(R.id.btnClear)
        scrollView = findViewById(R.id.scrollView)
        tvFinalMedicine     = findViewById(R.id.tvFinalMedicine)
        tvResultSectionHeader = findViewById(R.id.tvResultSectionHeader)
        btnConfirm          = findViewById(R.id.btnConfirm)
        rvSelections        = findViewById(R.id.rvSelections)
        resultScrollView    = findViewById(R.id.resultScrollView)

        selectionAdapter = com.example.myapplication.ui.MedicineSelectionAdapter { kw, med ->
            viewModel.selectMedicine(kw, med)
        }
        rvSelections.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        rvSelections.adapter = selectionAdapter

        switchAiMode        = findViewById(R.id.switchAiMode)
        tvAiModeStatus      = findViewById(R.id.tvAiModeStatus)
        aiModeDot           = findViewById(R.id.aiModeDot)

        recordingScreenGroup = findViewById(R.id.recordingScreenGroup)
        cartScreenGroup      = findViewById(R.id.cartScreenGroup)

        rvCartItems          = findViewById(R.id.rvCartItems)
        btnBackToRecording   = findViewById(R.id.btnBackToRecording)
        btnSendToPc          = findViewById(R.id.btnSendToPc)

        cartItemAdapter = com.example.myapplication.ui.CartItemAdapter(
            { name, qty -> viewModel.updateCartItemQuantity(name, qty) },
            { name -> viewModel.removeCartItem(name) }
        )
        rvCartItems.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        rvCartItems.adapter = cartItemAdapter

        modelDownloadCard = findViewById(R.id.modelDownloadCard)
        downloadProgressContainer = findViewById(R.id.downloadProgressContainer)
        downloadProgressBar = findViewById(R.id.downloadProgressBar)
        tvDownloadStatus = findViewById(R.id.tvDownloadStatus)
        btnDownloadInApp = findViewById(R.id.btnDownloadInApp)
        btnDownloadLink = findViewById(R.id.btnDownloadLink)
        btnSelectModelFile = findViewById(R.id.btnSelectModelFile)
        btnCancelAiMode = findViewById(R.id.btnCancelAiMode)
        etServerIp          = findViewById(R.id.etServerIp)
        etServerPort        = findViewById(R.id.etServerPort)
        tvPcConnectionStatus = findViewById(R.id.tvPcConnectionStatus)
        pcStatusDot         = findViewById(R.id.pcStatusDot)

        rvFormatTags        = findViewById(R.id.rvFormatTags)
        tvResetFormat       = findViewById(R.id.tvResetFormat)
        cgAvailableTags     = findViewById(R.id.cgAvailableTags)

        setupClickListeners()
        setupPcConnectionSettings()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkGemmaAvailability()
    }

    private fun setupClickListeners() {
        btnRecord.setOnClickListener {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                viewModel.startRecording()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        btnStop.setOnClickListener {
            viewModel.stopRecording()
        }

        btnClear.setOnClickListener {
            viewModel.clearTranscription()
        }

        switchAiMode.setOnCheckedChangeListener { _, _ ->
            viewModel.toggleAiMode()
        }

        val downloadUrl = "https://drive.google.com/uc?export=download&id=1dbxdYvBiS73lvIY_5d9v0-fwTbWeeooF"
        val browserUrl = "https://drive.google.com/file/d/1dbxdYvBiS73lvIY_5d9v0-fwTbWeeooF/view?usp=sharing"

        btnDownloadInApp.setOnClickListener {
            if (viewModel.isDownloading.value) {
                viewModel.cancelDownload()
            } else {
                viewModel.downloadGemmaModel(downloadUrl)
            }
        }

        btnDownloadLink.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(browserUrl))
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to open browser: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        btnSelectModelFile.setOnClickListener {
            selectModelLauncher.launch(arrayOf("*/*"))
        }

        btnCancelAiMode.setOnClickListener {
            viewModel.toggleAiMode()
        }

        btnConfirm.setOnClickListener {
            viewModel.confirmSelections()
        }

        btnBackToRecording.setOnClickListener {
            viewModel.navigateToRecording()
        }

        btnSendToPc.setOnClickListener {
            viewModel.sendCartToPc()
        }
    }

    private fun setupPcConnectionSettings() {
        etServerIp.setText(viewModel.serverIp.value)
        etServerPort.setText(viewModel.serverPort.value)

        etServerIp.addTextChangedListener(object : android.text.TextWatcher {
            private var isUpdating = false
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (isUpdating) return
                val text = s?.toString() ?: ""
                if (text.contains(":")) {
                    isUpdating = true
                    val colonIndex = text.lastIndexOf(':')
                    val ipOnly = text.substring(0, colonIndex).trim()
                    val portOnly = text.substring(colonIndex + 1).trim()
                    
                    etServerIp.setText(ipOnly)
                    try {
                        etServerIp.setSelection(ipOnly.length)
                    } catch (_: Exception) {}
                    
                    if (portOnly.isNotEmpty()) {
                        etServerPort.setText(portOnly)
                        viewModel.updateServerPort(portOnly)
                    }
                    viewModel.updateServerIp(ipOnly)
                    isUpdating = false
                } else {
                    viewModel.updateServerIp(text)
                }
            }
        })

        etServerPort.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                viewModel.updateServerPort(s?.toString() ?: "")
            }
        })

        // Initialize Typing Format Tag RecyclerView
        formatTagAdapter = com.example.myapplication.ui.FormatTagAdapter { position ->
            val current = viewModel.typingFormat.value.toMutableList()
            if (position in current.indices) {
                current.removeAt(position)
                viewModel.updateTypingFormat(current)
            }
        }
        rvFormatTags.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this, androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false)
        rvFormatTags.adapter = formatTagAdapter

        // Attach Drag-to-Reorder ItemTouchHelper
        val touchHelperCallback = object : androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
            androidx.recyclerview.widget.ItemTouchHelper.LEFT or androidx.recyclerview.widget.ItemTouchHelper.RIGHT,
            0
        ) {
            override fun onMove(
                recyclerView: androidx.recyclerview.widget.RecyclerView,
                viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder,
                target: androidx.recyclerview.widget.RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = viewHolder.adapterPosition
                val toPos = target.adapterPosition
                val current = viewModel.typingFormat.value.toMutableList()
                if (fromPos in current.indices && toPos in current.indices) {
                    val movedItem = current.removeAt(fromPos)
                    current.add(toPos, movedItem)
                    viewModel.updateTypingFormat(current)
                    return true
                }
                return false
            }
            override fun onSwiped(viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder, direction: Int) {}
        }
        val itemTouchHelper = androidx.recyclerview.widget.ItemTouchHelper(touchHelperCallback)
        itemTouchHelper.attachToRecyclerView(rvFormatTags)

        // Setup format reset
        tvResetFormat.setOnClickListener {
            viewModel.updateTypingFormat(listOf("NAME", "TAB", "QTY", "ENTER"))
        }

        // Setup available tags list
        setupAvailableFormatChips()
    }

    private fun setupAvailableFormatChips() {
        val availableTags = listOf(
            "NAME" to "+ Medicine Name",
            "TAB" to "+ Tab Key",
            "QTY" to "+ Quantity",
            "ENTER" to "+ Enter Key",
            "SPACE" to "+ Space Key",
            "COMMA" to "+ Comma (,)"
        )
        cgAvailableTags.removeAllViews()
        for ((code, label) in availableTags) {
            val chip = com.google.android.material.chip.Chip(this).apply {
                text = label
                isClickable = true
                isCheckable = false
                setChipBackgroundColorResource(android.R.color.transparent)
                chipStrokeWidth = 2f
                chipStrokeColor = ColorStateList.valueOf(Color.parseColor("#475569"))
                setTextColor(Color.parseColor("#CBD5E1"))
                
                setOnClickListener {
                    val current = viewModel.typingFormat.value.toMutableList()
                    current.add(code)
                    viewModel.updateTypingFormat(current)
                }
            }
            cgAvailableTags.addView(chip)
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.status.collectLatest { status ->
                        tvStatus.text = status
                        updateStatusUi(status)
                    }
                }

                launch {
                    viewModel.connectionStatus.collectLatest { status ->
                        tvPcConnectionStatus.text = status
                        if (status == "CONNECTED") {
                            tvPcConnectionStatus.setTextColor(Color.parseColor("#22C55E"))
                            pcStatusDot.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#22C55E"))
                        } else {
                            tvPcConnectionStatus.setTextColor(Color.parseColor("#EF4444"))
                            pcStatusDot.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#EF4444"))
                        }
                    }
                }

                launch {
                    viewModel.typingFormat.collectLatest { format ->
                        formatTagAdapter.submitList(format)
                    }
                }

                launch {
                    viewModel.timerText.collectLatest { timerText ->
                        tvTimer.text = timerText
                    }
                }

                launch {
                    viewModel.transcription.collectLatest { transcription ->
                        if (transcription.isEmpty()) {
                            tvTranscription.text = "Your transcription will appear here after you stop recording..."
                        } else {
                            tvTranscription.text = transcription
                        }
                    }
                }

                launch {
                    viewModel.isRecording.collectLatest { isRecording ->
                        updateButtonStates(isRecording, viewModel.isModelLoaded.value, viewModel.isTranscribing.value)
                    }
                }

                launch {
                    viewModel.isModelLoaded.collectLatest { isModelLoaded ->
                        updateButtonStates(viewModel.isRecording.value, isModelLoaded, viewModel.isTranscribing.value)
                    }
                }

                launch {
                    viewModel.isTranscribing.collectLatest { isTranscribing ->
                        updateButtonStates(viewModel.isRecording.value, viewModel.isModelLoaded.value, isTranscribing)
                    }
                }

                launch {
                    viewModel.errorEvent.collectLatest { errorMsg ->
                        Toast.makeText(this@MainActivity, errorMsg, Toast.LENGTH_LONG).show()
                    }
                }

                launch {
                    viewModel.cartItems.collectLatest { cartList ->
                        cartItemAdapter.submitList(cartList)
                    }
                }

                launch {
                    combine(
                        viewModel.isAiMode,
                        viewModel.isGemmaAvailable,
                        viewModel.currentScreen
                    ) { aiOn, isAvailable, screen ->
                        Triple(aiOn, isAvailable, screen)
                    }.collectLatest { (aiOn, isAvailable, screen) ->
                        if (aiOn && !isAvailable) {
                            modelDownloadCard.visibility = View.VISIBLE
                            recordingScreenGroup.visibility = View.GONE
                            cartScreenGroup.visibility = View.GONE
                        } else {
                            modelDownloadCard.visibility = View.GONE
                            if (screen == MainViewModel.Screen.RECORDING) {
                                recordingScreenGroup.visibility = View.VISIBLE
                                cartScreenGroup.visibility = View.GONE
                            } else {
                                recordingScreenGroup.visibility = View.GONE
                                cartScreenGroup.visibility = View.VISIBLE
                            }
                        }
                    }
                }

                launch {
                    viewModel.isDownloading.collectLatest { isDownloading ->
                        btnDownloadLink.isEnabled = !isDownloading
                        btnSelectModelFile.isEnabled = !isDownloading

                        if (isDownloading) {
                            downloadProgressContainer.visibility = View.VISIBLE
                            btnDownloadInApp.text = "Cancel Download"
                        } else {
                            downloadProgressContainer.visibility = View.GONE
                            btnDownloadInApp.text = "Download in App"
                        }
                    }
                }

                launch {
                    viewModel.downloadProgress.collectLatest { progress ->
                        downloadProgressBar.progress = progress
                    }
                }

                launch {
                    viewModel.downloadStatusText.collectLatest { text ->
                        tvDownloadStatus.text = text
                    }
                }



                launch {
                    viewModel.medicineSelections.collectLatest { selections ->
                        selectionAdapter.submitList(selections)
                        
                        // Toggle visibility based on mode and availability of selections
                        if (!viewModel.isAiMode.value && selections.isNotEmpty()) {
                            rvSelections.visibility = View.VISIBLE
                            resultScrollView.visibility = View.GONE
                            btnConfirm.visibility = View.VISIBLE
                        } else {
                            rvSelections.visibility = View.GONE
                            resultScrollView.visibility = View.VISIBLE
                            btnConfirm.visibility = View.GONE
                        }
                    }
                }

                launch {
                    viewModel.isMedicinesLoaded.collectLatest { loaded ->
                        if (loaded) {
                            selectionAdapter.updateMedicineList(viewModel.medicineList)
                        }
                    }
                }

                launch {
                    viewModel.finalMedicines.collectLatest { result ->
                        tvFinalMedicine.text = result.ifEmpty {
                            if (viewModel.isAiMode.value) "Gemma result will appear here..."
                            else "Fuzzy match result will appear here..."
                        }
                    }
                }

                launch {
                    kotlinx.coroutines.flow.combine(
                        viewModel.isAiMode,
                        viewModel.isGemmaModelLoaded,
                        viewModel.isGemmaLoading
                    ) { aiOn, loaded, loading ->
                        Triple(aiOn, loaded, loading)
                    }.collectLatest { (aiOn, loaded, loading) ->
                        // Update switch without triggering the listener again
                        if (switchAiMode.isChecked != aiOn) switchAiMode.isChecked = aiOn

                        // Section header label
                        tvResultSectionHeader.text = if (aiOn) "GEMMA RESULT" else "FUZZY MATCH"

                        // Status badge text, color and indicator dot color
                        if (aiOn) {
                            when {
                                loading -> {
                                    tvAiModeStatus.text = "LOADING..."
                                    tvAiModeStatus.setTextColor(Color.parseColor("#F59E0B"))
                                    aiModeDot.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F59E0B"))
                                }
                                loaded -> {
                                    tvAiModeStatus.text = "ON"
                                    tvAiModeStatus.setTextColor(Color.parseColor("#22C55E"))
                                    aiModeDot.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#22C55E"))
                                }
                                else -> {
                                    tvAiModeStatus.text = "NOT READY"
                                    tvAiModeStatus.setTextColor(Color.parseColor("#EF4444"))
                                    aiModeDot.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#EF4444"))
                                }
                            }
                        } else {
                            tvAiModeStatus.text = "OFF"
                            tvAiModeStatus.setTextColor(Color.parseColor("#475569"))
                            aiModeDot.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#475569"))
                        }

                        // Clear placeholder text to reflect new mode
                        if (tvFinalMedicine.text.endsWith("here...")) {
                            tvFinalMedicine.text = if (aiOn) "Gemma result will appear here..."
                                                   else "Fuzzy match result will appear here..."
                        }
                    }
                }

                launch {
                    viewModel.runningTasks.collectLatest { tasks ->
                        updateTasksUi(tasks)
                    }
                }
            }
        }
    }

    private fun updateTasksUi(tasks: List<String>) {
        val container = findViewById<android.widget.LinearLayout>(R.id.tasksListContainer) ?: return
        container.removeAllViews()
        
        if (tasks.isEmpty()) {
            val tvNoTasks = TextView(this).apply {
                text = "No active background processes (Idle)"
                setTextColor(Color.parseColor("#64748B"))
                textSize = 13f
            }
            container.addView(tvNoTasks)
        } else {
            for (task in tasks) {
                val taskLayout = android.widget.LinearLayout(this).apply {
                    orientation = android.widget.LinearLayout.HORIZONTAL
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, 4.toPx(), 0, 4.toPx())
                    }
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }

                val progressBar = android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleSmall).apply {
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        16.toPx(),
                        16.toPx()
                    ).apply {
                        marginEnd = 8.toPx()
                    }
                    indeterminateTintList = ColorStateList.valueOf(Color.parseColor("#3B82F6"))
                }

                val tvTask = TextView(this).apply {
                    text = task
                    setTextColor(Color.parseColor("#CBD5E1"))
                    textSize = 14f
                }

                taskLayout.addView(progressBar)
                taskLayout.addView(tvTask)
                container.addView(taskLayout)
            }
        }
    }

    private fun Int.toPx(): Int {
        val density = resources.displayMetrics.density
        return (this * density).toInt()
    }

    private fun updateStatusUi(status: String) {
        val color = when (status) {
            "Ready", "Completed" -> "#10B981" // Green
            "Recording..." -> "#EF4444"       // Red
            "Transcribing..." -> "#F59E0B"    // Orange
            "Loading model...", "Initializing..." -> "#94A3B8" // Grey
            else -> "#EF4444"                 // Red for errors
        }
        statusDot.backgroundTintList = ColorStateList.valueOf(Color.parseColor(color))
    }

    private fun updateButtonStates(isRecording: Boolean, isModelLoaded: Boolean, isTranscribing: Boolean) {
        btnRecord.isEnabled = !isRecording && !isTranscribing && isModelLoaded
        btnStop.isEnabled = isRecording
        btnClear.isEnabled = !isRecording && !isTranscribing
    }
}