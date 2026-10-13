package com.dybe.deshort.input.telex

import java.text.BreakIterator

/**
 * TelexEngine — Vietnamese Telex input method engine.
 *
 * Processes one keypress at a time, maintains the raw-key buffer for the current
 * composing word, and derives precomposed Unicode Vietnamese output on every
 * keystroke.
 *
 * This class is pure Kotlin with zero Android imports so it can be unit-tested
 * directly on the JVM.
 *
 * ## Telex rules implemented
 *
 * ### Vowel digraphs
 *   aa → â   aw → ă   ee → ê   oo → ô   ow → ơ   uw → ư
 *
 * ### Consonant digraph
 *   dd → đ   (syllable-initial consonant position)
 *
 * ### Vowel modifier 'w'
 *   - 'w' standalone / initial → 'ư'
 *   - 'ww' → literal 'w'
 *   - 'w' with vowels: modifies 'uo' → 'ươ', 'ua' → 'ưa' (except qu), 'oi' → 'ơi', 'o' → 'ơ', 'u' → 'ư', 'a' → 'ă'
 *
 * ### Tone marks (applied to the tonal nucleus vowel)
 *   s → sắc (´)    f → huyền (`)    r → hỏi (̉)
 *   x → ngã (~)    j → nặng (.)
 *   z → cancel tone (return to bare base vowel)
 *
 * Supports entering tone marks before, between, or after vowel transformations,
 * changing tones, tone toggling/cancellation, and grapheme-aware backspace.
 */
class TelexEngine {

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    /** Raw lower-case key presses for the current composing word. */
    private val rawKeys = StringBuilder()

    /** Whether each position in rawKeys was originally uppercase. */
    private val upperFlags = mutableListOf<Boolean>()

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Feed one typed character into the engine.
     *
     * @param ch  The character typed (may be upper or lower case).
     * @return    The full composed string for the current word.
     */
    fun type(ch: Char): String {
        val lower = ch.lowercaseChar()
        rawKeys.append(lower)
        upperFlags.add(ch.isUpperCase())
        return compose()
    }

    /**
     * Remove the last user-perceived grapheme/character from the composing text.
     * Synchronizes the internal buffer so subsequent typing continues composing seamlessly.
     *
     * @return The new composed string, or "" if the buffer is now empty.
     */
    fun backspace(): String {
        if (rawKeys.isEmpty()) return ""

        val currentComposed = compose()
        if (currentComposed.isEmpty()) {
            reset()
            return ""
        }

        // Find the boundary of the last grapheme cluster in the composed string
        val boundary = BreakIterator.getCharacterInstance()
        boundary.setText(currentComposed)
        val end = boundary.last()
        val start = boundary.previous()

        val newComposed = if (start == BreakIterator.DONE || start <= 0) {
            ""
        } else {
            currentComposed.substring(0, start)
        }

        if (newComposed.isEmpty()) {
            reset()
            return ""
        }

        // Re-synchronize internal rawKeys and upperFlags with the remaining composed string
        syncWithComposed(newComposed)
        return newComposed
    }

    /**
     * Clear all state (call on word boundary: space, enter, punctuation, etc.).
     */
    fun reset() {
        rawKeys.clear()
        upperFlags.clear()
    }

    /** True when no composing word is in progress. */
    val isEmpty: Boolean get() = rawKeys.isEmpty()

    /** The current raw key buffer (for debugging / testing). */
    val rawBuffer: String get() = rawKeys.toString()

    /**
     * Restore the engine's internal state to match a previously composed word.
     */
    fun restoreComposed(composed: String) {
        syncWithComposed(composed)
    }

