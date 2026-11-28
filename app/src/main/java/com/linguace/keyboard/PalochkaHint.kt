package com.linguace.keyboard

/**
 * Aide à la palochka : en tchétchène, quelques consonnes se combinent très
 * souvent avec Ӏ (гӀ, кӀ, пӀ, тӀ, цӀ, чӀ, хӀ : кӀант, цӀе, хӀун, тӀехь…).
 * Après l'une d'elles, la bande du haut propose la combinaison : un appui
 * ajoute Ӏ juste après la lettre (sans espace). Idée de l'auteur, 2026-11-28.
 */
object PalochkaHint {
    /** Consonnes qui se combinent avec Ӏ, en minuscules. */
    const val BASES = "гкптцчх"

    /** Palochka, toujours en forme majuscule (U+04C0). */
    const val PALOCHKA = "Ӏ"

    /**
     * La combinaison à proposer après [word] (le mot en cours, avant le
     * curseur), ou null. La lettre garde sa casse : « К » → « КӀ ».
     */
    fun chip(word: String): String? {
        val last = word.lastOrNull() ?: return null
        if (last.lowercaseChar() !in BASES) return null
        return last + PALOCHKA
    }
}
