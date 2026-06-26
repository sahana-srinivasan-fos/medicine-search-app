package com.example.myapplication.whisper

import android.util.Log

/**
 * Double Metaphone phonetic algorithm implementation in pure Kotlin.
 *
 * Based on the original algorithm by Lawrence Philips (1990, 2000).
 * Returns a Pair<primary, alternate> phonetic code for any English/pharmaceutical word.
 *
 * Reference: https://en.wikipedia.org/wiki/Metaphone#Double_Metaphone
 */
object DoubleMetaphone {

    fun encode(input: String): Pair<String, String> {
        if (input.isBlank()) return Pair("", "")
        val word = input.uppercase().trim()
        if (word.isEmpty()) return Pair("", "")

        val primary = StringBuilder()
        val alternate = StringBuilder()
        var pos = 0
        val last = word.length - 1

        fun charAt(i: Int) = if (i in 0..last) word[i] else '\u0000'
        fun subStr(start: Int, len: Int): String {
            if (start < 0 || start >= word.length) return ""
            val end = minOf(start + len, word.length)
            return word.substring(start, end)
        }
        fun contains(start: Int, len: Int, vararg values: String): Boolean {
            val s = subStr(start, len)
            return values.any { it == s }
        }
        fun isVowel(i: Int) = charAt(i) in "AEIOUY"
        fun isSlavoGermanic() = word.contains('W') || word.contains('K') ||
                word.contains("CZ") || word.contains("WITZ")

        // Handle initial special cases
        if (contains(0, 2, "AE", "GN", "KN", "PN", "WR")) pos = 1

        val initialVowel = isVowel(0)
        if (initialVowel) {
            primary.append('A')
            alternate.append('A')
            pos = 1
        }

        fun add(p: String, a: String = p) {
            primary.append(p)
            alternate.append(a)
        }

        while (primary.length < 4 || alternate.length < 4) {
            if (pos > last) break
            val c = charAt(pos)

            when (c) {
                'A', 'E', 'I', 'O', 'U', 'Y' -> {
                    // vowels at start already handled; interior vowels mapped to nothing
                    pos++
                }
                'B' -> {
                    add("P")
                    pos += if (charAt(pos + 1) == 'B') 2 else 1
                }
                'Ç' -> { add("S"); pos++ }
                'C' -> {
                    when {
                        pos > 1 && !isVowel(pos - 2) && contains(pos - 1, 3, "ACH") &&
                                charAt(pos + 2) != 'I' && (charAt(pos + 2) != 'E' ||
                                contains(pos - 2, 6, "BACHER", "MACHER")) -> {
                            add("K"); pos += 2
                        }
                        pos == 0 && contains(0, 6, "CAESAR") -> {
                            add("S"); pos += 2
                        }
                        contains(pos, 4, "CHIA") -> { add("K"); pos += 2 }
                        contains(pos, 2, "CH") -> {
                            when {
                                pos > 0 && contains(pos, 4, "CHAE") -> { add("K", "X"); pos += 2 }
                                pos == 0 && (contains(1, 5, "HARAC", "HARIS") ||
                                        contains(1, 3, "HOR", "HYM", "HIA", "HEM")) &&
                                        !contains(0, 5, "CHORE") -> { add("K"); pos += 2 }
                                contains(0, 4, "VAN ", "VON ") || contains(0, 3, "SCH") ||
                                        contains(pos - 2, 6, "ORCHES", "ARCHIT", "ORCHID") ||
                                        contains(pos + 2, 1, "T", "S") ||
                                        (contains(pos - 1, 1, "A", "O", "U", "E") && pos == 0 ||
                                                contains(pos + 2, 1, "L", "R", "N", "M", "B", "H", "F", "V", "W", " ")) -> {
                                    add("K"); pos += 2
                                }
                                pos > 0 -> { add("X"); pos += 2 }
                                else -> { add("X"); pos += 2 }
                            }
                        }
                        contains(pos, 2, "CZ") && !contains(pos - 2, 4, "WICZ") -> {
                            add("S", "X"); pos += 2
                        }
                        contains(pos + 1, 3, "CIA") -> { add("X"); pos += 3 }
                        contains(pos, 2, "CC") && !(pos == 1 && charAt(0) == 'M') -> {
                            if (contains(pos + 2, 1, "I", "E", "H")) {
                                if (contains(pos + 2, 2, "HU")) { add("K") } else { add("X") }
                                pos += 3
                            } else { add("K"); pos += 2 }
                        }
                        contains(pos, 2, "CK", "CG", "CQ") -> { add("K"); pos += 2 }
                        contains(pos, 2, "CI", "CE", "CY") -> { add("S", "X"); pos += 2 }
                        else -> {
                            add("K")
                            pos += if (contains(pos + 1, 2, " C", " Q", " G")) 3 else
                                if (contains(pos + 1, 1, "C", "K", "Q") && !contains(pos + 1, 2, "CE", "CI")) 2
                                else 1
                        }
                    }
                }
                'D' -> {
                    when {
                        contains(pos, 2, "DG") -> {
                            if (contains(pos + 2, 1, "I", "E", "Y")) { add("J"); pos += 3 }
                            else { add("TK"); pos += 2 }
                        }
                        contains(pos, 2, "DT", "DD") -> { add("T"); pos += 2 }
                        else -> { add("T"); pos++ }
                    }
                }
                'F' -> { add("F"); pos += if (charAt(pos + 1) == 'F') 2 else 1 }
                'G' -> {
                    when {
                        charAt(pos + 1) == 'H' -> {
                            when {
                                pos > 0 && !isVowel(pos - 1) -> { add("K"); pos += 2 }
                                pos == 0 -> {
                                    if (charAt(pos + 2) == 'I') { add("J") } else { add("K") }
                                    pos += 2
                                }
                                (pos > 1 && contains(pos - 2, 1, "B", "H", "D")) ||
                                        (pos > 2 && contains(pos - 3, 1, "B", "H", "D")) ||
                                        (pos > 3 && contains(pos - 4, 1, "B", "H")) -> { pos += 2 }
                                else -> {
                                    if (pos > 2 && charAt(pos - 1) == 'U' &&
                                            contains(pos - 3, 1, "C", "G", "L", "R", "T")) {
                                        add("F")
                                    } else if (pos > 0 && charAt(pos - 1) != 'I') {
                                        add("K")
                                    }
                                    pos += 2
                                }
                            }
                        }
                        charAt(pos + 1) == 'N' -> {
                            if (pos == 1 && isVowel(0) && !isSlavoGermanic()) { add("KN", "N") }
                            else {
                                if (!contains(pos + 2, 2, "EY") && charAt(pos + 1) != 'Y' && !isSlavoGermanic()) {
                                    add("N", "KN")
                                } else { add("KN") }
                            }
                            pos += 2
                        }
                        contains(pos + 1, 2, "LI") && !isSlavoGermanic() -> { add("KL", "L"); pos += 2 }
                        pos == 0 && (charAt(pos + 1) == 'Y' || contains(pos + 1, 2,
                                "ES", "EP", "EB", "EL", "EY", "IB", "IL", "IN", "IE", "EI", "ER")) -> {
                            add("K", "J"); pos += 2
                        }
                        (contains(pos + 1, 2, "ER") || charAt(pos + 1) == 'Y') &&
                                !contains(0, 6, "DANGER", "RANGER", "MANGER") &&
                                !contains(pos - 1, 1, "E", "I") &&
                                !contains(pos - 1, 3, "RGY", "OGY") -> {
                            add("K", "J"); pos += 2
                        }
                        contains(pos + 1, 1, "E", "I", "Y") || contains(pos - 1, 4, "AGGI", "OGGI") -> {
                            if (contains(0, 4, "VAN ", "VON ") || contains(0, 3, "SCH") ||
                                    contains(pos + 1, 2, "ET")) { add("K") }
                            else if (contains(pos + 1, 4, "IER ")) { add("J") }
                            else { add("J", "K") }
                            pos += 2
                        }
                        charAt(pos + 1) == 'G' -> { add("K"); pos += 2 }
                        else -> { add("K"); pos++ }
                    }
                }
                'H' -> {
                    if ((pos == 0 || isVowel(pos - 1)) && isVowel(pos + 1)) { add("H"); pos += 2 }
                    else pos++
                }
                'J' -> {
                    when {
                        contains(pos, 4, "JOSE") || contains(0, 4, "SAN ") -> {
                            if ((pos == 0 && charAt(pos + 4) == ' ') || word == "SAN " || contains(0, 4, "SAN ")) {
                                add("H")
                            } else { add("J", "H") }
                            pos++
                        }
                        pos == 0 && !contains(0, 4, "JOSE") -> {
                            add("J", "A"); pos += if (charAt(pos + 1) == 'J') 2 else 1
                        }
                        isVowel(pos - 1) && !isSlavoGermanic() && contains(pos + 1, 1, "A", "O") -> {
                            add("J", "H"); pos += if (charAt(pos + 1) == 'J') 2 else 1
                        }
                        pos == last -> {
                            add("J", ""); pos++
                        }
                        !contains(pos + 1, 1, "L", "T", "K", "S", "N", "M", "B", "Z") &&
                                !contains(pos - 1, 1, "S", "K", "L") -> {
                            add("J"); pos += if (charAt(pos + 1) == 'J') 2 else 1
                        }
                        else -> { pos += if (charAt(pos + 1) == 'J') 2 else 1 }
                    }
                }
                'K' -> { add("K"); pos += if (charAt(pos + 1) == 'K') 2 else 1 }
                'L' -> {
                    if (charAt(pos + 1) == 'L') {
                        if ((pos == last - 1 && contains(pos - 1, 4, "ILLO", "ILLA", "ALLE")) ||
                                (contains(last - 1, 2, "AS", "OS") || contains(last, 1, "A", "O")) &&
                                contains(pos - 1, 4, "ALLE")) {
                            add("L", ""); pos += 2
                        } else { add("L"); pos += 2 }
                    } else { add("L"); pos++ }
                }
                'M' -> {
                    if ((contains(pos - 1, 3, "UMB") && (pos + 1 == last || contains(pos + 2, 2, "ER"))) ||
                            charAt(pos + 1) == 'M') {
                        add("M"); pos += 2
                    } else { add("M"); pos++ }
                }
                'N' -> { add("N"); pos += if (charAt(pos + 1) == 'N') 2 else 1 }
                'Ñ' -> { add("N"); pos++ }
                'P' -> {
                    if (charAt(pos + 1) == 'H') { add("F"); pos += 2 }
                    else { add("P"); pos += if (charAt(pos + 1) in "PB") 2 else 1 }
                }
                'Q' -> { add("K"); pos += if (charAt(pos + 1) == 'Q') 2 else 1 }
                'R' -> {
                    if (pos == last && !isSlavoGermanic() && contains(pos - 2, 2, "IE") &&
                            !contains(pos - 4, 2, "ME", "MA")) { add("", "R") }
                    else { add("R") }
                    pos += if (charAt(pos + 1) == 'R') 2 else 1
                }
                'S' -> {
                    when {
                        contains(pos - 1, 3, "ISL", "YSL") -> pos++
                        pos == 0 && contains(0, 5, "SUGAR") -> { add("X", "S"); pos++ }
                        contains(pos, 2, "SH") -> { add("X"); pos += 2 }
                        contains(pos, 3, "SIO", "SIA") -> {
                            if (isSlavoGermanic()) add("S") else add("S", "X"); pos += 3
                        }
                        (pos == 0 && contains(1, 1, "M", "N", "L", "W")) ||
                                contains(pos, 2, "SCH") -> { add("S", "X"); pos++ }
                        contains(pos, 4, "SCHE") || contains(pos, 4, "SCHL") -> {
                            add("SK"); pos += 3
                        }
                        pos == last && (contains(pos - 2, 2, "AI", "OI")) -> {
                            add("", "S"); pos++
                        }
                        contains(pos, 2, "SC") -> {
                            when {
                                charAt(pos + 2) == 'H' -> { add("SK"); pos += 3 }
                                contains(pos + 2, 1, "I", "E", "Y") -> { add("S"); pos += 3 }
                                else -> { add("SK"); pos += 3 }
                            }
                        }
                        else -> {
                            add("S"); pos += if (contains(pos + 1, 1, "S", "Z")) 2 else 1
                        }
                    }
                }
                'T' -> {
                    when {
                        contains(pos, 4, "TION") || contains(pos, 3, "TIA", "TCH") -> {
                            add("X"); pos += if (contains(pos, 3, "TCH")) 3 else 3
                        }
                        contains(pos, 2, "TH") || contains(pos, 3, "TTH") -> {
                            if (contains(pos + 2, 1, "O", "A") || contains(0, 4, "VAN ", "VON ") ||
                                    contains(0, 3, "SCH")) { add("T") } else { add("0", "T") }
                            pos += 2
                        }
                        else -> { add("T"); pos += if (contains(pos + 1, 1, "T", "D")) 2 else 1 }
                    }
                }
                'V' -> { add("F"); pos += if (charAt(pos + 1) == 'V') 2 else 1 }
                'W' -> {
                    when {
                        contains(pos, 2, "WR") -> { add("R"); pos += 2 }
                        pos == 0 && (isVowel(pos + 1) || contains(pos, 2, "WH")) -> {
                            if (isVowel(pos + 1)) { add("A", "F") } else { add("A") }
                            pos++
                        }
                        (pos == last && isVowel(pos - 1)) ||
                                contains(pos - 1, 5, "EWSKI", "EWSKY", "OWSKI", "OWSKY") ||
                                contains(0, 3, "SCH") -> { add("", "F"); pos++ }
                        contains(pos, 4, "WICZ", "WITZ") -> { add("TS", "FX"); pos += 4 }
                        else -> pos++
                    }
                }
                'X' -> {
                    if (!(pos == last && (contains(pos - 3, 3, "IAU", "EAU") ||
                                    contains(pos - 2, 2, "AU", "OU")))) { add("KS") }
                    pos += if (contains(pos + 1, 1, "C", "X")) 2 else 1
                }
                'Z' -> {
                    if (charAt(pos + 1) == 'H') { add("J"); pos += 2 }
                    else {
                        if (contains(pos + 1, 2, "ZO", "ZI", "ZA") ||
                                (isSlavoGermanic() && pos > 0 && charAt(pos - 1) != 'T')) {
                            add("S", "TS")
                        } else { add("S") }
                        pos += if (charAt(pos + 1) == 'Z') 2 else 1
                    }
                }
                else -> pos++
            }
        }

        return Pair(primary.toString().take(6), alternate.toString().take(6))
    }
}