    /**
     * Attempts to apply [toneChar] to an already composed word [composed].
     * Returns the newly composed string if [toneChar] modifies the word,
     * or null if [toneChar] does not validly modify [composed].
     *
     * If successful, the engine remains in the newly composed state.
     * If unsuccessful, the engine state is left unchanged.
     */
    fun applyToneToComposed(composed: String, toneChar: Char): String? {
        if (composed.isEmpty()) return null
        val lowerTone = toneChar.lowercaseChar()
        if (lowerTone !in TONE_KEYS) return null

        val hasVowel = composed.any { isVowelChar(it.lowercaseChar()) }
        if (!hasVowel) return null

        val savedRaw = rawKeys.toString()
        val savedFlags = ArrayList(upperFlags)

        syncWithComposed(composed)
        val modified = type(toneChar)

        if (modified != composed && modified != "$composed$toneChar") {
            return modified
        } else {
            rawKeys.clear()
            rawKeys.append(savedRaw)
            upperFlags.clear()
            upperFlags.addAll(savedFlags)
            return null
        }
    }

    // -------------------------------------------------------------------------
    // Composition Engine
    // -------------------------------------------------------------------------

    private data class KeyItem(
        val char: Char,
        val isUpper: Boolean
    )

    /**
     * Re-derive the composed Vietnamese string from [rawKeys] and [upperFlags].
     */
    fun compose(): String {
        if (rawKeys.isEmpty()) return ""

        val items = ArrayList<KeyItem>(rawKeys.length)
        for (i in rawKeys.indices) {
            items.add(KeyItem(rawKeys[i], upperFlags[i]))
        }

        // 1. Separate tone keys from phonetic stream
        val toneExtraction = extractTone(items)
        val phoneticItems = toneExtraction.phonetics
        val activeTone = toneExtraction.activeTone

        // 2. Transform consonants (dd -> đ) and vowels (aa, ee, oo, aw, ow, uw, w-rules)
        val transformed = transformPhonetics(phoneticItems)

        // 3. Apply active tone mark to the tonal nucleus vowel
        val tonedText = applyTone(transformed.text, activeTone)

        // 4. Restore uppercase formatting
        val result = StringBuilder(tonedText.length)
        for (i in tonedText.indices) {
            val c = tonedText[i]
            val isUpper = if (i < transformed.upperFlags.size) transformed.upperFlags[i] else false
            result.append(if (isUpper) c.uppercaseChar() else c)
        }

        return result.toString()
    }

    // -------------------------------------------------------------------------
    // Pass 1 — Tone extraction
    // -------------------------------------------------------------------------

    private data class ToneExtractionResult(
        val phonetics: List<KeyItem>,
        val activeTone: Char?
    )

