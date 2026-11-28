package com.linguace.keyboard

/**
 * Mouvements du clavier : trois animations courtes, jamais sur les lettres
 * (principe de « fréquence d'usage » : ce qui se répète sans cesse reste
 * instantané).
 *
 * - la bulle des variantes grandit depuis sa touche ([POPUP_MS]) ;
 * - le changement de panneau (lettres ↔ emoji ↔ symboles) se fond et glisse
 *   de quelques points ([PANEL_MS]) ;
 * - la bande du haut (suggestions / outils) apparaît en fondu, mot après mot
 *   ([STRIP_MS], décalage [STRIP_STAGGER_MS]).
 *
 * Chaque animation est une simple fonction de l'heure : si un nouveau geste
 * arrive, il repart de zéro sans rien bloquer (mouvement interruptible).
 */
object Motion {
    const val POPUP_MS = 90L
    const val PANEL_MS = 150L
    const val STRIP_MS = 100L
    const val STRIP_STAGGER_MS = 30L

    /** Avancement 0…1 ; 1 si l'animation n'a pas démarré (start = 0) ou est finie. */
    fun progress(now: Long, start: Long, duration: Long, delay: Long = 0L): Float {
        if (start == 0L || duration <= 0L) return 1f
        return ((now - start - delay).toFloat() / duration).coerceIn(0f, 1f)
    }

    /** Démarrage vif puis freinage (« slow out ») : 1 − (1 − t)². */
    fun easeOut(t: Float): Float {
        val u = 1f - t.coerceIn(0f, 1f)
        return 1f - u * u
    }

    /** Une animation est-elle encore en cours ? */
    fun running(now: Long, start: Long, duration: Long, maxDelay: Long = 0L): Boolean =
        start != 0L && now - start < duration + maxDelay

    /**
     * Le réglage Android « durée des animations » à 0 (« supprimer les
     * animations ») coupe aussi les nôtres.
     */
    fun enabled(durationScale: Float): Boolean = durationScale > 0f
}