/**
 * Phonetic index over a list of medicine names using Double Metaphone.
 *
 * Built once at startup. Maps phonetic codes to medicine indices for fast lookup.
 */
class PhoneticIndex(private val medicineList: List<String>) {

    companion object {
        private const val TAG = "PhoneticIndex"
    }

    // phoneticCode -> list of medicine indices
    private val codeToIndices: Map<String, List<Int>>

    init {
        val startMs = System.currentTimeMillis()
        val map = mutableMapOf<String, MutableList<Int>>()

        for (i in medicineList.indices) {
            val name = medicineList[i]
            // Encode each token of the medicine name individually
            for (token in tokenize(name)) {
                if (token.length < 2) continue
                val (primary, alternate) = DoubleMetaphone.encode(token)
                if (primary.isNotEmpty()) {
                    map.getOrPut(primary) { mutableListOf() }.add(i)
                }
                if (alternate.isNotEmpty() && alternate != primary) {
                    map.getOrPut(alternate) { mutableListOf() }.add(i)
                }
            }
        }

        // Deduplicate each list
        codeToIndices = map.mapValues { (_, v) -> v.distinct() }

        Log.d(TAG, "Phonetic index built: ${codeToIndices.size} codes in ${System.currentTimeMillis() - startMs}ms")
    }

    /**
     * Returns all medicine indices that share a phonetic code with [queryToken].
     * Checks both primary and alternate Double Metaphone codes.
     */
    fun lookup(queryToken: String): Set<Int> {
        val result = mutableSetOf<Int>()
        val (primary, alternate) = DoubleMetaphone.encode(queryToken)
        codeToIndices[primary]?.let { result.addAll(it) }
        if (alternate.isNotEmpty() && alternate != primary) {
            codeToIndices[alternate]?.let { result.addAll(it) }
        }
        return result
    }

    /**
     * Returns true if [queryToken]'s primary phonetic code exactly matches
     * the primary code of at least one token in [medicineName].
     */
    fun exactPrimaryMatch(queryToken: String, medicineName: String): Boolean {
        val (queryPrimary, _) = DoubleMetaphone.encode(queryToken)
        if (queryPrimary.isEmpty()) return false
        for (token in tokenize(medicineName)) {
            val (p, _) = DoubleMetaphone.encode(token)
            if (p == queryPrimary) return true
        }
        return false
    }
}