    /**
     * Extracts active tone key while correctly handling:
     * - Keys in TONE_KEYS only act as tones if preceded by a vowel in the word.
     * - Double tone keys (e.g. ss, ff, rr, xx, jj, zz) cancel tone and output literal character.
     * - Changing tone (e.g. toans + f -> toàn, tieest + j -> tiệt): new tone supersedes previous different tone.
     * - Tone cancel key 'z' (e.g. as + z -> a).
     */
    private fun extractTone(items: List<KeyItem>): ToneExtractionResult {
        if (items.isEmpty()) return ToneExtractionResult(emptyList(), null)

        val droppedIndices = mutableSetOf<Int>()
        val literalIndices = mutableSetOf<Int>()
        val repeatedPairSeconds = mutableMapOf<Int, Int>() // secondIndex -> firstIndex

        // 1. Identify all contiguous repeated tone pairs (e.g. ss, ff, rr, xx, jj, zz)
        var i = 0
        while (i < items.size) {
            val c = items[i].char
            val hasVowel = hasVowelBefore(items, i)
            if (c in TONE_KEYS && hasVowel && i + 1 < items.size && items[i + 1].char == c) {
                // First key is consumed as tone cancellation, second key is retained as literal character
                droppedIndices.add(i)
                literalIndices.add(i + 1)
                repeatedPairSeconds[i + 1] = i
                i += 2
                continue
            }
            i++
        }

        // 2. The rightmost tone event in the syllable determines the active tone state
        var activeTone: Char? = null
        for (idx in items.indices.reversed()) {
            val pairFirst = repeatedPairSeconds[idx]
            if (pairFirst != null) {
                // The rightmost tone event was a repeated cancellation pair (e.g. 'ss', 'rr')
                activeTone = null
                val pairChar = items[pairFirst].char
                // Drop any earlier different tone key that was superseded
                for (k in (pairFirst - 1) downTo 0) {
                    if (k !in literalIndices && k !in droppedIndices && items[k].char in TONE_KEYS && items[k].char != pairChar && hasVowelBefore(items, k)) {
                        droppedIndices.add(k)
                        break
                    }
                }
                break
            }

            if (idx in literalIndices || idx in droppedIndices) continue

            val c = items[idx].char
            if (c in TONE_KEYS && hasVowelBefore(items, idx)) {
                if (c == 'z') {
                    // 'z' cancels tone
                    droppedIndices.add(idx)
                    // Cancel earlier tone key if any
                    for (k in (idx - 1) downTo 0) {
                        if (k !in literalIndices && k !in droppedIndices && items[k].char in TONE_KEYS && hasVowelBefore(items, k)) {
                            droppedIndices.add(k)
                            break
                        }
                    }
                    activeTone = null
                    break
                }

                // Active tone key found
                activeTone = c
                droppedIndices.add(idx)

                // Earlier different tone keys are superseded
                for (k in (idx - 1) downTo 0) {
                    if (k !in literalIndices && k !in droppedIndices && items[k].char in TONE_KEYS && items[k].char != c && hasVowelBefore(items, k)) {
                        droppedIndices.add(k)
                        break
                    }
                }
                break
            }
        }

        val phonetics = mutableListOf<KeyItem>()
        for (idx in items.indices) {
            if (idx !in droppedIndices) {
                phonetics.add(items[idx])
            }
        }

        return ToneExtractionResult(phonetics, activeTone)
    }

    private fun hasVowelBefore(items: List<KeyItem>, i: Int): Boolean {
        return (0 until i).any { idx ->
            isVowelChar(items[idx].char) || (items[idx].char == 'w' && idx == 0)
        }
    }

    // -------------------------------------------------------------------------
    // Pass 2 — Phonetic transformation
    // -------------------------------------------------------------------------

    private data class TransformedPhonetics(
        val text: String,
        val upperFlags: List<Boolean>
    )

