package com.example.myapplication

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.whisper.Bm25Index
import com.example.myapplication.whisper.PhoneticIndex
import com.example.myapplication.whisper.HybridRetriever
import com.example.myapplication.whisper.CorrectionRegistry
import com.example.myapplication.whisper.GemmaManager
import com.example.myapplication.ui.MainViewModel
import org.json.JSONArray
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import android.util.Log
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun testHybridRetriever() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val jsonString = appContext.assets.open("medicines.json").use { it.bufferedReader().readText() }
        val jsonArray = JSONArray(jsonString)
        val medicineList = mutableListOf<String>()
        for (i in 0 until jsonArray.length()) {
            medicineList.add(jsonArray.getString(i))
        }
        
        val bm25Index = Bm25Index(medicineList)
        val phoneticIndex = PhoneticIndex(medicineList)
        val correctionRegistry = CorrectionRegistry(appContext)
        val retriever = HybridRetriever(medicineList, bm25Index, phoneticIndex, correctionRegistry)
        
        // Let's test the specific user keywords
        val queries = listOf("t-bact", "nitrobact 100", "dolo 650")
        for (query in queries) {
            val normalizedQuery = MainViewModel.normalizeSegment(query)
            val keywords = normalizedQuery.split(Regex("[^a-zA-Z0-9]+"))
                .flatMap { token ->
                    val m = Regex("([a-zA-Z]+)(\\d+)").matchEntire(token)
                    if (m != null) listOf(m.groupValues[1], m.groupValues[2])
                    else listOf(token)
                }
                .filter { it.length >= 3 && !it.all { char -> char.isDigit() } }
            
            val output = retriever.retrieve(query, normalizedQuery, keywords)
            Log.d("TEST_RETRIEVER", "Query: $query")
            Log.d("TEST_RETRIEVER", "Keywords: $keywords")
            Log.d("TEST_RETRIEVER", "High-confidence direct: ${output.highConfResolved}")
            Log.d("TEST_RETRIEVER", "Gemma keywords: ${output.gemmaKeywords}")
            Log.d("TEST_RETRIEVER", "Gemma candidates: ${output.gemmaCandidates}")
        }
    }

    @Test
    fun testGemmaManager() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        try {
            val gemmaManager = GemmaManager(appContext)
            assertNotNull(gemmaManager)
            if (GemmaManager.lastInitError != null) {
                Log.e("TEST_GEMMA", "Gemma initialization error: ${GemmaManager.lastInitError}")
            } else {
                Log.d("TEST_GEMMA", "Gemma initialized successfully")
                
                // Let's run a test reranking!
                val candidates = listOf(
                    "DOLO DROPS 15ML",
                    "DOLO 650MG TAB",
                    "DOLO 250MG SYP 60ML",
                    "P-650 TAB",
                    "CALPOL 650MG TAB"
                )
                runBlocking {
                    val result = gemmaManager.reRankMedicines("dolo 650", candidates)
                    Log.d("TEST_GEMMA", "Rerank result: $result")
                }
            }
        } catch (e: Exception) {
            Log.e("TEST_GEMMA", "Exception in testGemmaManager", e)
        }
    }

    @Test
    fun testEndToEndPipeline() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        try {
            val jsonString = appContext.assets.open("medicines.json").use { it.bufferedReader().readText() }
            val jsonArray = JSONArray(jsonString)
            val medicineList = mutableListOf<String>()
            for (i in 0 until jsonArray.length()) {
                medicineList.add(jsonArray.getString(i))
            }
            
            val bm25Index = Bm25Index(medicineList)
            val phoneticIndex = PhoneticIndex(medicineList)
            val correctionRegistry = CorrectionRegistry(appContext)
            val retriever = HybridRetriever(medicineList, bm25Index, phoneticIndex, correctionRegistry)
            val gemmaManager = GemmaManager(appContext)
            
            // Full transcription from user request
            val query = "-Dolor 650 nitro backed 100 T backed Thrombo Fobe Augment in 625."
            val normalizedQuery = MainViewModel.normalizeSegment(query)
            val keywords = normalizedQuery.split(Regex("[^a-zA-Z0-9]+"))
                .flatMap { token ->
                    val m = Regex("([a-zA-Z]+)(\\d+)").matchEntire(token)
                    if (m != null) listOf(m.groupValues[1], m.groupValues[2])
                    else listOf(token)
                }
                .filter { it.length >= 3 && !it.all { char -> char.isDigit() } }
            
            val retrieval = retriever.retrieve(query, normalizedQuery, keywords)
            val directOutput = retrieval.highConfResolved.values.toMutableList()
            val gemmaVerified = mutableListOf<String>()
            
            if (retrieval.gemmaCandidates.isNotEmpty()) {
                runBlocking {
                    val gemmaOutput = gemmaManager.reRankMedicines(normalizedQuery, retrieval.gemmaCandidates)
                    Log.d("TEST_E2E", "Gemma raw output:\n$gemmaOutput")
                    if (gemmaOutput != null) {
                        val verified = retriever.verifyGemmaOutput(
                            gemmaRawOutput   = gemmaOutput,
                            allCandidates    = retrieval.gemmaCandidates,
                            gemmaKeywords    = retrieval.gemmaKeywords,
                            perKeywordDebug  = retrieval.perKeywordDebug
                        )
                        gemmaVerified.addAll(verified)
                    }
                }
            }
            
            val finalOutput = (directOutput + gemmaVerified).distinct()
            val deduplicatedOutput = MainViewModel.deduplicateByBrand(finalOutput, query, retrieval.gemmaCandidates)
            Log.d("TEST_E2E", "Final output results:")
            deduplicatedOutput.forEach { Log.d("TEST_E2E", "  - $it") }
        } catch (e: Exception) {
            Log.e("TEST_E2E", "Exception in testEndToEndPipeline", e)
        }
    }
}