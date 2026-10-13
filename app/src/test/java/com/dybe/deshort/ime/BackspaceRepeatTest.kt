package com.dybe.deshort.ime

import com.dybe.deshort.input.telex.TelexEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.text.BreakIterator
import java.text.Normalizer

/**
 * Unit tests covering backspace deletion logic, grapheme cluster handling,
 * and composing buffer state.
 */
class BackspaceRepeatTest {

    private lateinit var engine: TelexEngine

    @Before
    fun setUp() {
        engine = TelexEngine()
    }

    @Test
    fun `backspace removes last perceived character instead of tone first`() {
        engine.type('t')
        engine.type('i')
        engine.type('e')
        engine.type('e')
        engine.type('n')
        engine.type('g')
        engine.type('s') // tiếng

        // Backspace should delete 'g', producing 'tiến', NOT stripping tone 's' to 'tiêng'
        val result1 = engine.backspace()
        assertEquals("tiến", result1)

        val result2 = engine.backspace()
        assertEquals("tiế", result2)

        val result3 = engine.backspace()
        assertEquals("ti", result3)

        val result4 = engine.backspace()
        assertEquals("t", result4)

        val result5 = engine.backspace()
        assertEquals("", result5)
        assertTrue(engine.isEmpty)
    }

    @Test
    fun `backspace on single character word clears buffer`() {
        engine.type('a')
        engine.type('s') // 'á'
        assertEquals("", engine.backspace())
        assertTrue(engine.isEmpty)
    }

    @Test
    fun `releasing backspace clears buffer state`() {
        engine.type('x')
        engine.type('i')
        engine.type('n')
        assertFalse(engine.isEmpty)

        engine.reset()
        assertTrue(engine.isEmpty)
    }

    @Test
    fun `grapheme break iterator correctly counts characters in decomposed Vietnamese text`() {
        // Precomposed 'ệ' (U+1EC7) is 1 code unit
        val precomposed = "việt"
        val boundaryPre = BreakIterator.getCharacterInstance()
        boundaryPre.setText(precomposed)
        val endPre = boundaryPre.last()
        val startPre = boundaryPre.previous()
        assertEquals(1, endPre - startPre) // 't' is 1 char

        // Decomposed (NFD) 'việt': 'v', 'i', 'e', '\u0302', '\u0323', 't'
        val decomposed = Normalizer.normalize("việt", Normalizer.Form.NFD)
        // If cursor is at the end of "việ" (after '\u0323')
        val vie = decomposed.substring(0, decomposed.length - 1)
        val boundaryDecomp = BreakIterator.getCharacterInstance()
        boundaryDecomp.setText(vie)
        val endDecomp = boundaryDecomp.last()
        val startDecomp = boundaryDecomp.previous()

        // Decomposed 'ệ' has base 'e' + combining circumflex (\u0302) + combining dot below (\u0323) = 3 code units
        // BreakIterator must treat all 3 units as a SINGLE grapheme cluster
        assertEquals(3, endDecomp - startDecomp)
    }

    @Test
    fun `grapheme break iterator correctly counts surrogate pairs for emoji`() {
        val textWithEmoji = "hello😊"
        val boundary = BreakIterator.getCharacterInstance()
        boundary.setText(textWithEmoji)
        val end = boundary.last()
        val start = boundary.previous()

        // Emoji 😊 is a surrogate pair (2 code units)
        assertEquals(2, end - start)
    }
}
