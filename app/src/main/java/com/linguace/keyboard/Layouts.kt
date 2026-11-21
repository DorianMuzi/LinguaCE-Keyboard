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

    /** Chiffre de l'appui long (rangée du haut), affiché en haut à gauche. */
    val digit: String? get() = popup.firstOrNull { it.length == 1 && it[0].isDigit() }

    /** Lettre dérivée de l'appui long, affichée en haut à droite. */
    val variant: String? get() = popup.firstOrNull { !(it.length == 1 && it[0].isDigit()) }
}

data class Layout(val id: String, val rows: List<List<Key>>) {
    /** Disposition latine (QWERTY ou AZERTY). */
    val isLatin: Boolean get() = id.startsWith("latin")

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
     * Latin tchétchène (choix de l'auteur, 2026-11-21) : QWERTY de 4 rangées,
     * avec ə et ç sur leurs propres touches ; les autres lettres à diacritique
     * sont sur l'appui long de leur lettre de base (x → ẋ, s → ş…).
     */
    val LATIN = Layout(
        "latin",
        listOf(
            // Plus de rangée de lettres tchétchènes (décision de l'auteur,
            // 2026-11-21) : chaque variante est sur l'appui long de sa lettre
            // de base, ce qui garde un clavier de 4 rangées comme le cyrillique.
            // w à sa place QWERTY (mots étrangers : WhatsApp, Wi-Fi, Wagner…).
            withDigits(
                listOf(
                    k("q", "q$DOT"), k("w"), k("e"), k("r"), k("t", "ṫ"),
                    k("y"), k("u", "ü"), k("i"), k("o", "ö"), k("p", "ṗ"),
                ),
            ),
            // ə a sa touche, à droite de l, comme sur le clavier azerbaïdjanais.
            listOf(
                k("a", "ä"), k("s", "ş"), k("d"), k("f"), k("g", "ġ"),
                k("h"), k("j"), k("k", "k$DOT"), k("l"), k("ə"),
            ),
            // ç a sa touche (ç̇ en appui long) ; c ne garde que ċ.
            shiftRow(
                10f,
                listOf(
                    k("z", "ƶ"), k("x", "ẋ"), k("c", "ċ"), k("v"),
                    k("b"), k("n", "ŋ"), k("m"), k("ç", "ç$DOT"),
                ),
            ),
            bottom(Key("123", KeyType.SYMBOLS, 1.25f)),
        ),
    )

    /**
     * Latin tchétchène en AZERTY (demande de l'auteur, 2026-11-22), sur le
     * modèle de Gboard français : beaucoup de Tchétchènes de France et de
     * Belgique ont l'habitude de l'AZERTY. ç et ə prennent la place de
     * l'apostrophe et des accents français, en fin de troisième rangée.
     */
    val LATIN_AZERTY = Layout(
        "latin_azerty",
        listOf(
            withDigits(
                listOf(
                    k("a", "ä"), k("z", "ƶ"), k("e"), k("r"), k("t", "ṫ"),
                    k("y"), k("u", "ü"), k("i"), k("o", "ö"), k("p", "ṗ"),
                ),
            ),
            listOf(
                k("q", "q$DOT"), k("s", "ş"), k("d"), k("f"), k("g", "ġ"),
                k("h"), k("j"), k("k", "k$DOT"), k("l"), k("m"),
            ),
            shiftRow(
                10f,
                listOf(
                    k("w"), k("x", "ẋ"), k("c", "ċ"), k("v"),
                    k("b"), k("n", "ŋ"), k("ç", "ç$DOT"), k("ə"),
                ),
            ),
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
            withDigits(
                listOf(
                    k("й"), k("ц"), k("у"), k("к"), k("е", "ё"), k("н"), k("г"),
                    k("ш"), k("щ"), k("з"), k("х"),
                ),
            ),
            row("ф ы в а п р о л д ж э"),
            shiftRow(
                11f,
                listOf(k("я"), k("ч"), k("с"), k("м"), k("и"), k("т"), k("ь", "ъ"), k("б"), k("ю")),
            ),
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

    /** Largeur de ⇧ et ⌫, en touches (Gboard : ≈ 1,5). */
    const val FUNCTION_KEY_WIDTH = 1.4f

    /**
     * Rangée « ⇧ lettres ⌫ » : ⇧ et ⌫ larges comme sur Gboard (demande de
     * l'auteur), les lettres se partagent le reste de la largeur [units].
     */
    private fun shiftRow(units: Float, letters: List<Key>): List<Key> {
        val w = (units - 2 * FUNCTION_KEY_WIDTH) / letters.size
        return listOf(Key("⇧", KeyType.SHIFT, FUNCTION_KEY_WIDTH)) +
            letters.map { it.copy(width = w) } +
            Key("⌫", KeyType.DELETE, FUNCTION_KEY_WIDTH)
    }

    /**
     * Chiffres par appui long sur la rangée du haut, comme Gboard : 1 à 0 sur
     * les dix premières touches, APRÈS la lettre dérivée éventuelle (t → ṫ, 5).
     */
    private fun withDigits(row: List<Key>): List<Key> =
        row.mapIndexed { i, key ->
            if (i < 10) key.copy(popup = key.popup + ((i + 1) % 10).toString()) else key
        }

    private fun bottom(switch: Key) = listOf(
        Key("🌐", KeyType.GLOBE, 1.25f), switch, Key(","),
        Key(" ", KeyType.SPACE, 4f), Key("."), Key("↵", KeyType.ENTER, 1.5f),
    )

    /** Disposition latine choisie dans les réglages. */
    fun latin(azerty: Boolean): Layout = if (azerty) LATIN_AZERTY else LATIN

    /** « cyrillic » → cyrillique ; sinon le latin choisi. */
    fun byId(id: String?, azerty: Boolean = false): Layout =
        if (id == CYRILLIC.id) CYRILLIC else latin(azerty)
}
