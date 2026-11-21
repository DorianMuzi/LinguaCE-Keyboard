package com.linguace.keyboard

import android.os.Build
import android.view.View
import android.view.WindowInsets

/**
 * Android 15 dessine les écrans bord à bord : on écarte le contenu de la
 * barre d'état, de la barre de navigation et du clavier ouvert, sans quoi le
 * haut du texte passe sous la barre d'état (constaté sur le téléphone de
 * l'auteur).
 */
fun View.padForSystemBars() {
    // Les marges propres de la vue s'AJOUTENT à celles des barres du système.
    val left0 = paddingLeft
    val top0 = paddingTop
    val right0 = paddingRight
    val bottom0 = paddingBottom
    setOnApplyWindowInsetsListener { v, insets ->
        val top: Int
        val bottom: Int
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            val ime = insets.getInsets(WindowInsets.Type.ime())
            top = bars.top
            bottom = maxOf(bars.bottom, ime.bottom)
        } else {
            @Suppress("DEPRECATION")
            top = insets.systemWindowInsetTop
            @Suppress("DEPRECATION")
            bottom = insets.systemWindowInsetBottom
        }
        v.setPadding(left0, top0 + top, right0, bottom0 + bottom)
        insets
    }
}
