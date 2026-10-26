package com.dybe.deshort.ime.toolbar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiDataTest {

    @Test
    fun testMoaiEmojiExistsInObjectsCategory() {
        val objectsCategory = EmojiData.categories.find { it.label == "Objects" }
        assertNotNull("Objects category must exist", objectsCategory)
        assertTrue("Objects category must contain 🗿", objectsCategory!!.emojis.contains("🗿"))
    }

    @Test
    fun testMoaiEmojiCodePoint() {
        val moai = "🗿"
        assertEquals("Moai length in UTF-16 characters must be 2", 2, moai.length)
        val codePoint = moai.codePointAt(0)
        assertEquals("Moai code point must be U+1F5FF", 0x1F5FF, codePoint)
    }

    @Test
    fun testNoDuplicateEmojisInCategory() {
        for (category in EmojiData.categories) {
            val seen = mutableSetOf<String>()
            val duplicates = mutableListOf<String>()
            for (emoji in category.emojis) {
                if (!seen.add(emoji)) {
                    duplicates.add(emoji)
                }
            }
            assertTrue(
                "Category '${category.label}' contains duplicate emojis: $duplicates",
                duplicates.isEmpty()
            )
        }
    }

    @Test
    fun testAllCategoriesPreserved() {
        val expectedLabels = listOf("Recent", "Smileys", "People", "Nature", "Food", "Travel", "Objects", "Symbols")
        val actualLabels = EmojiData.categories.map { it.label }
        assertEquals("All 8 emoji categories must be preserved", expectedLabels, actualLabels)
        assertTrue("Display categories should drop Recent", EmojiData.displayCategories.none { it.label == "Recent" })
        assertEquals(7, EmojiData.displayCategories.size)
    }
}