    /**
     * Transform consonant digraphs (dd -> đ) and vowel modifications.
     */
    private fun transformPhonetics(items: List<KeyItem>): TransformedPhonetics {
        if (items.isEmpty()) return TransformedPhonetics("", emptyList())

        val resultChars = mutableListOf<Char>()
        val resultFlags = mutableListOf<Boolean>()

        var i = 0
        while (i < items.size) {
            val item = items[i]
            val c = item.char

            // Count contiguous identical characters
            var runLen = 1
            while (i + runLen < items.size && items[i + runLen].char == c) {
                runLen++
            }

            // Repeated Key Rule: 3 or more identical characters in a row stay raw
            if (runLen >= 3) {
                for (k in 0 until runLen) {
                    resultChars.add(items[i + k].char)
                    resultFlags.add(items[i + k].isUpper)
                }
                i += runLen
                continue
            }

            // Exactly 2 identical characters
            if (runLen == 2) {
                val pair = "$c$c"
                val isPairUpper = items[i].isUpper || items[i + 1].isUpper

                if (pair == "dd" && (i == 0 || resultChars.none { isVowelChar(it) })) {
                    resultChars.add('đ')
                    resultFlags.add(isPairUpper)
                    i += 2
                    continue
                }

                if (pair == "ww") {
                    resultChars.add('w')
                    resultFlags.add(isPairUpper)
                    i += 2
                    continue
                }

                val substitution = VOWEL_DIGRAPHS[pair]
                if (substitution != null) {
                    resultChars.add(substitution[0])
                    resultFlags.add(isPairUpper)
                    i += 2
                    continue
                }

                // Other double keys (e.g. ll, mm, tt) -> keep both
                resultChars.add(c)
                resultFlags.add(items[i].isUpper)
                resultChars.add(c)
                resultFlags.add(items[i + 1].isUpper)
                i += 2
                continue
            }

            // Triphthong check: 'uwow' -> 'ươ'
            if (c == 'u' && i + 3 < items.size &&
                items[i + 1].char == 'w' && items[i + 2].char == 'o' && items[i + 3].char == 'w'
            ) {
                val isUUpper = items[i].isUpper || items[i + 1].isUpper
                val isOUpper = items[i + 2].isUpper || items[i + 3].isUpper
                resultChars.add('ư')
                resultFlags.add(isUUpper)
                resultChars.add('ơ')
                resultFlags.add(isOUpper)
                i += 4
                continue
            }

            // Triphthong check: 'uow' -> 'ươ'
            if (c == 'u' && i + 2 < items.size && items[i + 1].char == 'o' && items[i + 2].char == 'w') {
                val isUUpper = items[i].isUpper
                val isOUpper = items[i + 1].isUpper || items[i + 2].isUpper
                resultChars.add('ư')
                resultFlags.add(isUUpper)
                resultChars.add('ơ')
                resultFlags.add(isOUpper)
                i += 3
                continue
            }

            // Check for 'ua' + 'w' -> 'ưa' (e.g. muaw -> mưa, chuaw -> chưa, cuaw -> cưa)
            // Note: if preceded by 'q' (quaw -> quă), 'u' is part of initial 'qu' so 'aw' -> 'ă'
            if (c == 'u' && i + 2 < items.size && items[i + 1].char == 'a' && items[i + 2].char == 'w') {
                val precedingChar = resultChars.lastOrNull()?.lowercaseChar()
                if (precedingChar != 'q') {
                    val isUUpper = items[i].isUpper || items[i + 2].isUpper
                    val isAUpper = items[i + 1].isUpper
                    resultChars.add('ư')
                    resultFlags.add(isUUpper)
                    resultChars.add('a')
                    resultFlags.add(isAUpper)
                    i += 3
                    continue
                }
            }

            // Digraph lookup (aw -> ă, ow -> ơ, uw -> ư)
            if (i + 1 < items.size) {
                val nextItem = items[i + 1]
                val pair = "$c${nextItem.char}"
                val substitution = VOWEL_DIGRAPHS[pair]
                if (substitution != null) {
                    val isPairUpper = items[i].isUpper || nextItem.isUpper
                    resultChars.add(substitution[0])
                    resultFlags.add(isPairUpper)
                    i += 2
                    continue
                }
            }

            // Standalone 'w' at start of word -> 'ư'
            if (c == 'w' && i == 0) {
                resultChars.add('ư')
                resultFlags.add(item.isUpper)
                i++
                continue
            }

            // Check if 'w' is modifying preceding vowels in the syllable
            if (c == 'w') {
                val modified = applyWToPrecedingVowels(resultChars, resultFlags, item.isUpper)
                if (modified) {
                    i++
                    continue
                }
            }

            // Normal single character
            resultChars.add(c)
            resultFlags.add(item.isUpper)
            i++
        }

        return TransformedPhonetics(resultChars.joinToString(""), resultFlags)
    }

