package com.linguace.keyboard

/**
 * Combinaisons proposées dans la bande du haut (cyrillique), classées par
 * fréquence. En tchétchène, quelques consonnes se combinent très souvent avec
 * Ӏ (кӀант, цӀе, хӀун, гӀант, бӀе) ou avec ъ / ь (къ, хь). Après l'une d'elles,
 * un appui ajoute la lettre suivante, sans espace.
 *
 * Fréquences relevées dans le lexique validé de LinguaCE Core (157 mots, 2026-11-28) :
 * Ӏ après х 11, г 5, к 5, б 4, ц 4 ; ъ après к 12 ; ь après х 7. Échantillon
 * limité (mots de leçons) : la liste se corrigera avec un plus grand texte.
 * Les voyelles à tréma (аь, оь, уь…) ne sont pas proposées : ь est déjà une
 * touche directe (choix de l'auteur, 2026-11-28).
 */
object CombinationHints {
    /** Palochka, toujours en forme majuscule (U+04C0). */
    const val PALOCHKA = "Ӏ"
    private const val HARD = "ъ"
    private const val SOFT = "ь"

    /** Au plus deux propositions : la bande garde de la place pour les mots. */
    const val MAX = 2

    /** Lettre de base → lettres à ajouter, de la plus fréquente à la moins fréquente. */
    private val FOLLOWERS: Map<Char, List<String>> = mapOf(
        'к' to listOf(HARD, PALOCHKA),
        'х' to listOf(PALOCHKA, SOFT),
        'г' to listOf(PALOCHKA),
        'ц' to listOf(PALOCHKA),
        'б' to listOf(PALOCHKA),
        'т' to listOf(PALOCHKA),
        'п' to listOf(PALOCHKA),
        'ч' to listOf(PALOCHKA),
    )

    /**
     * Les combinaisons à proposer après [word] (le mot en cours, avant le
     * curseur) : la lettre de base suivie de la lettre à ajouter, dans la casse
     * de la lettre de base (« К » → « Къ », « КӀ »). Vide si rien à proposer.
     */
    fun chips(word: String): List<String> {
        val last = word.lastOrNull() ?: return emptyList()
        val followers = FOLLOWERS[last.lowercaseChar()] ?: return emptyList()
        return followers.take(MAX).map { last + it }
    }
}
