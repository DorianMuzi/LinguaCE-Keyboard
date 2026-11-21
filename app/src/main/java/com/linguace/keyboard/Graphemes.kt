package com.linguace.keyboard

import java.text.BreakIterator
import java.util.Locale

object Graphemes {
    /**
     * Longueur (en unités UTF-16) de la dernière lettre visible de [text] :
     * ⌫ efface « ç̇ » (2 points de code) ou un émoji entier d'un seul coup,
     * jamais un point orphelin.
     */
    fun lastLength(text: CharSequence): Int {
        if (text.isEmpty()) return 0
        val it = BreakIterator.getCharacterInstance(Locale.ROOT)
        it.setText(text.toString())
        val end = it.last()
        val start = it.previous()
        return if (start == BreakIterator.DONE) text.length else end - start
    }
}
