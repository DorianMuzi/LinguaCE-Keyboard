package com.linguace.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class GraphemesTest {
    @Test
    fun deletesWholeLetters() {
        assertEquals(0, Graphemes.lastLength(""))
        assertEquals(1, Graphemes.lastLength("ẋa"))
        assertEquals(1, Graphemes.lastLength("ẋ"))
        // ç̇ = ç + U+0307 : une seule lettre, deux unités.
        assertEquals(2, Graphemes.lastLength("Naxç̇"))
        assertEquals(2, Graphemes.lastLength("q̇"))
        assertEquals(1, Graphemes.lastLength("Ӏ"))
        // Émoji hors du plan de base (paire de substitution).
        assertEquals(2, Graphemes.lastLength("ok🔥"))
    }
}