    /**
     * Applies 'w' modifier to applicable preceding vowels according to Vietnamese orthography:
     * - 'uo' -> 'ươ' (e.g. nhuoc + w -> nhược, thuong + w -> thương)
     * - 'ua' -> 'ưa' (e.g. mua + w -> mưa, chua + w -> chưa)
     * - 'oi' -> 'ơi' (e.g. moi + w -> mới)
     * - 'o'  -> 'ơ'  (e.g. to + w -> tơ, cho + w -> chơ)
     * - 'u'  -> 'ư'  (e.g. tu + w -> tư)
     * - 'a'  -> 'ă'  (e.g. ta + w -> tă)
     */
    private fun applyWToPrecedingVowels(
        chars: MutableList<Char>,
        flags: MutableList<Boolean>,
        wIsUpper: Boolean
    ): Boolean {
        val lastVowelIdx = chars.indexOfLast { isVowelChar(it) }
        if (lastVowelIdx < 0) return false

        var firstVowelIdx = lastVowelIdx
        while (firstVowelIdx > 0 && isVowelChar(chars[firstVowelIdx - 1])) {
            firstVowelIdx--
        }

        val vowelCluster = chars.subList(firstVowelIdx, lastVowelIdx + 1).joinToString("")

        // 1. 'uo' or 'uô' -> 'ươ'
        if (vowelCluster.contains("uo") || vowelCluster.contains("uô")) {
            for (j in firstVowelIdx until lastVowelIdx) {
                if (chars[j] == 'u' && (chars[j + 1] == 'o' || chars[j + 1] == 'ô')) {
                    chars[j] = 'ư'
                    chars[j + 1] = 'ơ'
                    if (wIsUpper) {
                        flags[j] = true
                        flags[j + 1] = true
                    }
                    return true
                }
            }
        }

        // 2. 'ua' -> 'ưa' (except after 'q')
        if (vowelCluster.contains("ua")) {
            val precedingChar = if (firstVowelIdx > 0) chars[firstVowelIdx - 1].lowercaseChar() else null
            if (precedingChar != 'q') {
                for (j in firstVowelIdx until lastVowelIdx) {
                    if (chars[j] == 'u' && chars[j + 1] == 'a') {
                        chars[j] = 'ư'
                        if (wIsUpper) flags[j] = true
                        return true
                    }
                }
            }
        }

        // 3. 'oi' -> 'ơi'
        if (vowelCluster.contains("oi")) {
            for (j in firstVowelIdx until lastVowelIdx) {
                if (chars[j] == 'o' && chars[j + 1] == 'i') {
                    chars[j] = 'ơ'
                    if (wIsUpper) flags[j] = true
                    return true
                }
            }
        }

        // 4. 'o' -> 'ơ'
        val oIdx = chars.subList(firstVowelIdx, lastVowelIdx + 1).indexOfLast { it == 'o' }
        if (oIdx >= 0) {
            val actualIdx = firstVowelIdx + oIdx
            chars[actualIdx] = 'ơ'
            if (wIsUpper) flags[actualIdx] = true
            return true
        }

        // 5. 'u' -> 'ư'
        val uIdx = chars.subList(firstVowelIdx, lastVowelIdx + 1).indexOfLast { it == 'u' }
        if (uIdx >= 0) {
            val actualIdx = firstVowelIdx + uIdx
            chars[actualIdx] = 'ư'
            if (wIsUpper) flags[actualIdx] = true
            return true
        }

        // 6. 'a' -> 'ă'
        val aIdx = chars.subList(firstVowelIdx, lastVowelIdx + 1).indexOfLast { it == 'a' }
        if (aIdx >= 0) {
            val actualIdx = firstVowelIdx + aIdx
            chars[actualIdx] = 'ă'
            if (wIsUpper) flags[actualIdx] = true
            return true
        }

        return false
    }

    // -------------------------------------------------------------------------
    // Pass 3 — Tone application
    // -------------------------------------------------------------------------

    /**
     * Apply [toneChar] to the tonal nucleus in [phonemes].
     */
    private fun applyTone(phonemes: String, toneChar: Char?): String {
        if (toneChar == null || toneChar == 'z') return phonemes

        val clusterEnd = phonemes.indexOfLast { isVowelChar(it) }
        if (clusterEnd < 0) return phonemes

        var clusterStart = clusterEnd
        while (clusterStart > 0 && isVowelChar(phonemes[clusterStart - 1])) {
            clusterStart--
        }

        val cluster = phonemes.substring(clusterStart, clusterEnd + 1)
        val hasFinalConsonant = clusterEnd < phonemes.length - 1
        val precedingChar = if (clusterStart > 0) phonemes[clusterStart - 1].lowercaseChar() else null

        val targetInCluster = chooseTonalTarget(cluster, hasFinalConsonant, precedingChar)
        val targetGlobal = clusterStart + targetInCluster

        val targetChar = phonemes[targetGlobal]
        val toned = TONE_TABLE[targetChar]?.get(toneChar) ?: return phonemes

        return phonemes.substring(0, targetGlobal) + toned + phonemes.substring(targetGlobal + 1)
    }

