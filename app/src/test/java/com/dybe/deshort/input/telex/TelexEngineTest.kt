package com.dybe.deshort.input.telex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive unit tests for [TelexEngine].
 */
class TelexEngineTest {

    private lateinit var engine: TelexEngine

    @Before
    fun setUp() {
        engine = TelexEngine()
    }

    // =========================================================================
    // Core user-specified required examples
    // =========================================================================

    @Test fun `mowsi produces moi with horn and sac`() {
        assertEquals("mới", engine.typeAll("mowsi"))
    }

    @Test fun `nhuwowcj produces nhuoc with horns and nang`() {
        assertEquals("nhược", engine.typeAll("nhuwowcj"))
    }

    @Test fun `tieengs produces tieng with sac`() {
        assertEquals("tiếng", engine.typeAll("tieengs"))
    }

    @Test fun `Vieetj produces Viet with nang and capital V`() {
        assertEquals("Việt", engine.typeAll("Vieetj"))
    }

    @Test fun `ddawng produces dang with d-stroke and breve`() {
        assertEquals("đăng", engine.typeAll("ddawng"))
    }

    @Test fun `hoaf produces hoa with huyen on a`() {
        assertEquals("hoà", engine.typeAll("hoaf"))
    }

    // =========================================================================
    // Bug fixes: Out-of-order typing and modifier 'w'
    // =========================================================================

    @Test fun `nhuocjw produces nhuoc instead of nhuocw`() {
        assertEquals("nhược", engine.typeAll("nhuocjw"))
    }

    @Test fun `nhuocwj produces nhuoc with tone after w`() {
        assertEquals("nhược", engine.typeAll("nhuocwj"))
    }

    @Test fun `moisw produces moi with horn and sac instead of moiw`() {
        assertEquals("mới", engine.typeAll("moisw"))
    }

    @Test fun `thuongw produces thuong with horns`() {
        assertEquals("thương", engine.typeAll("thuongw"))
    }

    @Test fun `duongw produces duong with horns`() {
        assertEquals("dương", engine.typeAll("duongw"))
    }

    @Test fun `dduongw produces d-stroke-uong with horns`() {
        assertEquals("đương", engine.typeAll("dduongw"))
    }

    @Test fun `tow produces to with horn`() {
        assertEquals("tơ", engine.typeAll("tow"))
    }

    @Test fun `tuw produces tu with horn`() {
        assertEquals("tư", engine.typeAll("tuw"))
    }

    @Test fun `taw produces ta with breve`() {
        assertEquals("tă", engine.typeAll("taw"))
    }

    @Test fun `standalone w produces u-horn`() {
        assertEquals("ư", engine.typeAll("w"))
    }

    @Test fun `standalone ws produces u-horn with sac`() {
        assertEquals("ứ", engine.typeAll("ws"))
    }

    @Test fun `standalone wf produces u-horn with huyen`() {
        assertEquals("ừ", engine.typeAll("wf"))
    }

    @Test fun `double w produces literal w`() {
        assertEquals("w", engine.typeAll("ww"))
    }

    @Test fun `w does not modify non-modifiable vowels`() {
        assertEquals("tinw", engine.typeAll("tinw"))
        engine.reset()
        assertEquals("two", engine.typeAll("two"))
    }

    // =========================================================================
    // Vowel digraphs
    // =========================================================================

    @Test fun `aa produces a-circumflex`() {
        assertEquals("â", engine.typeAll("aa"))
    }

    @Test fun `aw produces a-breve`() {
        assertEquals("ă", engine.typeAll("aw"))
    }

    @Test fun `ee produces e-circumflex`() {
        assertEquals("ê", engine.typeAll("ee"))
    }

    @Test fun `oo produces o-circumflex`() {
        assertEquals("ô", engine.typeAll("oo"))
    }

    @Test fun `ow produces o-horn`() {
        assertEquals("ơ", engine.typeAll("ow"))
    }

    @Test fun `uw produces u-horn`() {
        assertEquals("ư", engine.typeAll("uw"))
    }

    // =========================================================================
    // Consonant digraph
    // =========================================================================

