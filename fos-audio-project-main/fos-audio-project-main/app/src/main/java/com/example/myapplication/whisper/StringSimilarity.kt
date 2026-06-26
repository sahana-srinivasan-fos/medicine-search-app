package com.example.myapplication.whisper

import kotlin.math.max
import kotlin.math.min

object StringSimilarity {

    fun levenshtein(s1: CharSequence, s2: CharSequence): Int {
        val len1 = s1.length
        val len2 = s2.length
        var dp = IntArray(len2 + 1) { it }
        var nextDp = IntArray(len2 + 1)
        for (i in 1..len1) {
            nextDp[0] = i
            for (j in 1..len2) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                nextDp[j] = minOf(
                    dp[j] + 1,
                    nextDp[j - 1] + 1,
                    dp[j - 1] + cost
                )
            }
            val temp = dp
            dp = nextDp
            nextDp = temp
        }
        return dp[len2]
    }

    fun ratio(s1: String, s2: String): Double {
        val len1 = s1.length
        val len2 = s2.length
        if (len1 == 0 && len2 == 0) return 1.0
        val dist = levenshtein(s1, s2)
        return (len1 + len2 - dist).toDouble() / (len1 + len2)
    }

    fun partialRatio(s1: String, s2: String): Double {
        val str1 = s1.lowercase()
        val str2 = s2.lowercase()
        val shorter = if (str1.length <= str2.length) str1 else str2
        val longer = if (str1.length <= str2.length) str2 else str1
        val n = shorter.length
        val m = longer.length
        if (n == 0) return 1.0

        if (longer.contains(shorter)) return 1.0

        var maxRatio = 0.0
        for (start in 0..m - n) {
            val sub = longer.substring(start, start + n)
            val r = ratio(shorter, sub)
            if (r > maxRatio) {
                maxRatio = r
            }
        }
        return maxRatio
    }

    /**
     * Token Sort Ratio: Tokenize strings, sort tokens alphabetically, and then compare.
     * Handles variation in word order (e.g., "Dolo 650" vs "650 Dolo").
     */
    fun tokenSortRatio(s1: String, s2: String): Double {
        val t1 = s1.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }.sorted().joinToString(" ")
        val t2 = s2.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }.sorted().joinToString(" ")
        return ratio(t1, t2)
    }

    /**
     * Token Set Ratio: Finds intersection of tokens and compares common parts.
     * Handles extra words/descriptors (e.g., "Dolo" vs "Dolo 650mg Tablet").
     */
    fun tokenSetRatio(s1: String, s2: String): Double {
        val tokens1 = s1.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }.toSet()
        val tokens2 = s2.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }.toSet()

        val intersection = tokens1.intersect(tokens2)
        val diff1to2 = tokens1.subtract(tokens2)
        val diff2to1 = tokens2.subtract(tokens1)

        val t0 = intersection.sorted().joinToString(" ").trim()
        val t1 = (intersection + diff1to2).sorted().joinToString(" ").trim()
        val t2 = (intersection + diff2to1).sorted().joinToString(" ").trim()

        val r0 = ratio(t0, t1)
        val r1 = ratio(t0, t2)
        val r2 = ratio(t1, t2)

        return maxOf(r0, r1, r2)
    }

    /**
     * Jaro-Winkler Similarity: Gives a prefix bonus for strings that start with the same characters.
     * Excellent for medicine brand names where the prefix is the most unique part.
     */
    fun jaroWinkler(s1: String, s2: String): Double {
        val jaro = jaroSimilarity(s1, s2)
        if (jaro < 0.7) return jaro

        var prefix = 0
        for (i in 0 until min(4, min(s1.length, s2.length))) {
            if (s1[i] == s2[i]) prefix++ else break
        }
        return jaro + (prefix * 0.1 * (1.0 - jaro))
    }

    private fun jaroSimilarity(s1: String, s2: String): Double {
        val len1 = s1.length
        val len2 = s2.length
        if (len1 == 0 && len2 == 0) return 1.0
        if (len1 == 0 || len2 == 0) return 0.0

        val matchDistance = max(len1, len2) / 2 - 1
        val s1Matches = BooleanArray(len1)
        val s2Matches = BooleanArray(len2)
        var matches = 0

        for (i in 0 until len1) {
            val start = max(0, i - matchDistance)
            val end = min(i + matchDistance + 1, len2)
            for (j in start until end) {
                if (s2Matches[j] || s1[i] != s2[j]) continue
                s1Matches[i] = true
                s2Matches[j] = true
                matches++
                break
            }
        }

        if (matches == 0) return 0.0

        var transpositions = 0
        var k = 0
        for (i in 0 until len1) {
            if (!s1Matches[i]) continue
            while (!s2Matches[k]) k++
            if (s1[i] != s2[k]) transpositions++
            k++
        }

        val m = matches.toDouble()
        return (m / len1 + m / len2 + (m - transpositions / 2.0) / m) / 3.0
    }
}
