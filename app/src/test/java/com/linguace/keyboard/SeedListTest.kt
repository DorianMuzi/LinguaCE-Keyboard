package com.linguace.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Contrôle de la liste de base publique des suggestions (assets/seed_words.tsv)
 * avant chaque publication : graphie, doublons, format. Toute nouvelle entrée
 * passe par ces vérifications.
 */
class SeedListTest {

    private val raw = File("src/main/assets/seed_words.tsv").readText()

    private val lines = raw.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .toList()

    private val words = lines.map { it.split('\t')[0] }

    private fun isCyrillic(w: String) = w.any { it in 'Ѐ'..'ӿ' }

    /** Découpe en lettres de l'alphabet : « ç̇ » = ç + point combinant. */
    private fun letters(w: String): List<String> {
        val out = mutableListOf<String>()
        for (c in w) {
            if (c == '̇' && out.isNotEmpty()) out[out.lastIndex] += c else out += c.toString()
        }
        return out
    }

    @Test
    fun everyLine_isWordTabPositiveWeight() {
        for (line in lines) {
            val parts = line.split('\t')
            assertEquals("ligne mal formée : « $line »", 2, parts.size)
            val weight = parts[1].toIntOrNull()
            assertTrue("poids invalide : « $line »", weight != null && weight > 0)
            assertTrue("pas un mot : « ${parts[0]} »", WordStore().isWord(parts[0]))
        }
    }

    @Test
    fun latinWords_useOnlyTheChechenAlphabet() {
        val alphabet = Alphabet.LETTERS.toSet()
        for (w in words.filterNot(::isCyrillic)) {
            for (l in letters(w)) {
                assertTrue("« $w » : « $l » n'est pas une lettre de l'alphabet", l in alphabet)
            }
        }
    }

    @Test
    fun cyrillicWords_haveNoLatinLetter_andTheRealPalochka() {
        for (w in words.filter(::isCyrillic)) {
            assertTrue("« $w » contient une lettre latine", w.none { it in 'a'..'z' || it in 'A'..'Z' })
            assertTrue("« $w » : palotchka minuscule ӏ (U+04CF)", 'ӏ' !in w)
            assertTrue("« $w » : chiffre 1 au lieu du palotchka", '1' !in w)
        }
    }

    @Test
    fun noDuplicates_evenIgnoringAccents() {
        val dup = words.groupBy { Fold.fold(it) }.filterValues { it.size > 1 }
        assertTrue("doublons : $dup", dup.isEmpty())
    }

    @Test
    fun bothScripts_coverTheSameWords() {
        val latin = words.count { !isCyrillic(it) }
        val cyrillic = words.count(::isCyrillic)
        assertEquals("autant de mots en latin qu'en cyrillique", latin, cyrillic)
        assertTrue("liste de base trop courte : $latin", latin >= 50)
    }
}
