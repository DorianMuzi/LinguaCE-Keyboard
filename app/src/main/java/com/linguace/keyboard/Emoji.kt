package com.linguace.keyboard

/**
 * Le panneau des emoji : une grille qui défile et des onglets de catégories.
 * Aucune donnée ne quitte le téléphone ; les emoji récents sont gardés
 * localement (SharedPreferences), comme les mots appris.
 *
 * Les emoji viennent de la police du téléphone : le service écarte ceux que
 * sa police ne sait pas dessiner (téléphone ancien) avec [Layout] filtré.
 */
object Emoji {
    const val COLUMNS = 9
    const val RECENT = "recent"
    const val MAX_RECENT = 27
    const val PREFIX = "emoji:"

    class Category(val id: String, val icon: String, val emojis: List<String>)

    private fun list(s: String): List<String> = s.trim().split(Regex("\\s+"))

    val CATEGORIES: List<Category> = listOf(
        Category(
            "smileys", "😀",
            list(
                """
                😀 😃 😄 😁 😆 😅 😂 🤣 ☺️ 😊 😇 🙂 🙃 😉 😌 😍 😘 😗 😙 😚 😋 😛 😝 😜 🤪 🤨 🧐 🤓 😎 🤩
                😏 😒 😞 😔 😟 😕 🙁 ☹️ 😣 😖 😫 😩 😢 😭 😤 😠 😡 🤬 🤯 😳 😱 😨 😰 😥 😓 🤗 🤔 🤭 🤫 🤥
                😶 😐 😑 😬 🙄 😯 😦 😧 😮 😲 😴 🤤 😪 😵 🤐 🤢 🤮 🤧 😷 🤒 🤕 🤑 🤠 😈 👿 💀 👻 👽 🤖 💩
                👍 👎 👌 ✌️ 🤞 🤟 🤘 👈 👉 👆 👇 ☝️ ✋ 🤚 🖖 👋 🤙 💪 🙏 🤝 👏 🙌 👐 🤲 ✍️ 💅 👀 👅 👄
                👶 👦 👧 👨 👩 👴 👵 🧑 👮 🕵️ 💂 👷 🤴 👸 🎅 🤶 🧙 🧚 🧛 🧜 🧝 🙋 🤦 🤷 💃 🕺 🚶 🏃 👪
                """,
            ),
        ),
        Category(
            "nature", "🐱",
            list(
                """
                🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🙈 🙉 🙊 🐒 🐔 🐧 🐦 🐤 🦆 🦅 🦉 🦇 🐺 🐗 🐴
                🦄 🐝 🐛 🦋 🐌 🐞 🐜 🕷️ 🦂 🐢 🐍 🦎 🐙 🦑 🦐 🦀 🐡 🐠 🐟 🐬 🐳 🐋 🦈 🐊 🐅 🐆 🦓 🦍 🐘 🦏
                🐪 🐫 🦒 🐃 🐂 🐄 🐎 🐖 🐏 🐑 🐐 🦌 🐕 🐩 🐈 🐓 🦃 🕊️ 🐇 🐁 🐀 🐿️ 🦔 🐾 🐉 🌵 🎄 🌲 🌳 🌴
                🌱 🌿 ☘️ 🍀 🍁 🍂 🍃 🌺 🌻 🌹 🥀 🌷 🌼 🌸 💐 🍄 🌰 🌍 🌎 🌏 🌙 🌞 🌝 ⭐ 🌟 ✨ ⚡ 🔥 🌈 ☀️
                ⛅ ☁️ 🌧️ ⛈️ ❄️ ☃️ 💧 🌊 ⛰️ 🏔️ 🌋 🏕️
                """,
            ),
        ),
        Category(
            "food", "🍔",
            list(
                """
                🍏 🍎 🍐 🍊 🍋 🍌 🍉 🍇 🍓 🍈 🍒 🍑 🍍 🥥 🥝 🍅 🍆 🥑 🥦 🥒 🌶️ 🌽 🥕 🥔 🍠 🥐 🍞 🥖 🧀 🥚
                🍳 🥞 🥓 🥩 🍗 🍖 🌭 🍔 🍟 🍕 🥪 🌮 🌯 🥙 🍝 🍜 🍲 🍛 🍣 🍱 🥟 🍤 🍙 🍚 🍘 🍥 🍢 🍡 🍧 🍨
                🍦 🥧 🍰 🎂 🍮 🍭 🍬 🍫 🍿 🍩 🍪 🥛 🍼 ☕ 🍵 🥤 🍶 🍺 🍻 🥂 🍷 🥃 🍸 🍹 🍾 🥄 🍴 🍽️ 🍯 🥜
                """,
            ),
        ),
        Category(
            "activities", "⚽",
            list(
                """
                ⚽ 🏀 🏈 ⚾ 🎾 🏐 🏉 🎱 🏓 🏸 🥅 🏒 🏑 🏏 ⛳ 🏹 🎣 🥊 🥋 ⛸️ 🎿 🛷 🏂 🏇 🏊 🚴 🤸 🤼 🤺 🏆
                🥇 🥈 🥉 🏅 🎖️ 🎪 🎭 🎨 🎬 🎤 🎧 🎼 🎹 🥁 🎷 🎺 🎸 🎻 🎲 🎯 🎳 🎮 🎰 🎁 🎈 🎉 🎊 🎓 📚 📖
                🚗 🚕 🚙 🚌 🚎 🏎️ 🚓 🚑 🚒 🚐 🚚 🚛 🚜 🛵 🏍️ 🚲 🛴 🚨 🚔 🚍 🚘 🚖 🚃 🚄 🚅 🚂 🚇 ✈️ 🛫 🛬
                🚁 🚀 🛸 ⛵ 🚤 🚢 ⚓ ⛽ 🚦 🗺️ 🗿 🗽 🗼 🏰 🏯 🏟️ 🎡 🎢 🎠 ⛲ 🏖️ 🏝️ ⛺ 🏠 🏡 🏢 🏥 🏦 🏪 🏫
                🕌 ⛪ 🕍 🕋 ⌚ 📱 💻 ⌨️ 🖥️ 📷 🎥 📺 📻 ⏰ ⌛ 💡 🔦 🕯️ 💰 💳 💎 🔧 🔨 🛠️ ⚙️ 🔑 🚪 🛏️ 📦 📝 ✂️
                """,
            ),
        ),
        Category(
            "symbols", "❤️",
            list(
                """
                ❤️ 🧡 💛 💚 💙 💜 🖤 💔 ❣️ 💕 💞 💓 💗 💖 💘 💝 💟 ☮️ ✝️ ☪️ 🕉️ ☸️ ✡️ ☯️ ⚫ ⚪ ✅ ❌ ❎ ⭕
                ❗ ❓ ❕ ❔ ‼️ ⁉️ 💯 🔔 🔕 🔊 🔇 📢 💬 💭 ♠️ ♣️ ♥️ ♦️ 🃏 ➕ ➖ ➗ ✖️ ♻️ ⚠️ 🚫 🔞 📵 🚭 ✔️ ☑️
                ⬆️ ⬇️ ⬅️ ➡️ ↗️ ↘️ ↙️ ↖️ ↕️ ↔️ 🔄 🔁 🔀 ▶️ ⏸️ ⏹️ ⏩ ⏪ 🔼 🔽 🆗 🆕 🆒 🆓 🆙 🔝 ™️ ©️ ®️ 🏁 🚩
                """,
            ),
        ),
    )

