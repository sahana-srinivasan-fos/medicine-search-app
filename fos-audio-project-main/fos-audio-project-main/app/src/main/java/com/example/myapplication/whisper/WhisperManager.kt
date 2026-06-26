package com.example.myapplication.whisper

import android.content.Context
import android.util.Log
import com.example.myapplication.media.decodeWaveFile
import com.whispercpp.whisper.WhisperContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class WhisperManager(private val context: Context) {
    private var whisperContext: WhisperContext? = null
    private val modelFile = File(context.filesDir, "models/ggml-base.en.bin")

    suspend fun initModel(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!modelFile.exists()) {
                Log.d(TAG, "Model file not found in storage. Copying from assets...")
                copyModelFromAssets()
            } else {
                Log.d(TAG, "Model file already exists in internal storage.")
            }
            Log.d(TAG, "Loading model from ${modelFile.absolutePath}...")
            whisperContext = WhisperContext.createContextFromFile(modelFile.absolutePath)
            Log.d(TAG, "Model loaded successfully.")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model", e)
            false
        }
    }

    private fun copyModelFromAssets() {
        val modelsDir = modelFile.parentFile
        if (modelsDir != null && !modelsDir.exists()) {
            modelsDir.mkdirs()
        }
        context.assets.open("models/ggml-base.en.bin").use { input ->
            modelFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        Log.d(TAG, "Model copied successfully to ${modelFile.absolutePath}")
    }

    suspend fun transcribe(file: File): String = withContext(Dispatchers.Default) {
        val currentContext = whisperContext ?: throw IllegalStateException("Whisper context not initialized")
        val data = decodeWaveFile(file)
        Log.d(TAG, "Starting transcription for ${file.name} (${data.size} samples)...")
        val result = currentContext.transcribeData(data, printTimestamp = false)
        Log.d(TAG, "Transcription complete: $result")
        result
    }

    suspend fun release() = withContext(Dispatchers.Default) {
        whisperContext?.release()
        whisperContext = null
    }

    companion object {
        private const val TAG = "WhisperManager"
    }
}
