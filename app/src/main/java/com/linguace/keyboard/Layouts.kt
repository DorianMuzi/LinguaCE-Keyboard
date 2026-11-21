package com.linguace.keyboard

import com.linguace.keyboard.Alphabet.DOT

/** SUGGESTION : un mot proposé dans la bande au-dessus des touches. */
enum class KeyType { CHAR, SHIFT, DELETE, ENTER, SPACE, GLOBE, SYMBOLS, LETTERS, SUGGESTION }

/**
 * Une touche. [width] en unités (1 = une touche de lettre). [popup] : lettres
 * proposées par appui long. [caseless] : jamais mise en majuscule (Ӏ).
 */
data class Key(
    val label: String,
    val type: KeyType = KeyType.CHAR,
    val width: Float = 1f,
    val popup: List<String> = emptyList(),
    val caseless: Boolean = false,
) {
    /** Texte émis, selon l'état de la touche ⇧. */
    fun output(shifted: Boolean): String =
        if (shifted && !caseless) Alphabet.upper(label) else label
}

data class Layout(val id: String, val rows: List<List<Key>>) {
    /** Largeur de la rangée la plus large, en unités. */
    val units: Float get() = rows.maxOf { row -> row.sumOf { it.width.toDouble() }.toFloat() }

    /** Tout ce qu'on peut taper avec cette disposition (touches + appuis longs). */
    val typeable: Set<String>
        get() = rows.flatten().filter { it.type == KeyType.CHAR }
            .flatMap { listOf(it.label) + it.popup }.toSet()
}

object Layouts {
    private fun k(label: String, vararg popup: String) = Key(label, popup = popup.toList())
    private fun row(s: String) = s.split(" ").map { Key(it) }

    /**
     * Latin tchétchène (choix de l'auteur, 2026-11-21) : QWERTY, w remplacé
     * par ə (w n'existe pas dans l'alphabet tchétchène ; il reste accessible par appui long
     * sur ə pour les mots étrangers), plus une rangée des 10 lettres les plus
     * fréquentes. Les autres par appui long sur leur lettre de base.
     */
    val LATIN = Layout(
        "latin",
        listOf(
            row("ä ç ç$DOT ġ ŋ q$DOT ş ṫ ẋ ƶ"),
            listOf(
                // w (hors alphabet) par appui long sur ə, à sa place QWERTY :
                // mots d'origine étrangère (WhatsApp, Wi-Fi, Wagner…).
                k("q", "q$DOT"), k("ə", "w"), k("e", "ə"), k("r"), k("t", "ṫ"),
                k("y"), k("u", "ü"), k("i"), k("o", "ö"), k("p", "ṗ"),
            ),
            listOf(
                k("a", "ä"), k("s", "ş"), k("d"), k("f"), k("g", "ġ"),
                k("h"), k("j"), k("k", "k$DOT"), k("l"),
            ),
            listOf(Key("⇧", KeyType.SHIFT, 1.5f)) +
                listOf(
                    k("z", "ƶ"), k("x", "ẋ"), k("c", "ċ", "ç", "ç$DOT"), k("v"),
                    k("b"), k("n", "ŋ"), k("m"),
                ) +
                Key("⌫", KeyType.DELETE, 1.5f),
            bottom(Key("123", KeyType.SYMBOLS, 1.25f)),
        ),
    )

    /**
     * Cyrillique tchétchène : ЙЦУКЕН, avec la touche Ӏ (palochka, U+04C0,
     * toujours en forme majuscule comme le veut l'usage tchétchène). ъ par
     * appui long sur ь, ё sur е.
     */
    val CYRILLIC = Layout(
        "cyrillic",
        listOf(
            listOf(
                k("й"), k("ц"), k("у"), k("к"), k("е", "ё"), k("н"), k("г"),
                k("ш"), k("щ"), k("з"), k("х"),
            ),
            row("ф ы в а п р о л д ж э"),
            listOf(Key("⇧", KeyType.SHIFT, 1f)) +
                listOf(k("я"), k("ч"), k("с"), k("м"), k("и"), k("т"), k("ь", "ъ"), k("б"), k("ю")) +
                Key("⌫", KeyType.DELETE, 1f),
            listOf(
                Key("🌐", KeyType.GLOBE, 1.25f), Key("123", KeyType.SYMBOLS, 1.25f),
                Key(","), Key("Ӏ", caseless = true), Key(" ", KeyType.SPACE, 3.75f),
                Key("."), Key("↵", KeyType.ENTER, 1.75f),
            ),
        ),
    )

    val SYMBOLS = Layout(
        "symbols",
        listOf(
            row("1 2 3 4 5 6 7 8 9 0"),
            row("@ # % & - + ( ) / ="),
            row("* \" ' : ; ! ? « »") + Key("⌫", KeyType.DELETE, 1f),
            listOf(
                Key("abc", KeyType.LETTERS, 1.5f), Key("🌐", KeyType.GLOBE, 1.25f),
                Key(","), Key(" ", KeyType.SPACE, 3.75f), Key("."),
                Key("↵", KeyType.ENTER, 1.5f),
            ),
        ),
    )

    private fun bottom(switch: Key) = listOf(
        Key("🌐", KeyType.GLOBE, 1.25f), switch, Key(","),
        Key(" ", KeyType.SPACE, 4f), Key("."), Key("↵", KeyType.ENTER, 1.5f),
    )

    fun byId(id: String?): Layout = if (id == CYRILLIC.id) CYRILLIC else LATIN
}