    /**
     * Choose which vowel in [cluster] receives the tone mark based on standard
     * Vietnamese orthography rules:
     *   1. If cluster contains modified vowels (â, ă, ê, ô, ơ, ư):
     *      - If 'ươ' is present, tone goes on 'ơ'.
     *      - Otherwise, tone goes on the last modified vowel.
     *   2. 'qu' and 'gi' initial consonant exceptions:
     *      - qu + vowel: 'u' is semi-vowel, tone on 2nd vowel (e.g. quá, quán).
     *      - gi + vowel: 'i' is semi-vowel, tone on 2nd vowel (e.g. giá, gió, giúp).
     *   3. Multi-vowel cluster with final consonant -> tone on 2nd vowel (e.g. oán, uýn, hoàn).
     *   4. Multi-vowel cluster at end of syllable (no final consonant):
     *      - Starting with oa, oe, uy: tone on 2nd vowel (hoà, hoè, thuỳ).
     *      - Other diphthongs (ai, ao, au, ay, oi, eo, ui, ia, ua): tone on 1st vowel.
     */
    private fun chooseTonalTarget(cluster: String, hasFinalConsonant: Boolean, precedingChar: Char?): Int {
        if (cluster.length <= 1) return 0

        val lowerCluster = cluster.lowercase()

        // 1. Modified vowels (â, ă, ê, ô, ơ, ư) always receive tone priority
        val lastModified = cluster.indices.lastOrNull { cluster[it] in MODIFIED_VOWELS }
        if (lastModified != null) {
            return lastModified
        }

        // 2. Initial consonant cluster exceptions: 'qu' and 'gi'
        if (precedingChar == 'q' && lowerCluster.startsWith("u")) {
            return 1
        }
        if (precedingChar == 'g' && lowerCluster.startsWith("i")) {
            return 1
        }

        // 3. Multi-vowel cluster with final consonant -> tone on 2nd vowel
        if (hasFinalConsonant) {
            return 1
        }

        // 4. Multi-vowel cluster without final consonant
        if (lowerCluster.startsWith("oa") || lowerCluster.startsWith("oe") || lowerCluster.startsWith("uy")) {
            return 1
        }

        // Standard diphthongs (ai, ao, au, ay, oi, eo, ui, ia, ua) -> tone on 1st vowel
        return 0
    }

    // -------------------------------------------------------------------------
    // Backspace Synchronization
    // -------------------------------------------------------------------------

    /**
     * Synchronizes [rawKeys] and [upperFlags] to match [composed] after a backspace.
     * Decomposes [composed] into canonical phonetic keys and active tone so subsequent
     * keystrokes continue composing accurately.
     */
    private fun syncWithComposed(composed: String) {
        rawKeys.clear()
        upperFlags.clear()

        var activeToneKey: Char? = null

        for (i in composed.indices) {
            val ch = composed[i]
            val isUpper = ch.isUpperCase()
            val lower = ch.lowercaseChar()

            val toneInfo = REVERSE_TONE_MAP[lower]
            val baseChar = toneInfo?.first ?: lower
            val toneKey = toneInfo?.second
            if (toneKey != null) {
                activeToneKey = toneKey
            }

            val rawExpansion = BASE_CHAR_TO_RAW_KEYS[baseChar] ?: baseChar.toString()
            for (r in rawExpansion) {
                rawKeys.append(r)
                upperFlags.add(isUpper)
            }
        }

        // Append active tone key to raw buffer so engine retains tone on next letter
        if (activeToneKey != null) {
            rawKeys.append(activeToneKey)
            upperFlags.add(false)
        }
    }

