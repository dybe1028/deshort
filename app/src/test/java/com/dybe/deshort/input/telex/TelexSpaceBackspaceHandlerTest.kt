package com.dybe.deshort.input.telex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [TelexSpaceBackspaceHandler] verifying tone changes after Space and Backspace.
 */
class TelexSpaceBackspaceHandlerTest {

    private lateinit var engine: TelexEngine
    private lateinit var handler: TelexSpaceBackspaceHandler

    @Before
    fun setUp() {
        engine = TelexEngine()
        handler = TelexSpaceBackspaceHandler(engine)
    }

    // =========================================================================
    // Core user requirement: vis + Space + Backspace + j -> vị
    // =========================================================================

    @Test
    fun `vis then Space then Backspace then j produces vi-with-dot`() {
        // 1. Type "vis" -> "ví"
        val composed = engine.typeAll("vis")
        assertEquals("ví", composed)

        // 2. Press Space -> commits "ví", editor has "ví "
        handler.onSpace(composed)
        engine.reset()
        val textAfterSpace = "ví "

        // 3. Press Backspace -> removes space, editor has "ví"
        val armed = handler.onBackspace(textAfterSpace)
        assertTrue("Backspace should arm tone resumption for the committed word", armed)
        assertEquals("ví", handler.resumableWord)
        val textAfterBackspace = "ví"

        // 4. Type 'j' -> should change tone from sắc to nặng
        val toneChange = handler.onKey('j', textAfterBackspace)
        assertNotNull("Tone change should succeed", toneChange)
        assertEquals("ví", toneChange!!.first)
        assertEquals("vị", toneChange.second)
    }

    // =========================================================================
    // The same correction after other tone keys
    // =========================================================================

    @Test
    fun `vis then Space then Backspace then f produces vi-with-huyen`() {
        val composed = engine.typeAll("vis")
        handler.onSpace(composed)
        engine.reset()

        assertTrue(handler.onBackspace("ví "))
        val toneChange = handler.onKey('f', "ví")
        assertNotNull(toneChange)
        assertEquals("ví", toneChange!!.first)
        assertEquals("vì", toneChange.second)
    }

    @Test
    fun `vis then Space then Backspace then r produces vi-with-hoi`() {
        val composed = engine.typeAll("vis")
        handler.onSpace(composed)
        engine.reset()

        assertTrue(handler.onBackspace("ví "))
        val toneChange = handler.onKey('r', "ví")
        assertNotNull(toneChange)
        assertEquals("vỉ", toneChange!!.second)
    }

    @Test
    fun `vis then Space then Backspace then x produces vi-with-nga`() {
        val composed = engine.typeAll("vis")
        handler.onSpace(composed)
        engine.reset()

        assertTrue(handler.onBackspace("ví "))
        val toneChange = handler.onKey('x', "ví")
        assertNotNull(toneChange)
        assertEquals("vĩ", toneChange!!.second)
    }

    @Test
    fun `vis then Space then Backspace then z produces bare vi without tone`() {
        val composed = engine.typeAll("vis")
        handler.onSpace(composed)
        engine.reset()

        assertTrue(handler.onBackspace("ví "))
        val toneChange = handler.onKey('z', "ví")
        assertNotNull(toneChange)
        assertEquals("vi", toneChange!!.second)
    }

    @Test
    fun `vis then Space then Backspace then s toggles tone to literal vis`() {
        val composed = engine.typeAll("vis")
        handler.onSpace(composed)
        engine.reset()

        assertTrue(handler.onBackspace("ví "))
        val toneChange = handler.onKey('s', "ví")
        assertNotNull(toneChange)
        assertEquals("vis", toneChange!!.second)
    }

    @Test
    fun `toans then Space then Backspace then f produces toan-with-huyen`() {
        val composed = engine.typeAll("toans") // toán
        assertEquals("toán", composed)
        handler.onSpace(composed)
        engine.reset()

        assertTrue(handler.onBackspace("toán "))
        val toneChange = handler.onKey('f', "toán")
        assertNotNull(toneChange)
        assertEquals("toàn", toneChange!!.second)
    }