    /** Onglets : les emoji récents d'abord, puis les catégories. */
    val TABS: List<Pair<String, String>> =
        listOf(RECENT to "🕘") + CATEGORIES.map { it.id to it.icon }

    fun category(id: String): Category? = CATEGORIES.firstOrNull { it.id == id }

    /** Emoji courants proposés tant que l'on n'en a pas assez tapé soi-même. */
    val DEFAULT_RECENT: List<String> = list(
        "😀 😂 🤣 😊 😍 😘 😉 😎 🤔 😭 😢 😡 👍 👎 🙏 👏 💪 ❤️ 🔥 ✨ 🎉 💯 ✅ 😅 🥰 🤝 😴",
    )

    /**
     * Contenu de l'onglet 🕘 : les emoji récents d'abord, puis des emoji
     * courants pour compléter (la page n'est jamais vide).
     */
    fun withDefaults(recent: List<String>): List<String> =
        (recent + DEFAULT_RECENT.filter { it !in recent }).take(MAX_RECENT)

    /** Page affichée à l'ouverture : les récents s'il y en a, sinon les smileys. */
    fun firstPage(recent: List<String>) = if (recent.isEmpty()) CATEGORIES.first().id else RECENT

    /**
     * Le panneau pour la page [page] : [emojis] sur [COLUMNS] colonnes (la
     * dernière rangée est complétée par des cases vides pour rester alignée
     * à gauche), puis la rangée d'onglets « abc … ⌫ ».
     */
    fun layout(page: String, emojis: List<String>): Layout {
        val blank = { Key("", KeyType.PICTO, caseless = true) }
        val chunks = emojis.chunked(COLUMNS).map { chunk ->
            chunk.map { Key(it, KeyType.PICTO, caseless = true) } + List(COLUMNS - chunk.size) { blank() }
        }
        // Au moins 3 rangées (récents presque vides) : la hauteur ne change pas.
        val grid = chunks + List((Layout.VISIBLE_SCROLL_ROWS - chunks.size).coerceAtLeast(0)) {
            List(COLUMNS) { blank() }
        }
        val tabs = TABS.map { (id, icon) -> Key(icon, KeyType.PAGE, 1f, target = id) }
        val nav = listOf(Key("abc", KeyType.LETTERS, 1.5f)) + tabs +
            Key("⌫", KeyType.DELETE, COLUMNS - 1.5f - tabs.size)
        return Layout(PREFIX + page, grid + listOf(nav), scrollRows = grid.size)
    }

    /** « emoji:food » → « food » ; null si ce n'est pas le panneau emoji. */
    fun pageOf(layoutId: String): String? =
        if (layoutId.startsWith(PREFIX)) layoutId.removePrefix(PREFIX) else null

    // ─── Emoji récents ──────────────────────────────────────────
    /** [emoji] passe en tête ; pas de doublon ; [MAX_RECENT] au plus. */
    fun pushRecent(recent: List<String>, emoji: String): List<String> =
        (listOf(emoji) + recent.filter { it != emoji }).take(MAX_RECENT)

    fun serialize(recent: List<String>) = recent.joinToString("\t")

    fun parse(s: String?): List<String> =
        s.orEmpty().split('\t').filter { it.isNotBlank() }.take(MAX_RECENT)
}
