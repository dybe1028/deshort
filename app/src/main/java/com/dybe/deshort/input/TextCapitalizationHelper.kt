package com.dybe.deshort.input

/**
 * Helper to determine whether the cursor position warrants auto-capitalization.
 */
object TextCapitalizationHelper {

    /**
     * Determines whether the cursor is currently at the start of a sentence or text.
     *
     * Rules:
     * - Null, empty or all whitespace -> true (start of input or blank line)
     * - Ends with newline -> true
     * - Ends with sentence-terminating punctuation ('.', '?', '!') followed by at least one whitespace -> true
     * - Otherwise -> false
     */
    fun isSentenceStart(textBeforeCursor: CharSequence?): Boolean {
        if (textBeforeCursor.isNullOrEmpty()) return true
        if (textBeforeCursor.all { it.isWhitespace() }) return true

        val lastChar = textBeforeCursor.last()
        if (lastChar == '\n' || lastChar == '\r') return true

        var i = textBeforeCursor.length - 1
        var hasWhitespace = false
        while (i >= 0 && (textBeforeCursor[i] == ' ' || textBeforeCursor[i] == '\t')) {
            hasWhitespace = true
            i--
        }

        if (hasWhitespace && i >= 0) {
            val nonSpaceChar = textBeforeCursor[i]
            if (nonSpaceChar == '.' || nonSpaceChar == '?' || nonSpaceChar == '!' || nonSpaceChar == '\n' || nonSpaceChar == '\r') {
                return true
            }
        }

        return false
    }
}
