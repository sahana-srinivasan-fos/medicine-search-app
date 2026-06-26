package com.example.myapplication.whisper

import android.util.Log
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * BM25 (Okapi BM25) index over a list of medicine names.
 *
 * Build once after loading medicines.json, then call [score] or [topN] per query keyword.
 * All string comparison is done in lowercase.
 *
 * Parameters k1=1.5, b=0.75 are standard BM25 defaults.
 */
class Bm25Index(private val medicineList: List<String>) {

    companion object {
        private const val TAG = "Bm25Index"
        private const val K1 = 1.5
        private const val B = 0.75
    }

    // tokenised documents: docTokens[i] = token list for medicineList[i]
    private val docTokens: List<List<String>>

    // term -> document-frequency (number of docs containing that term)
    private val df: Map<String, Int>

    // IDF per term: ln((N - df + 0.5) / (df + 0.5) + 1)
    private val idf: Map<String, Double>

    // term frequency per doc: tf[i][term] = count
    private val tf: List<Map<String, Int>>

    // average document length (in tokens)
    private val avgDl: Double

    private val n: Int get() = medicineList.size

    init {
        val startMs = System.currentTimeMillis()

        docTokens = medicineList.map { tokenize(it) }

        // Build term frequency per doc
        tf = docTokens.map { tokens ->
            val counts = mutableMapOf<String, Int>()
            for (t in tokens) counts[t] = (counts[t] ?: 0) + 1
            counts
        }

        // Build document frequency
        val dfMut = mutableMapOf<String, Int>()
        for (docTf in tf) {
            for (term in docTf.keys) {
                dfMut[term] = (dfMut[term] ?: 0) + 1
            }
        }
        df = dfMut

        // Compute IDF
        val nD = n.toDouble()
        idf = df.mapValues { (_, docFreq) ->
            ln((nD - docFreq + 0.5) / (docFreq + 0.5) + 1.0)
        }

        avgDl = if (docTokens.isEmpty()) 1.0
        else docTokens.sumOf { it.size }.toDouble() / docTokens.size

        Log.d(TAG, "BM25 index built: ${n} docs, ${df.size} unique terms in ${System.currentTimeMillis() - startMs}ms")
    }

    /**
     * Score all documents against [query] and return a list of (medicineIndex, bm25Score)
     * sorted descending by score. Only documents with score > 0 are included.
     */
    fun score(query: String): List<Pair<Int, Double>> {
        val queryTokens = tokenize(query)
        if (queryTokens.isEmpty()) return emptyList()

        val scores = mutableListOf<Pair<Int, Double>>()
        for (i in 0 until n) {
            val docTf = tf[i]
            val dl = docTokens[i].size.toDouble()
            var s = 0.0
            for (qt in queryTokens) {
                val termIdf = idf[qt] ?: continue
                val termTf = (docTf[qt] ?: 0).toDouble()
                if (termTf == 0.0) continue
                val numerator = termTf * (K1 + 1.0)
                val denominator = termTf + K1 * (1.0 - B + B * dl / avgDl)
                s += termIdf * (numerator / denominator)
            }
            if (s > 0.0) scores.add(Pair(i, s))
        }

        scores.sortByDescending { it.second }
        return scores
    }

    /**
     * Return the top [n] (medicineIndex, score) pairs for [query], sorted descending.
     */
    fun topN(query: String, n: Int): List<Pair<Int, Double>> {
        return score(query).take(n)
    }
}

/**
 * Shared tokenizer used by BM25 and HybridRetriever.
 *
 * Splits on spaces, hyphens, slashes, and alpha/numeric boundaries.
 * Filters tokens shorter than 2 characters.
 */
fun tokenize(text: String): List<String> {
    // Lowercase then split on non-alphanumeric
    val lower = text.lowercase()
    // First split on separators
    val parts = lower.split(Regex("[^a-z0-9]+"))
    // Then further split each part on alpha/numeric boundary
    val tokens = mutableListOf<String>()
    for (part in parts) {
        if (part.isEmpty()) continue
        // Split "nitrobact100" -> ["nitrobact", "100"]
        val sub = Regex("([a-z]+)(\\d+)").find(part)
        if (sub != null && sub.range.first == 0 && sub.range.last == part.length - 1) {
            val alpha = sub.groupValues[1]
            val digit = sub.groupValues[2]
            if (alpha.length >= 2) tokens.add(alpha)
            if (digit.length >= 2) tokens.add(digit)
        } else {
            if (part.length >= 2) tokens.add(part)
        }
    }
    return tokens
}
