package com.linguace.keyboard

import com.linguace.keyboard.Alphabet.DOT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutsTest {

    @Test
    fun alphabet_isTheCoreSpec() {
        assertEquals(41, Alphabet.LETTERS.size)
        assertEquals(41, Alphabet.LETTERS.toSet().size)
        assertEquals(
            "ä ċ ç ç$DOT ə ġ k$DOT ŋ ö ṗ q$DOT ş ṫ ü ẋ ƶ",
            Alphabet.SPECIAL.joinToString(" "),
        )
        assertEquals(0x015F, "ş".codePointAt(0)) // cédille, pas ș
        assertEquals(0x01B6, "ƶ".codePointAt(0))
        assertEquals(0x0259, "ə".codePointAt(0))
    }

    @Test
    fun latin_reachesEveryLetter() {
        val typeable = Layouts.LATIN.typeable
        for (l in Alphabet.LETTERS) assertTrue("$l inaccessible", l in typeable)
    }

    @Test
    fun w_isOutsideTheAlphabet_butTypeableForForeignWords() {
        assertFalse("w" in Alphabet.LETTERS)
        // Pas de touche w : appui long sur ə, à la place de w en QWERTY.
        val keys = Layouts.LATIN.rows.flatten()
        assertFalse(keys.any { it.label == "w" })
        val schwa = Layouts.LATIN.rows[1][1]
        assertEquals("ə", schwa.label)
        assertEquals(listOf("w"), schwa.popup)
        assertEquals("W", Key("w").output(true))
    }

    @Test
    fun latin_topRowIsTheAuthorsChoice() {
        assertEquals(
            "ä ç ç$DOT ġ ŋ q$DOT ş ṫ ẋ ƶ",
            Layouts.LATIN.rows.first().joinToString(" ") { it.label },
        )
        // ə à la place de w, juste après q.
        assertEquals("ə", Layouts.LATIN.rows[1][1].label)
    }

    @Test
    fun combiningLetters_areEmittedAsLetterPlusDot() {
        val key = Layouts.LATIN.rows.first().first { it.label.startsWith("ç") && it.label.length == 2 }
        assertEquals("ç̇", key.output(false))
        assertEquals("Ç̇", key.output(true))
        assertEquals(2, key.output(true).length)
    }

    @Test
    fun cyrillic_hasPalochka_neverLowercased() {
        val palochka = Layouts.CYRILLIC.rows.flatten().first { it.label == "Ӏ" }
        assertEquals("Ӏ", palochka.output(false))
        assertEquals("Ӏ", palochka.output(true))
        assertEquals(0x04C0, palochka.label.codePointAt(0))
        for (l in "абвгдеёжзийклмнопрстуфхцчшщъыьэюя".map { it.toString() }) {
            assertTrue("$l inaccessible", l in Layouts.CYRILLIC.typeable)
        }
    }

    @Test
    fun everyRow_fitsTheLayoutWidth() {
        for (layout in listOf(Layouts.LATIN, Layouts.CYRILLIC, Layouts.SYMBOLS)) {
            for (row in layout.rows) {
                val w = row.sumOf { it.width.toDouble() }
                assertTrue("${layout.id} : rangée de $w > ${layout.units}", w <= layout.units + 1e-6)
            }
        }
        assertEquals(10f, Layouts.LATIN.units)
        assertEquals(11f, Layouts.CYRILLIC.units)
    }
}
