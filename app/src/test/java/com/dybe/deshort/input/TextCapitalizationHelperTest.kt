package com.dybe.deshort.input

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [TextCapitalizationHelper].
 */
class TextCapitalizationHelperTest {

    @Test
    fun `null or empty text is considered sentence start`() {
        assertTrue(TextCapitalizationHelper.isSentenceStart(null))
        assertTrue(TextCapitalizationHelper.isSentenceStart(""))
    }

    @Test
    fun `whitespace-only text is considered sentence start`() {
        assertTrue(TextCapitalizationHelper.isSentenceStart("   "))
        assertTrue(TextCapitalizationHelper.isSentenceStart("\t"))
        assertTrue(TextCapitalizationHelper.isSentenceStart(" \n "))
    }

    @Test
    fun `newline ending is considered sentence start`() {
        assertTrue(TextCapitalizationHelper.isSentenceStart("Hello\n"))
        assertTrue(TextCapitalizationHelper.isSentenceStart("Hello\r\n"))
        assertTrue(TextCapitalizationHelper.isSentenceStart("Hello.\n"))
    }

    @Test
    fun `period followed by space triggers sentence start`() {
        assertTrue(TextCapitalizationHelper.isSentenceStart("Tôi là người Việt Nam. "))
        assertTrue(TextCapitalizationHelper.isSentenceStart("A. "))
        assertTrue(TextCapitalizationHelper.isSentenceStart("Hôm nay trời đẹp.  "))
    }

    @Test
    fun `question mark followed by space triggers sentence start`() {
        assertTrue(TextCapitalizationHelper.isSentenceStart("Bạn khỏe không? "))
        assertTrue(TextCapitalizationHelper.isSentenceStart("Thật ư?  "))
    }

    @Test
    fun `exclamation mark followed by space triggers sentence start`() {
        assertTrue(TextCapitalizationHelper.isSentenceStart("Tuyệt vời quá! "))
        assertTrue(TextCapitalizationHelper.isSentenceStart("Cố lên!   "))
    }

    @Test
    fun `punctuation without trailing space does not trigger sentence start`() {
        assertFalse(TextCapitalizationHelper.isSentenceStart("Chào bạn."))
        assertFalse(TextCapitalizationHelper.isSentenceStart("Bạn khỏe không?"))
        assertFalse(TextCapitalizationHelper.isSentenceStart("Cố lên!"))
        assertFalse(TextCapitalizationHelper.isSentenceStart("Số 3.14"))
    }

    @Test
    fun `words without sentence-ending punctuation do not trigger sentence start`() {
        assertFalse(TextCapitalizationHelper.isSentenceStart("Chào"))
        assertFalse(TextCapitalizationHelper.isSentenceStart("Chào bạn "))
        assertFalse(TextCapitalizationHelper.isSentenceStart("Tôi đang gõ "))
        assertFalse(TextCapitalizationHelper.isSentenceStart("1, 2, 3 "))
    }

    @Test
    fun `ellipses followed by space triggers sentence start`() {
        assertTrue(TextCapitalizationHelper.isSentenceStart("Chờ một chút... "))
    }
}
