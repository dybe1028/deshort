package com.dybe.deshort.input.telex

/**
 * Manages the state transitions when a Telex syllable is committed by Space,
 * the trailing space is removed by Backspace, and a subsequent tone key is pressed.
 *
 * Implements:
 *   - vis -> ví
 *   - vis + Space -> ví [committed]
 *   - vis + Space + Backspace -> ví [eligible for tone modification]
 *   - vis + Space + Backspace + j -> vị
 *   - Preserves literal character input when tone modification is not applicable.
 *   - Invalidates state on repeated spaces, cursor movements, or layout switches.
 */
class TelexSpaceBackspaceHandler(private val telexEngine: TelexEngine) {

    /** Word that was just committed by the Space key. */
    var lastCommittedWord: String? = null
        private set

    /** True if the immediately preceding key was Space that committed a word. */
    var spaceJustCommitted: Boolean = false
        private set

    /** Word that is currently eligible for tone modification after Backspace removed the trailing space. */
    var resumableWord: String? = null
        private set

    /**
     * Call when Space key is pressed.
     * @param committedWord The word that was just committed by this Space press, or null if no word was being composed.
     */
    fun onSpace(committedWord: String?) {
        if (!committedWord.isNullOrEmpty()) {
            lastCommittedWord = committedWord
            spaceJustCommitted = true
        } else {
            lastCommittedWord = null
            spaceJustCommitted = false
        }
        resumableWord = null
    }

    /**
     * Call when Backspace is pressed while no active composing text exists.
     * @param textBeforeCursor Text immediately preceding the cursor in the editor.
     * @return true if Backspace is removing the trailing space of the just-committed word,
     *         arming tone resumption; false if normal deletion should occur.
     */
    fun onBackspace(textBeforeCursor: String?): Boolean {
        if (spaceJustCommitted && lastCommittedWord != null && textBeforeCursor != null) {
            val word = lastCommittedWord!!
            val expected = "$word "
            if (textBeforeCursor.endsWith(expected)) {
                resumableWord = word
                spaceJustCommitted = false
                lastCommittedWord = null
                return true
            }
        }
        reset()
        return false
    }

    /**
     * Call when a letter key is pressed.
     * @param char The character typed.
     * @param textBeforeCursor Text immediately preceding the cursor in the editor.
     * @return Pair(originalWord, modifiedWord) if the tone was successfully applied to the preceding syllable,
     *         or null if normal typing should proceed.
     */
    fun onKey(char: Char, textBeforeCursor: String?): Pair<String, String>? {
        val word = resumableWord
        if (word != null && char.lowercaseChar() in TelexEngine.TONE_KEYS && textBeforeCursor != null) {
            if (textBeforeCursor.endsWith(word)) {
                val modified = telexEngine.applyToneToComposed(word, char)
                if (modified != null && modified != word && modified != "$word$char") {
                    reset()
                    return Pair(word, modified)
                }
            }
        }
        reset()
        return null
    }

    /**
     * Reset all tracking state (e.g. on cursor movement, mode switch, non-word keys).
     */
    fun reset() {
        lastCommittedWord = null
        spaceJustCommitted = false
        resumableWord = null
    }
}