    private fun isVowelChar(c: Char): Boolean =
        c in ALL_VOWEL_CHARS || c in BARE_VOWELS || c in REVERSE_TONE_MAP.keys

    // -------------------------------------------------------------------------
    // Tables
    // -------------------------------------------------------------------------

    companion object {

        val BARE_VOWELS = setOf('a', 'e', 'i', 'o', 'u', 'y')

        val ALL_VOWEL_CHARS = setOf(
            'a', 'ă', 'â',
            'e', 'ê',
            'i',
            'o', 'ô', 'ơ',
            'u', 'ư',
            'y'
        )

        val MODIFIED_VOWELS = setOf('ă', 'â', 'ê', 'ô', 'ơ', 'ư')

        val TONE_KEYS = setOf('s', 'f', 'r', 'x', 'j', 'z')

        val VOWEL_DIGRAPHS = mapOf(
            "aa" to "â",
            "aw" to "ă",
            "ee" to "ê",
            "oo" to "ô",
            "ow" to "ơ",
            "uw" to "ư"
        )

        val TONE_TABLE: Map<Char, Map<Char, Char>> = mapOf(
            'a' to mapOf('s' to 'á', 'f' to 'à', 'r' to 'ả', 'x' to 'ã', 'j' to 'ạ'),
            'ă' to mapOf('s' to 'ắ', 'f' to 'ằ', 'r' to 'ẳ', 'x' to 'ẵ', 'j' to 'ặ'),
            'â' to mapOf('s' to 'ấ', 'f' to 'ầ', 'r' to 'ẩ', 'x' to 'ẫ', 'j' to 'ậ'),
            'e' to mapOf('s' to 'é', 'f' to 'è', 'r' to 'ẻ', 'x' to 'ẽ', 'j' to 'ẹ'),
            'ê' to mapOf('s' to 'ế', 'f' to 'ề', 'r' to 'ể', 'x' to 'ễ', 'j' to 'ệ'),
            'i' to mapOf('s' to 'í', 'f' to 'ì', 'r' to 'ỉ', 'x' to 'ĩ', 'j' to 'ị'),
            'o' to mapOf('s' to 'ó', 'f' to 'ò', 'r' to 'ỏ', 'x' to 'õ', 'j' to 'ọ'),
            'ô' to mapOf('s' to 'ố', 'f' to 'ồ', 'r' to 'ổ', 'x' to 'ỗ', 'j' to 'ộ'),
            'ơ' to mapOf('s' to 'ớ', 'f' to 'ờ', 'r' to 'ở', 'x' to 'ỡ', 'j' to 'ợ'),
            'u' to mapOf('s' to 'ú', 'f' to 'ù', 'r' to 'ủ', 'x' to 'ũ', 'j' to 'ụ'),
            'ư' to mapOf('s' to 'ứ', 'f' to 'ừ', 'r' to 'ử', 'x' to 'ữ', 'j' to 'ự'),
            'y' to mapOf('s' to 'ý', 'f' to 'ỳ', 'r' to 'ỷ', 'x' to 'ỹ', 'j' to 'ỵ')
        )

        /** Reverse tone lookup: tonedChar -> Pair(baseChar, toneKey) */
        val REVERSE_TONE_MAP: Map<Char, Pair<Char, Char>> = run {
            val map = mutableMapOf<Char, Pair<Char, Char>>()
            for ((base, toneMap) in TONE_TABLE) {
                for ((toneKey, toned) in toneMap) {
                    map[toned] = Pair(base, toneKey)
                }
            }
            map
        }

        /** Base character to canonical raw keys */
        val BASE_CHAR_TO_RAW_KEYS = mapOf(
            'â' to "aa",
            'ă' to "aw",
            'ê' to "ee",
            'ô' to "oo",
            'ơ' to "ow",
            'ư' to "uw",
            'đ' to "dd"
        )
    }
}
