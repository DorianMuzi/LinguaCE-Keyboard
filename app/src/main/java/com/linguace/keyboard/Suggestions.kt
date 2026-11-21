package com.linguace.keyboard

import java.text.Normalizer
import java.util.Locale

/**
 * Normalisation pour COMPARER (jamais pour afficher) : minuscules, sans
 * diacritiques. Taper « noxciyn » retrouve « noxçiyŋ » : un apprenant sans
 * habitude des lettres tchétchènes obtient quand même la bonne graphie.
 * Même logique que `TextFold` de LinguaCE App.
 */
object Fold {
    private val extra = mapOf('ŋ' to "n", 'ƶ' to "z", 'ə' to "e", 'Ӏ' to "ӏ", 'ı' to "i")
    private val marks = Regex("\\p{Mn}+")

    fun fold(s: String): String {
        val lower = s.lowercase(Locale.ROOT)
        val sb = StringBuilder(lower.length)
        for (c in lower) sb.append(extra[c] ?: c)
        return marks.replace(Normalizer.normalize(sb, Normalizer.Form.NFD), "")
    }
}

/**
 * Les mots proposés au-dessus du clavier : une petite liste de base publique
 * ([seed], mot → poids) et les mots que l'utilisateur tape, appris sur le
 * téléphone seulement ([learned]). Rien ne quitte l'appareil.
 */
class WordStore(private val seed: Map<String, Int> = emptyMap()) {

    private val learned = HashMap<String, Int>()

    val learnedCount: Int get() = learned.size

    /** Un mot = au moins deux lettres (ç̇ et Ӏ comptent), sans chiffre. */
    fun isWord(w: String): Boolean =
        w.length in 2..MAX_LENGTH &&
            w.all { it.isLetter() || Character.getType(it) == Character.NON_SPACING_MARK.toInt() } &&
            w.any { it.isLetter() }

    /** Retient un mot tapé (en minuscules, la casse est rendue à l'affichage). */
    fun learn(word: String) {
        if (!isWord(word)) return
        val w = lower(word)
        learned[w] = (learned[w] ?: 0) + 1
        if (learned.size > MAX_LEARNED) {
            // On oublie les mots les moins utilisés.
            learned.entries.sortedBy { it.value }.take(learned.size - MAX_LEARNED)
                .forEach { learned.remove(it.key) }
        }
    }

    fun clear() = learned.clear()

    /**
     * Mots appris que la liste de base ne connaît pas encore, les plus
     * utilisés d'abord : ceux que l'utilisateur peut proposer au projet.
     */
    fun newWords(): List<String> =
        learned.entries
            .filter { it.key !in seed }
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key }

    /**
     * Jusqu'à [limit] mots qui commencent comme [prefix], accents ignorés.
     * Ordre : début identique à la lettre près, puis mots appris (fréquence),
     * puis liste de base (poids). La casse suit celle de [prefix].
     */
    fun suggest(prefix: String, limit: Int = 3): List<String> {
        if (prefix.isEmpty()) return emptyList()
        val p = lower(prefix)
        val f = Fold.fold(prefix)
        val words = (seed.keys + learned.keys).toSet()
        return words
            .filter { it != p && Fold.fold(it).startsWith(f) }
            .sortedWith(
                compareByDescending<String> { it.startsWith(p) }
                    .thenByDescending { (learned[it] ?: 0) * 100 + (seed[it] ?: 0) }
                    .thenBy { it.length }
                    .thenBy { it },
            )
            .take(limit)
            .map { matchCase(it, prefix) }
    }

    /** « Nox » → « Noxçiyŋ », « NOX » → « NOXÇIYŊ ». */
    private fun matchCase(word: String, prefix: String): String = when {
        prefix.length > 1 && prefix == prefix.uppercase(Locale.ROOT) &&
            prefix != prefix.lowercase(Locale.ROOT) -> word.uppercase(Locale.ROOT)
        prefix.first().isUpperCase() -> Alphabet.upper(word.take(1)) + word.drop(1)
        else -> word
    }

    // ─── Sauvegarde (fichier privé de l'app, une ligne « mot<TAB>compte ») ───
    fun serialize(): String =
        learned.entries.joinToString("\n") { "${it.key}\t${it.value}" }

    fun load(text: String) {
        learned.clear()
        for (line in text.lineSequence()) {
            val parts = line.split('\t')
            val n = parts.getOrNull(1)?.toIntOrNull() ?: continue
            if (isWord(parts[0])) learned[parts[0]] = n
        }
    }

    companion object {
        const val MAX_LEARNED = 5000
        const val MAX_LENGTH = 40

        /** Minuscules, sauf le palotchka : Ӏ (U+04C0) ne s'écrit jamais ӏ en tchétchène. */
        fun lower(s: String): String = s.lowercase(Locale.ROOT).replace('ӏ', 'Ӏ')

        /** Liste de base : « mot<TAB>poids » par ligne, # pour un commentaire. */
        fun parseSeed(text: String): Map<String, Int> =
            text.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .mapNotNull { line ->
                    val parts = line.split('\t')
                    val w = lower(parts[0].trim())
                    if (w.isEmpty()) null else w to (parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 1)
                }
                .toMap()

        /** Le mot en cours de frappe : les lettres juste avant le curseur. */
        fun currentWord(before: CharSequence): String {
            var i = before.length
            while (i > 0) {
                val c = before[i - 1]
                if (c.isLetter() || Character.getType(c) == Character.NON_SPACING_MARK.toInt()) i--
                else break
            }
            return before.substring(i)
        }
    }
}
