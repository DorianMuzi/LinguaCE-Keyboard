package com.linguace.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PalochkaHintTest {

    @Test
    fun afterEachCombiningConsonant_thePalochkaIsOffered() {
        for (c in "гкптцчх") {
            assertEquals(c.toString() + "Ӏ", PalochkaHint.chip(c.toString()))
        }
    }

    @Test
    fun theBaseLetterKeepsItsCase() {
        assertEquals("КӀ", PalochkaHint.chip("К"))
        assertEquals("ЦӀ", PalochkaHint.chip("Ц"))
    }

    @Test
    fun onlyTheLastLetterOfTheWordCounts() {
        assertEquals("тӀ", PalochkaHint.chip("мот"))
        assertEquals("кӀ", PalochkaHint.chip("нахк"))
        assertNull(PalochkaHint.chip("мотта"))   // finit par une voyelle
        assertNull(PalochkaHint.chip("кӀ"))       // finit déjà par Ӏ
    }

    @Test
    fun noHintForOtherConsonants_orEmptyWord() {
        for (c in "бвджзлмнрсфш") assertNull(PalochkaHint.chip(c.toString()))
        assertNull(PalochkaHint.chip(""))
        assertNull(PalochkaHint.chip("k"))        // latin : pas de palochka
    }

    @Test
    fun palochkaIsTheRealU04C0() {
        assertEquals(0x04C0, PalochkaHint.PALOCHKA.codePointAt(0))
    }
}
