package com.linguace.keyboard

/**
 * Historique du bouton « annuler » (↶). Chaque entrée dit ce que le clavier a
 * retiré ([Edit.removed]) et inséré ([Edit.inserted]) juste avant le curseur ;
 * annuler fait l'inverse.
 *
 * Il ne vit QU'EN MÉMOIRE : jamais écrit sur le disque, vidé à chaque
 * changement de champ, et rien n'y entre dans un champ de mot de passe.
 * Les lettres d'un même mot (ou les effacements d'affilée) forment une seule
 * entrée : un appui sur ↶ enlève le mot, pas la dernière lettre.
 */
class UndoStack(private val max: Int = 30) {

    data class Edit(val removed: String, val inserted: String)

    private val edits = ArrayDeque<Edit>()

    val isEmpty: Boolean get() = edits.isEmpty()

    fun peek(): Edit? = edits.lastOrNull()

    fun pop(): Edit? = edits.removeLastOrNull()

    fun clear() = edits.clear()

    fun record(removed: String, inserted: String) {
        if (removed.isEmpty() && inserted.isEmpty()) return
        val top = edits.lastOrNull()
        when {
            // Lettres tapées à la suite : un seul mot.
            top != null && removed.isEmpty() && inserted.all(::isWordChar) &&
                top.removed.isEmpty() && top.inserted.isNotEmpty() &&
                isWordChar(top.inserted.last()) ->
                edits[edits.lastIndex] = Edit("", top.inserted + inserted)
            // Effacements à la suite : le texte effacé le plus récent passe devant.
            top != null && inserted.isEmpty() && top.inserted.isEmpty() &&
                top.removed.isNotEmpty() && removed.none { it == ' ' || it == '\n' } &&
                top.removed.none { it == ' ' || it == '\n' } ->
                edits[edits.lastIndex] = Edit(removed + top.removed, "")
            else -> {
                edits.addLast(Edit(removed, inserted))
                if (edits.size > max) edits.removeFirst()
            }
        }
    }

    private fun isWordChar(c: Char) =
        c.isLetterOrDigit() || Character.getType(c) == Character.NON_SPACING_MARK.toInt()
}
