package com.linguace.keyboard

/** Petites règles de saisie, sans Android (testées sur le poste). */
object TextRules {

    /**
     * Double espace → point (comme Gboard) : un deuxième espace tapé juste
     * après un mot remplace l'espace précédent par « . ». Seulement après une
     * lettre ou un chiffre (pas après « . », « ? »… ni deux espaces de suite).
     *
     * @param before texte avant le curseur, AVANT le deuxième espace.
     */
    fun doubleSpaceToPeriod(before: CharSequence): Boolean {
        if (before.length < 2 || before[before.length - 1] != ' ') return false
        var i = before.length - 2
        // « ç̇ » : lettre + point combinant ; on remonte jusqu'à la lettre.
        while (i > 0 && Character.getType(before[i]) == Character.NON_SPACING_MARK.toInt()) i--
        return before[i].isLetterOrDigit()
    }
}