    @Test fun `dd produces d-stroke`() {
        assertEquals("đ", engine.typeAll("dd"))
    }

    @Test fun `dd only applies at consonant position`() {
        assertEquals("add", engine.typeAll("add"))
    }

    // =========================================================================
    // Tone marks on single vowels
    // =========================================================================

    @Test fun `as produces a-sac`() {
        assertEquals("á", engine.typeAll("as"))
    }

    @Test fun `af produces a-huyen`() {
        assertEquals("à", engine.typeAll("af"))
    }

    @Test fun `ar produces a-hoi`() {
        assertEquals("ả", engine.typeAll("ar"))
    }

    @Test fun `ax produces a-nga`() {
        assertEquals("ã", engine.typeAll("ax"))
    }

    @Test fun `aj produces a-nang`() {
        assertEquals("ạ", engine.typeAll("aj"))
    }

    // =========================================================================
    // Changing tone marks
    // =========================================================================

    @Test fun `toans plus f changes sac to huyen`() {
        assertEquals("toàn", engine.typeAll("toansf"))
    }

    @Test fun `toanf plus s changes huyen to sac`() {
        assertEquals("toán", engine.typeAll("toanfs"))
    }

    @Test fun `toans plus r changes sac to hoi`() {
        assertEquals("toản", engine.typeAll("toansr"))
    }

    @Test fun `toanr plus x changes hoi to nga`() {
        assertEquals("toãn", engine.typeAll("toanrx"))
    }

    @Test fun `toanx plus j changes nga to nang`() {
        assertEquals("toạn", engine.typeAll("toanxj"))
    }

    // =========================================================================
    // Repeated tone key toggle / escape to literal
    // =========================================================================

    @Test fun `test plus s produces test-with-sac`() {
        assertEquals("tést", engine.typeAll("tests"))
    }

    @Test fun `test plus ss produces literal tests`() {
        assertEquals("tests", engine.typeAll("testss"))
    }

    @Test fun `a plus ff produces literal af`() {
        assertEquals("af", engine.typeAll("aff"))
    }

    @Test fun `a plus rr produces literal ar`() {
        assertEquals("ar", engine.typeAll("arr"))
    }

    @Test fun `a plus xx produces literal ax`() {
        assertEquals("ax", engine.typeAll("axx"))
    }

    @Test fun `a plus jj produces literal aj`() {
        assertEquals("aj", engine.typeAll("ajj"))
    }

    @Test fun `des produces de with sac`() {
        assertEquals("dé", engine.typeAll("des"))
    }

    @Test fun `dess produces literal des`() {
        assertEquals("des", engine.typeAll("dess"))
    }

    @Test fun `desshorrt produces literal deshort`() {
        assertEquals("desh", engine.typeAll("dessh"))
        engine.reset()
        assertEquals("desho", engine.typeAll("dessho"))
        engine.reset()
        assertEquals("deshỏ", engine.typeAll("desshor"))
        engine.reset()
        assertEquals("deshor", engine.typeAll("desshorr"))
        engine.reset()
        assertEquals("deshort", engine.typeAll("desshorrt"))
    }

    // =========================================================================
    // Tone cancellation with 'z'
    // =========================================================================

    @Test fun `asz cancels tone back to a`() {
        assertEquals("a", engine.typeAll("asz"))
    }

    @Test fun `aasz cancels tone back to a-circumflex`() {
        assertEquals("â", engine.typeAll("aasz"))
    }

    @Test fun `toansz cancels tone back to toan`() {
        assertEquals("toan", engine.typeAll("toansz"))
    }

    // =========================================================================
    // Multi-vowel combinations & Vietnamese orthography
    // =========================================================================

    @Test fun `quas produces qua-with-sac`() {
        assertEquals("quá", engine.typeAll("quas"))
    }

    @Test fun `quyeest produces quyet-with-sac`() {
        assertEquals("quyết", engine.typeAll("quyeest"))
    }

    @Test fun `quyeenr produces quyen-with-hoi`() {
        assertEquals("quyển", engine.typeAll("quyeenr"))
    }

