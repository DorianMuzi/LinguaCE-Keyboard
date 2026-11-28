package com.linguace.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CombinationHintsTest {

    private val P = "Ӏ"
    private val HARD = "ъ"
    private val SOFT = "ь"

    @Test
    fun afterK_hardSignComesFirst_thenPalochka() {
        assertEquals(listOf("к$HARD", "к$P"), CombinationHints.chips("к"))
    }

    @Test
    fun afterX_palochkaComesFirst_thenSoftSign() {
        assertEquals(listOf("х$P", "х$SOFT"), CombinationHints.chips("х"))
    }

    @Test
    fun otherConsonants_offerThePalochkaOnly() {
        for (c in "гцбтпч") assertEquals(listOf(c.toString() + P), CombinationHints.chips(c.toString()))
    }

    @Test
    fun theBaseLetterKeepsItsCase() {
        assertEquals(listOf("К$HARD", "К$P"), CombinationHints.chips("К"))
        assertEquals(listOf("Х$P", "Х$SOFT"), CombinationHints.chips("Х"))
        assertEquals(listOf("Ц$P"), CombinationHints.chips("Ц"))
    }

    @Test
    fun onlyTheLastLetterCounts() {
        assertEquals(listOf("т$P"), CombinationHints.chips("мот"))
        assertEquals(listOf("к$HARD", "к$P"), CombinationHints.chips("нахк"))
        assertTrue(CombinationHints.chips("мотта").isEmpty())   // finit par une voyelle
        assertTrue(CombinationHints.chips("к$P").isEmpty())      // finit déjà par Ӏ
    }

    @Test
    fun noVowelWithDiaeresis_noOtherLetters_noLatin() {
        for (c in "аоуюя") assertTrue(CombinationHints.chips(c.toString()).isEmpty())
        for (c in "джзлмнрсфш") assertTrue(CombinationHints.chips(c.toString()).isEmpty())
        assertTrue(CombinationHints.chips("").isEmpty())
        assertTrue(CombinationHints.chips("k").isEmpty())
    }

    @Test
    fun neverMoreThanTwo_andPalochkaIsU04C0() {
        for (c in "гкптцчхб") assertTrue(CombinationHints.chips(c.toString()).size <= CombinationHints.MAX)
        assertEquals(0x04C0, CombinationHints.PALOCHKA.codePointAt(0))
    }
}
