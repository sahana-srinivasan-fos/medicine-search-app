package com.example.myapplication.whisper

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class GemmaManager(private val context: Context) : AutoCloseable {

    private var llmInference: LlmInference? = null
    private val modelPath = (context.getExternalFilesDir(null)?.absolutePath ?: "") + "/gemma3n.task"

    companion object {
        private const val TAG = "GemmaManager"
        var lastInitError: String? = null

        // Increased to 2048 to handle larger prompts with up to 50 candidates safely without crashing the native engine
        private const val MAX_TOKENS = 2048
    }

    init {
        lastInitError = null
        if (context.getExternalFilesDir(null) == null) {
            lastInitError = "External files directory is not available."
            Log.e(TAG, lastInitError!!)
        } else {
            val modelFile = File(modelPath)
            var modelReady = true

            if (!modelFile.exists()) {
                Log.d(TAG, "Model file not found at ${modelFile.absolutePath}. Checking assets...")
                val assetName = "gemma3n.task"
                val hasAsset = try {
                    context.assets.open(assetName).use { }
                    true
                } catch (e: Exception) {
                    false
                }

                if (hasAsset) {
                    try {
                        copyAssetToFile(context, assetName, modelFile)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to copy model from assets", e)
                        lastInitError = "Failed to copy model from assets: ${e.localizedMessage ?: e.message}"
                        modelReady = false
                    }
                } else {
                    lastInitError = "gemma3n.task file missing. Please place it at: ${modelFile.absolutePath} or package it in assets."
                    Log.e(TAG, lastInitError!!)
                    modelReady = false
                }
            }

            if (modelReady) {
                try {
                    val options = LlmInference.LlmInferenceOptions.builder()
                        .setModelPath(modelPath)
                        .setMaxTokens(MAX_TOKENS)
                        .setPreferredBackend(LlmInference.Backend.CPU) // CPU backend first for stability
                        .build()

                    llmInference = LlmInference.createFromOptions(context, options)
                    Log.d(TAG, "Gemma loaded successfully with CPU backend")
                } catch (cpuError: Throwable) {
                    Log.w(TAG, "Gemma CPU initialization failed, falling back to GPU: ${cpuError.message}")
                    try {
                        val options = LlmInference.LlmInferenceOptions.builder()
                            .setModelPath(modelPath)
                            .setMaxTokens(MAX_TOKENS)
                            .setPreferredBackend(LlmInference.Backend.GPU) // GPU fallback
                            .build()

                        llmInference = LlmInference.createFromOptions(context, options)
                        Log.d(TAG, "Gemma loaded successfully with GPU backend")
                    } catch (gpuError: Throwable) {
                        Log.e(TAG, "Gemma GPU initialization also failed: ${gpuError.message}", gpuError)
                        lastInitError = "Gemma load failed: ${gpuError.localizedMessage ?: gpuError.message}"
                    }
                }
            }
        }
    }

    private fun copyAssetToFile(context: Context, assetName: String, destFile: File) {
        val tempFile = File(destFile.absolutePath + ".tmp")
        if (tempFile.exists()) tempFile.delete()

        Log.d(TAG, "Copying $assetName from assets to ${destFile.absolutePath}...")
        context.assets.open(assetName).use { input ->
            tempFile.outputStream().use { output ->
                val buffer = ByteArray(1024 * 1024) // 1MB buffer
                var bytesRead: Int
                var totalBytesCopied = 0L
                val startTime = System.currentTimeMillis()
                var lastLogTime = startTime

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalBytesCopied += bytesRead

                    val now = System.currentTimeMillis()
                    if (now - lastLogTime > 2000) { // Log progress every 2 seconds
                        val pct = (totalBytesCopied * 100) / 3136226711L
                        Log.d(TAG, "Copying model: $pct% ($totalBytesCopied bytes)")
                        lastLogTime = now
                    }
                }
            }
        }

        if (destFile.exists()) destFile.delete()
        if (tempFile.renameTo(destFile)) {
            Log.d(TAG, "Successfully copied and renamed model file.")
        } else {
            tempFile.delete()
            throw java.io.IOException("Failed to rename temporary model file to ${destFile.name}")
        }
    }

    /**
     * Given a spoken [transcription] and a [candidates] list (pre-filtered by BM25 + phonetic),
     * ask Gemma to select the single best matching medicine name per spoken drug from the list.
     *
     * Returns Gemma's raw multi-line output, or null on failure.
     */
    suspend fun reRankMedicines(transcription: String, candidates: List<String>): String? {
        val inference = llmInference ?: return null
        if (candidates.isEmpty()) return null

        return withContext(Dispatchers.IO) {
            try {
                val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                    .setTopK(1)
                    .setTemperature(0.1f)
                    .build()

                val scanSession = LlmInferenceSession.createFromOptions(inference, sessionOptions)
                scanSession.use { s ->
                    val candidatesListText = candidates.joinToString("\n")
                    val prompt = buildPrompt(transcription, candidatesListText)

                    Log.d(TAG, "Prompt sent to Gemma:\n$prompt")
                    s.addQueryChunk(prompt)
                    val response = s.generateResponse().trim()
                    Log.d(TAG, "Gemma response: $response")
                    response
                }
            } catch (e: Exception) {
                Log.e(TAG, "Gemma inference failed: ${e.message}", e)
                null
            }
        }
    }

    /**
     * Constructs the Gemma prompt with few-shot examples showing known speech-to-text
     * error patterns, plus strict output format constraints.
     */
    private fun buildPrompt(transcription: String, candidatesListText: String): String = """
You will be given a spoken transcript segment and a list of candidate medicine names from a pharmacy database.
Your job is to select the EXACT medicine name from the candidate list that best matches what was spoken,
accounting for common speech-to-text errors.

IMPORTANT RULES:
1. Select ONLY names that appear EXACTLY as written in the candidate list below.
2. Return ONLY the single best matching name for each distinct medicine mentioned in the transcript.
3. Do NOT return multiple alternatives or dosage variants for the same spoken medicine.
4. If no candidate matches a spoken medicine, output NONE for that line.
5. Output ONLY the selected medicine names, one per line. No explanations, no numbering, no extra text.
6. Your output MUST contain exactly one line for each distinct medicine mentioned in the transcript. For example, if there are 6 medicines mentioned in the transcript, you MUST output exactly 6 lines of text, with each line in the same order as in the transcript.

EXAMPLES of common speech-to-text errors and their correct matches:
  Transcript: "dota 650"          → DOLO 650MG TAB
  Transcript: "9 r bact"          → NITROBACT 100MG CAP 10S
  Transcript: "augmenting 625"    → AUGMENTIN 625 DUO TAB 6S
  Transcript: "tea bag ointment"  → T BACT OINT 15 GM
  Transcript: "crocine 500"       → CROCIN 500MG TAB
  Transcript: "zolo 650"          → DOLO 650MG TAB
  Transcript: "nitro bag 100"     → NITROBACT 100MG CAP 10S
  Transcript: "dota 650, 9 r bact, zolo 650" →
  DOLO 650MG TAB
  NITROBACT 100MG CAP 10S
  DOLO 650MG TAB

Candidate medicine list:
$candidatesListText

Speech transcript:
"$transcription"

Task: For each medicine mentioned in the transcript, select and output its single best matching
name from the candidate list, one per line. Output NONE if there is no match.
""".trimIndent()

    override fun close() {
        try {
            llmInference?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing llmInference: ${e.message}")
        }
    }
}
