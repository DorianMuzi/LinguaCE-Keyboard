package com.linguace.keyboard

import java.util.Locale

/**
 * L'alphabet latin tchétchène (Muziŋ Dar) : inventaire public des 41 lettres,
 * dans l'ordre validé par l'auteur (spécification du LinguaCE Core, 0.1).
 * Identique à `lib/services/muzin_dar.dart` de LinguaCE App : les deux
 * produits émettent exactement les mêmes points de code.
 *
 * Encodage NFC. ç̇, k̇ et q̇ n'ont pas de caractère précomposé dans Unicode :
 * lettre de base + U+0307 (point en chef combinant), émis d'un seul coup.
 */
object Alphabet {
    const val DOT = "̇"

    val LETTERS: List<String> = listOf(
        "a", "ä", "b", "c", "ċ", "ç", "ç$DOT", "d", "e", "ə", "f", "g", "ġ", "h",
        "i", "j", "k", "k$DOT", "l", "m", "n", "ŋ", "o", "ö", "p", "ṗ", "q",
        "q$DOT", "r", "s", "ş", "t", "ṫ", "u", "ü", "v", "x", "ẋ", "y", "z", "ƶ",
    )

    /** Lettres absentes d'un clavier latin ordinaire. */
    val SPECIAL: List<String> = LETTERS.filter { l -> l.any { it.code > 0x7F } }

    /** Majuscule : seule la lettre de base change (ç̇ → Ç̇). */
    fun upper(s: String): String = s.uppercase(Locale.ROOT)
}