    @Test fun `gias produces gia-with-sac`() {
        assertEquals("giá", engine.typeAll("gias"))
    }

    @Test fun `gios produces gio-with-sac`() {
        assertEquals("gió", engine.typeAll("gios"))
    }

    @Test fun `giups produces giup-with-sac`() {
        assertEquals("giúp", engine.typeAll("giups"))
    }

    @Test fun `giuwowngf produces giuong-with-huyen`() {
        assertEquals("giường", engine.typeAll("giuwowngf"))
    }

    @Test fun `ai plus nang produces a-nang-i`() {
        assertEquals("ại", engine.typeAll("aij"))
    }

    @Test fun `ao plus sac produces a-sac-o`() {
        assertEquals("áo", engine.typeAll("aos"))
    }

    @Test fun `ay plus nang produces a-nang-y`() {
        assertEquals("ạy", engine.typeAll("ayj"))
    }

    @Test fun `oi plus sac produces o-sac-i`() {
        assertEquals("ói", engine.typeAll("ois"))
    }

    @Test fun `oe plus hoi produces o-e-hoi`() {
        assertEquals("oẻ", engine.typeAll("oer"))
    }

    @Test fun `uy plus sac produces u-y-sac`() {
        assertEquals("uý", engine.typeAll("uys"))
    }

    @Test fun `thuyf produces thuy with huyen on y`() {
        assertEquals("thuỳ", engine.typeAll("thuyf"))
    }

    @Test fun `uon plus sac produces u-o-sac-n`() {
        assertEquals("uón", engine.typeAll("uons"))
    }

    @Test fun `mua without tone remains mua`() {
        assertEquals("mua", engine.typeAll("mua"))
    }

    @Test fun `muas produces mua-sac`() {
        assertEquals("múa", engine.typeAll("muas"))
    }

    @Test fun `muaw produces mua with horn on u`() {
        assertEquals("mưa", engine.typeAll("muaw"))
    }

    @Test fun `muaws produces mua with horn on u and sac`() {
        assertEquals("mứa", engine.typeAll("muaws"))
    }

    @Test fun `chuaw produces chua with horn on u`() {
        assertEquals("chưa", engine.typeAll("chuaw"))
    }

    // =========================================================================
    // Uppercase and mixed case handling
    // =========================================================================

    @Test fun `uppercase A with tone`() {
        assertEquals("Á", engine.typeAll("As"))
    }

    @Test fun `uppercase AA produces A-circumflex`() {
        assertEquals("Â", engine.typeAll("AA"))
    }

    @Test fun `Tooi produces T-o-circumflex-i`() {
        assertEquals("Tôi", engine.typeAll("Tooi"))
    }

    @Test fun `all uppercase VIEETJ produces VIET with all caps`() {
        assertEquals("VIỆT", engine.typeAll("VIEETJ"))
    }

    @Test fun `lowercase a and uppercase A produces uppercase A-circumflex`() {
        assertEquals("Â", engine.typeAll("aA"))
    }

    @Test fun `uppercase A and lowercase a produces uppercase A-circumflex`() {
        assertEquals("Â", engine.typeAll("Aa"))
    }

    @Test fun `mixed case dD produces uppercase D-stroke`() {
        assertEquals("Đ", engine.typeAll("dD"))
    }

    @Test fun `mixed case Dd produces uppercase D-stroke`() {
        assertEquals("Đ", engine.typeAll("Dd"))
    }

    // =========================================================================
    // Repeated literal keys
    // =========================================================================

    @Test fun `triple a produces raw aaa`() {
        assertEquals("aaa", engine.typeAll("aaa"))
    }

    @Test fun `oooo does not become oo-circumflex`() {
        assertEquals("oooo", engine.typeAll("oooo"))
    }

    @Test fun `uuuu remains raw uuuu`() {
        assertEquals("uuuu", engine.typeAll("uuuu"))
    }

    // =========================================================================
    // Backspace during composition: Grapheme-aware deletion
    // =========================================================================