    @Test
    fun `tieengs then Space then Backspace then j produces tieng-with-nang`() {
        val composed = engine.typeAll("tieengs") // tiếng
        assertEquals("tiếng", composed)
        handler.onSpace(composed)
        engine.reset()

        assertTrue(handler.onBackspace("tiếng "))
        val toneChange = handler.onKey('j', "tiếng")
        assertNotNull(toneChange)
        assertEquals("tiệng", toneChange!!.second)
    }

    @Test
    fun `hoaf then Space then Backspace then s produces hoa-with-sac`() {
        val composed = engine.typeAll("hoaf") // hoà
        assertEquals("hoà", composed)
        handler.onSpace(composed)
        engine.reset()

        assertTrue(handler.onBackspace("hoà "))
        val toneChange = handler.onKey('s', "hoà")
        assertNotNull(toneChange)
        assertEquals("hoá", toneChange!!.second)
    }

    @Test
    fun `ddawng then Space then Backspace then s produces dang-with-sac`() {
        val composed = engine.typeAll("ddawng") // đăng
        assertEquals("đăng", composed)
        handler.onSpace(composed)
        engine.reset()

        assertTrue(handler.onBackspace("đăng "))
        val toneChange = handler.onKey('s', "đăng")
        assertNotNull(toneChange)
        assertEquals("đắng", toneChange!!.second)
    }

    // =========================================================================
    // Negative cases: Preserve literal characters and ordinary backspace
    // =========================================================================

    @Test
    fun `backspace on ordinary text does not arm tone resumption`() {
        // No word was composed/committed by Space
        handler.onSpace(null)
        val armed = handler.onBackspace("hello ")
        assertFalse(armed)
        assertNull(handler.resumableWord)

        // Typing 'j' does not modify "hello"
        val toneChange = handler.onKey('j', "hello")
        assertNull(toneChange)
    }

    @Test
    fun `typing a non-tone letter after space and backspace does not modify syllable`() {
        val composed = engine.typeAll("vis")
        handler.onSpace(composed)
        engine.reset()

        assertTrue(handler.onBackspace("ví "))
        // 'k' is not a tone key
        val toneChange = handler.onKey('k', "ví")
        assertNull("Non-tone key should not modify syllable", toneChange)
        assertNull("Handler should reset after non-tone key", handler.resumableWord)
    }

    @Test
    fun `repeated space presses invalidate tone resumption`() {
        val composed = engine.typeAll("vis")
        handler.onSpace(composed) // First space after word -> ví 
        handler.onSpace(null)     // Second space -> ví  

        // Backspace on double space deletes one space, but cursor is still after a space
        val armed = handler.onBackspace("ví  ")
        assertFalse("Repeated spaces should not arm tone resumption", armed)
        assertNull(handler.resumableWord)

        // Typing 'j' should be literal
        val toneChange = handler.onKey('j', "ví ")
        assertNull(toneChange)
    }

    @Test
    fun `cursor movement invalidates tone resumption`() {
        val composed = engine.typeAll("vis")
        handler.onSpace(composed)
        engine.reset()

        assertTrue(handler.onBackspace("ví "))
        assertEquals("ví", handler.resumableWord)

        // User moved cursor away from "ví" (e.g. textBeforeCursor is now something else)
        val toneChange = handler.onKey('j', "other text")
        assertNull("Cursor away from word should not modify", toneChange)
        assertNull(handler.resumableWord)
    }

    @Test
    fun `layout switch or reset invalidates tone resumption`() {
        val composed = engine.typeAll("vis")
        handler.onSpace(composed)
        engine.reset()

        assertTrue(handler.onBackspace("ví "))
        // Switching layout (?123 -> ABC) calls reset()
        handler.reset()

        assertNull(handler.resumableWord)
        val toneChange = handler.onKey('j', "ví")
        assertNull(toneChange)
    }
}

private fun TelexEngine.typeAll(input: String): String {
    var result = ""
    for (ch in input) {
        result = this.type(ch)
    }
    return result
}
