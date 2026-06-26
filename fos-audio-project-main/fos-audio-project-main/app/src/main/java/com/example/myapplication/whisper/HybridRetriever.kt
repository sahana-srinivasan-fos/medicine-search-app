package com.example.myapplication.whisper

import android.util.Log

/**
 * Hybrid candidate retriever combining BM25 lexical scoring and Double Metaphone phonetic matching.
 *
 * For each keyword:
 *   1. Get top-N candidates by BM25 score (lexical relevance)
 *   2. Get all medicines sharing a phonetic code with the keyword
 *   3. Merge both sets, cap at [perKeywordCap] per keyword
 *   4. Assess whether the top candidate is "high confidence" (skip Gemma for it)
 *
 * Thread-safe for concurrent reads after construction (indices are immutable).
 */
class HybridRetriever(
    private val medicineList: List<String>,
    private val bm25Index: Bm25Index,
    private val phoneticIndex: PhoneticIndex,
    private val correctionRegistry: CorrectionRegistry
) {

    companion object {
        private const val TAG = "HybridRetriever"

        /** Score bonus given to a medicine if it was previously confirmed by the user for this keyword. */
        const val USER_PREF_BONUS = 50.0

        /** BM25 absolute score threshold for high-confidence direct selection (skip Gemma). */
        const val HIGH_CONF_ABS_THRESHOLD = 8.0

        /** Ratio of top score to second score required for high-confidence selection. */
        const val HIGH_CONF_RATIO = 2.0

        /** Max candidates per keyword fed into the merged list. */
        const val PER_KEYWORD_CAP = 15

        /** Max total candidates sent to Gemma. */
        const val TOTAL_CANDIDATES_CAP = 50
    }

    /**
     * Result of retrieving candidates for a single keyword.
     *
     * @param candidates  List of (medicineIndex, bm25Score) sorted descending, capped at [PER_KEYWORD_CAP]
     * @param topScore    BM25 score of the top candidate (0.0 if none)
     * @param secondScore BM25 score of the second candidate (0.0 if fewer than 2)
     * @param phoneticExactMatch True if the keyword's primary phonetic code exactly matches
     *                           the top BM25 candidate's phonetic code
     */
    data class CandidateResult(
        val keyword: String,
        val candidates: List<Pair<Int, Double>>,
        val topScore: Double,
        val secondScore: Double,
        val phoneticExactMatch: Boolean
    )

    /**
     * Retrieve up to [PER_KEYWORD_CAP] candidates for [keyword] by merging BM25 + phonetic results.
     */
    fun getCandidates(keyword: String): CandidateResult {
        // 0. Check User Correction Registry (Self-Learning)
        val userLearnedName = correctionRegistry.getCorrection(keyword)
        val userLearnedIdx = if (userLearnedName != null) {
            medicineList.indexOf(userLearnedName)
        } else -1

        // 1. BM25 top hits
        val bm25Hits = bm25Index.topN(keyword, PER_KEYWORD_CAP)  // (idx, score)

        // 2. Phonetic lookup — all matching medicine indices
        val phoneticHits = phoneticIndex.lookup(keyword)           // Set<Int>

        // Sort phonetic hits by token set ratio to keyword descending (better than partial ratio)
        val sortedPhonetic = phoneticHits.map { idx ->
            val name = medicineList[idx]
            val score = StringSimilarity.tokenSetRatio(keyword, name)
            Pair(idx, score)
        }.sortedByDescending { it.second }

        // 3. Merge: Learned first, then BM25 (scored), then phonetic-only hits
        val seen = mutableSetOf<Int>()
        val merged = mutableListOf<Pair<Int, Double>>()

        // Priority 1: Learned from user
        if (userLearnedIdx != -1) {
            seen.add(userLearnedIdx)
            merged.add(Pair(userLearnedIdx, USER_PREF_BONUS))
            Log.d(TAG, "PREFERENCE MATCH [$keyword] -> ${medicineList[userLearnedIdx]}")
        }

        // Priority 2: BM25 hits
        for ((idx, score) in bm25Hits) {
            if (seen.add(idx)) {
                // If it's a BM25 hit but also learned (though handled above), we don't repeat
                merged.add(Pair(idx, score))
            }
        }

        // Priority 3: Phonetic hits
        for ((idx, simScore) in sortedPhonetic) {
            if (seen.add(idx)) {
                merged.add(Pair(idx, 0.5 * simScore))
            }
        }

        // Cap at PER_KEYWORD_CAP
        val capped = merged.take(PER_KEYWORD_CAP)

        val topScore = capped.firstOrNull()?.second ?: 0.0
        val secondScore = capped.getOrNull(1)?.second ?: 0.0

        // 4. Phonetic exact match check against top candidate
        val phoneticExact = if (capped.isNotEmpty()) {
            phoneticIndex.exactPrimaryMatch(keyword, medicineList[capped[0].first])
        } else false

        return CandidateResult(keyword, capped, topScore, secondScore, phoneticExact)
    }

    /**
     * Returns true if [result] is high-confidence enough to skip Gemma.
     * Conditions (all must hold):
     *  - Top BM25 score ≥ HIGH_CONF_ABS_THRESHOLD
     *  - Top score ≥ HIGH_CONF_RATIO × second score (clear winner)
     *  - Phonetic code of keyword exactly matches top candidate
     */
    fun isHighConfidence(result: CandidateResult): Boolean {
        return result.topScore >= HIGH_CONF_ABS_THRESHOLD &&
                result.topScore >= HIGH_CONF_RATIO * result.secondScore &&
                result.phoneticExactMatch
    }

    /**
     * Run the full hybrid retrieval for all [keywords] and return a [RetrievalOutput]
     * splitting them into high-confidence (direct output) and Gemma candidates.
     *
     * Also emits a [QueryDebugLog] in DEBUG builds via logcat.
     */
    fun retrieve(
        rawTranscript: String,
        normalizedQuery: String,
        keywords: List<String>
    ): RetrievalOutput {
        val highConfResolved = mutableMapOf<String, String>()    // keyword -> medicine name
        val gemmaKeywords    = mutableListOf<String>()
        val gemmaCandidates  = mutableListOf<String>()
        val addedToGemma     = mutableSetOf<String>()

        val perKeywordDebug = mutableMapOf<String, List<Pair<String, Double>>>()
        val perKeywordPhonetic = mutableMapOf<String, Boolean>()

        val gemmaResults = mutableListOf<CandidateResult>()

        for (kw in keywords) {
            val result = getCandidates(kw)

            // Build debug info per keyword
            val debugCandidates = result.candidates.map { (idx, score) ->
                Pair(medicineList[idx], score)
            }
            perKeywordDebug[kw] = debugCandidates
            perKeywordPhonetic[kw] = result.phoneticExactMatch

            if (isHighConfidence(result)) {
                // Direct selection — skip Gemma
                val topName = medicineList[result.candidates[0].first]
                highConfResolved[kw] = topName
                Log.d(TAG, "HIGH-CONF [$kw] -> $topName (score=${result.topScore})")
                // Also add to gemmaCandidates as a match anchor to prevent false fuzzy matches
                if (addedToGemma.add(topName)) {
                    gemmaCandidates.add(topName)
                }
            } else {
                gemmaKeywords.add(kw)
                gemmaResults.add(result)
            }
        }

        // Add candidates to Gemma list using round-robin to prevent candidate starvation
        var index = 0
        var addedInLastPass = true
        while (gemmaCandidates.size < TOTAL_CANDIDATES_CAP && addedInLastPass) {
            addedInLastPass = false
            for (res in gemmaResults) {
                if (index < res.candidates.size) {
                    val idx = res.candidates[index].first
                    val name = medicineList[idx]
                    if (addedToGemma.add(name)) {
                        if (gemmaCandidates.size < TOTAL_CANDIDATES_CAP) {
                            gemmaCandidates.add(name)
                        }
                    }
                    addedInLastPass = true
                }
            }
            index++
        }

        if (Log.isLoggable("PharmaCam_DEBUG", Log.DEBUG)) {
            emitDebugLog(
                rawTranscript, normalizedQuery, keywords,
                perKeywordDebug, perKeywordPhonetic,
                highConfResolved, gemmaCandidates
            )
        }

        return RetrievalOutput(
            highConfResolved = highConfResolved,
            gemmaKeywords = gemmaKeywords,
            gemmaCandidates = gemmaCandidates,
            perKeywordDebug = perKeywordDebug
        )
    }

    /**
     * Given Gemma's raw output and the candidate list it was given, verify each
     * Gemma-selected name against the full medicine database.
     * - If Gemma returns a name not in [allCandidates], fall back to the top BM25
     *   candidate for the corresponding keyword.
     * - If Gemma returns "NONE", skip that line.
     *
     * @param gemmaRawOutput Raw multi-line output from Gemma
     * @param allCandidates  The candidate list sent to Gemma (used for fuzzy verification)
     * @param gemmaKeywords  Keywords that were sent to Gemma (for fallback)
     * @param perKeywordDebug Per-keyword candidate list with scores (for fallback)
     * @return Verified list of medicine names
     */
    fun verifyGemmaOutput(
        gemmaRawOutput: String,
        allCandidates: List<String>,
        gemmaKeywords: List<String>,
        perKeywordDebug: Map<String, List<Pair<String, Double>>>
    ): List<String> {
        val parsedLines = gemmaRawOutput.split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.uppercase() != "NONE" }

        val verified = mutableListOf<String>()
        for (line in parsedLines) {
            // Fuzzy match against the candidate list
            var bestMatch: String? = null
            var bestScore = 0.0
            for (cand in allCandidates) {
                val score = StringSimilarity.partialRatio(line, cand)
                if (score > bestScore && score >= 0.75) {
                    bestScore = score
                    bestMatch = cand
                }
            }
            if (bestMatch != null) {
                verified.add(bestMatch)
                Log.d(TAG, "VERIFIED: '$line' -> '$bestMatch' (score=$bestScore)")
            } else {
                Log.w(TAG, "GEMMA OUTPUT NOT VERIFIED: '$line' — discarded")
            }
        }

        // Fallback: for any Gemma keyword with zero verified results, use top BM25 candidate
        if (verified.isEmpty() && gemmaKeywords.isNotEmpty()) {
            for (kw in gemmaKeywords) {
                val topCand = perKeywordDebug[kw]?.firstOrNull()?.first
                if (topCand != null && topCand !in verified) {
                    Log.d(TAG, "FALLBACK [$kw] -> $topCand")
                    verified.add(topCand)
                }
            }
        }

        return verified
    }

    private fun emitDebugLog(
        rawTranscript: String,
        normalizedQuery: String,
        keywords: List<String>,
        perKeywordDebug: Map<String, List<Pair<String, Double>>>,
        perKeywordPhonetic: Map<String, Boolean>,
        highConfResolved: Map<String, String>,
        gemmaCandidates: List<String>
    ) {
        val sb = StringBuilder()
        sb.appendLine("=== PharmaCam DEBUG LOG ===")
        sb.appendLine("Raw transcript   : $rawTranscript")
        sb.appendLine("Normalized query : $normalizedQuery")
        sb.appendLine("Keywords         : $keywords")
        sb.appendLine()
        sb.appendLine("--- Per-keyword candidates ---")
        for (kw in keywords) {
            val cands = perKeywordDebug[kw] ?: emptyList()
            val phonetic = perKeywordPhonetic[kw] ?: false
            sb.appendLine("  [$kw] phoneticExact=$phonetic")
            cands.take(5).forEach { (name, score) ->
                sb.appendLine("    ${"%.2f".format(score).padStart(6)}  $name")
            }
        }
        sb.appendLine()
        sb.appendLine("--- High-confidence (skipped Gemma) ---")
        if (highConfResolved.isEmpty()) sb.appendLine("  (none)")
        highConfResolved.forEach { (kw, name) -> sb.appendLine("  [$kw] -> $name") }
        sb.appendLine()
        sb.appendLine("--- Sent to Gemma (${gemmaCandidates.size} candidates) ---")
        gemmaCandidates.forEach { sb.appendLine("  $it") }
        sb.appendLine("===========================")
        Log.d("PharmaCam_DEBUG", sb.toString())
    }
}

/**
 * Output of [HybridRetriever.retrieve].
 */
data class RetrievalOutput(
    /** Keywords resolved with high confidence — bypass Gemma. keyword -> medicine name */
    val highConfResolved: Map<String, String>,
    /** Keywords that need Gemma disambiguation */
    val gemmaKeywords: List<String>,
    /** All candidate medicine names to send to Gemma, capped at 50 */
    val gemmaCandidates: List<String>,
    /** Debug: per-keyword top candidates with BM25 scores */
    val perKeywordDebug: Map<String, List<Pair<String, Double>>>
)