    @Test fun `backspace on tieng deletes character-by-character without stripping tone first`() {
        engine.typeAll("tieengs") // "tiếng"
        assertEquals("tiến", engine.backspace()) // deletes 'g'
        assertEquals("tiế", engine.backspace())  // deletes 'n'
        assertEquals("ti", engine.backspace())   // deletes 'ế'
        assertEquals("t", engine.backspace())    // deletes 'i'
        assertEquals("", engine.backspace())     // deletes 't'
        assertTrue(engine.isEmpty)
    }

    @Test fun `backspace on moi deletes character-by-character`() {
        engine.typeAll("mowsi") // "mới"
        assertEquals("mớ", engine.backspace()) // deletes 'i'
        assertEquals("m", engine.backspace())  // deletes 'ớ'
        assertEquals("", engine.backspace())   // deletes 'm'
        assertTrue(engine.isEmpty)
    }

    @Test fun `backspace on nhuoc deletes character-by-character`() {
        engine.typeAll("nhuwowcj") // "nhược"
        assertEquals("nhượ", engine.backspace()) // deletes 'c'
        assertEquals("như", engine.backspace())  // deletes 'ợ'
        assertEquals("nh", engine.backspace())   // deletes 'ư'
        assertEquals("n", engine.backspace())    // deletes 'h'
        assertEquals("", engine.backspace())     // deletes 'n'
        assertTrue(engine.isEmpty)
    }

    @Test fun `continued typing after backspace continues composing correctly`() {
        engine.typeAll("tieengs") // "tiếng"
        assertEquals("tiến", engine.backspace()) // deletes 'g' -> 'tiến'

        // Re-typing 'g' restores 'tiếng'
        assertEquals("tiếng", engine.type('g'))

        // Delete 'g' then 'n' -> 'tiế'
        assertEquals("tiến", engine.backspace())
        assertEquals("tiế", engine.backspace())

        // Type 't' -> 'tiết'
        assertEquals("tiết", engine.type('t'))

        // Change tone to huyen -> 'tiệt' (nang) or 'tiền' -> 't' + 'j' -> 'tiệt'
        assertEquals("tiệt", engine.type('j'))
    }

    @Test fun `backspace on single toned character clears composing`() {
        engine.typeAll("as") // "á"
        assertEquals("", engine.backspace())
        assertTrue(engine.isEmpty)
    }

    @Test fun `backspace on empty returns empty`() {
        assertEquals("", engine.backspace())
        assertTrue(engine.isEmpty)
    }

    // =========================================================================
    // Reset and word isolation
    // =========================================================================

    @Test fun `words processed independently after reset`() {
        val word1 = engine.typeAll("xin")
        assertEquals("xin", word1)

        engine.reset()
        assertTrue(engine.isEmpty)

        val word2 = engine.typeAll("chao")
        assertEquals("chao", word2)
    }

    // =========================================================================
    // Applying tone to already composed words
    // =========================================================================

    @Test fun `applyToneToComposed modifies vi to vi-with-dot`() {
        assertEquals("vị", engine.applyToneToComposed("ví", 'j'))
    }

    @Test fun `applyToneToComposed modifies vi to vi-with-huyen`() {
        assertEquals("vì", engine.applyToneToComposed("ví", 'f'))
    }

    @Test fun `applyToneToComposed modifies vi to vi-with-hoi`() {
        assertEquals("vỉ", engine.applyToneToComposed("ví", 'r'))
    }

    @Test fun `applyToneToComposed modifies vi to vi-with-nga`() {
        assertEquals("vĩ", engine.applyToneToComposed("ví", 'x'))
    }

    @Test fun `applyToneToComposed cancels tone with z`() {
        assertEquals("vi", engine.applyToneToComposed("ví", 'z'))
    }

    @Test fun `applyToneToComposed returns null on invalid or non-tone inputs`() {
        org.junit.Assert.assertNull(engine.applyToneToComposed("123", 'j'))
        org.junit.Assert.assertNull(engine.applyToneToComposed("ví", 'k'))
        org.junit.Assert.assertNull(engine.applyToneToComposed("", 'j'))
    }
}

private fun TelexEngine.typeAll(input: String): String {
    var result = ""
    for (ch in input) {
        result = this.type(ch)
    }
    return result
}
