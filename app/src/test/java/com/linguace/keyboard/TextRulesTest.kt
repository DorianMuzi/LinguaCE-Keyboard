package com.linguace.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextRulesTest {

    @Test
    fun doubleSpace_afterAWord_givesAPeriod() {
        assertTrue(TextRules.doubleSpaceToPeriod("salam "))
        assertTrue(TextRules.doubleSpaceToPeriod("noxçiyŋ "))
        assertTrue(TextRules.doubleSpaceToPeriod("салам "))
        assertTrue(TextRules.doubleSpaceToPeriod("йоӀ "))   // palotchka = lettre
        assertTrue(TextRules.doubleSpaceToPeriod("2026 "))
    }

    @Test
    fun doubleSpace_afterALetterWithCombiningDot() {
        assertTrue(TextRules.doubleSpaceToPeriod("ç̇ "))
        assertTrue(TextRules.doubleSpaceToPeriod("ak̇ "))
    }

    @Test
    fun doubleSpace_notAfterPunctuationOrSpaces() {
        assertFalse(TextRules.doubleSpaceToPeriod(""))
        assertFalse(TextRules.doubleSpaceToPeriod(" "))
        assertFalse(TextRules.doubleSpaceToPeriod("salam"))      // pas encore d'espace
        assertFalse(TextRules.doubleSpaceToPeriod("salam. "))
        assertFalse(TextRules.doubleSpaceToPeriod("muxa? "))
        assertFalse(TextRules.doubleSpaceToPeriod("salam  "))    // troisième espace
    }

    @Test
    fun settings_parseWithSafeDefaults() {
        assertEquals(Theme.SYSTEM, Theme.of(null))
        assertEquals(Theme.DARK, Theme.of("dark"))
        assertEquals(Theme.SYSTEM, Theme.of("rose"))
        assertEquals(KeyHeight.NORMAL, KeyHeight.of(null))
        assertEquals(KeyHeight.LARGE, KeyHeight.of("large"))
        assertTrue(KeyHeight.SMALL.scale < 1f && KeyHeight.LARGE.scale > 1f)
    }
}
