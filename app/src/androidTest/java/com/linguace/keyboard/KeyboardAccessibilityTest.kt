package com.linguace.keyboard

import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Ce que TalkBack voit du clavier : une touche = un nœud nommé, cliquable,
 * avec les lettres de l'appui long en actions. S'exécute sur l'émulateur.
 */
@RunWith(AndroidJUnit4::class)
class KeyboardAccessibilityTest {

    private val typed = mutableListOf<String>()
    private lateinit var view: KeyboardView

    private val listener = object : KeyboardView.Listener {
        override fun onText(text: String) { typed += text }
        override fun onDelete() { typed += "⌫" }
        override fun onEnter() { typed += "↵" }
        override fun onShift() { typed += "⇧" }
        override fun onGlobe() { typed += "🌐" }
        override fun onGlobeLongPress() {}
        override fun onSymbols() { typed += "123" }
        override fun onLetters() {}
        override fun onSuggestion(word: String) { typed += "[$word]" }
        override fun onEmojiPanel() { typed += "😊" }
        override fun onEmojiPage(page: String) { typed += "page:$page" }
        override fun onEmojiPick(emoji: String) { typed += emoji }
        override fun onSettings() { typed += "⚙" }
        override fun onUndo() { typed += "↶" }
        override fun onPaste() { typed += "📋" }
    }

    private fun onMain(block: () -> Unit) =
        InstrumentationRegistry.getInstrumentation().runOnMainSync(block)

    @Before
    fun setUp() = onMain {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        view = KeyboardView(ctx, listener)
        view.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }

    private val provider: AccessibilityNodeProvider get() = view.accessibilityNodeProvider

    /** Nœuds des touches, par nom lu. */
    private fun nodes(): Map<String, Pair<Int, AccessibilityNodeInfo>> {
        val out = mutableMapOf<String, Pair<Int, AccessibilityNodeInfo>>()
        onMain {
            val host = provider.createAccessibilityNodeInfo(AccessibilityNodeProvider.HOST_VIEW_ID)!!
            for (i in 0 until host.childCount) {
                val n = provider.createAccessibilityNodeInfo(i)!!
                out[n.contentDescription.toString()] = i to n
            }
        }
        return out
    }

    private fun desc(id: Int) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test
    fun everyLatinKey_isANamedClickableNode() {
        val n = nodes()
        assertEquals(Layouts.LATIN.rows.sumOf { it.size }, n.size)
        for (name in listOf("c", "ç", "w", "x", "ə", desc(R.string.key_delete),
            desc(R.string.key_shift), desc(R.string.key_globe), desc(R.string.key_space))) {
            val node = n[name]
            assertNotNull("touche « $name » absente", node)
            assertTrue(node!!.second.isClickable)
            assertTrue(node.second.boundsInScreenNonEmpty())
        }
    }

    @Test
    fun emojiPanel_isReadable_andKeysAreTyped() {
        onMain { view.layout = Emoji.layout("smileys", Emoji.category("smileys")!!.emojis) }
        val n = nodes()
        // Seule la partie visible de la grille est exposée (3 rangées + onglets).
        val visible = Layout.VISIBLE_SCROLL_ROWS * Emoji.COLUMNS + Emoji.TABS.size + 2
        assertEquals(visible, n.size)
        val (id, _) = n["😀"]!!
        onMain { provider.performAction(id, AccessibilityNodeInfo.ACTION_CLICK, null) }
        assertEquals(listOf("😀"), typed)
        // Les onglets ont un nom lisible dans la langue du téléphone.
        val food = n[desc(R.string.emoji_food)]!!.first
        onMain { provider.performAction(food, AccessibilityNodeInfo.ACTION_CLICK, null) }
        assertEquals("page:food", typed.last())
    }

    @Test
    fun toolbar_showsFourNamedButtons_inTheStrip() {
        onMain { view.showStrip = true; view.showToolbar = true }
        val n = nodes()
        assertEquals(Layouts.LATIN.rows.sumOf { it.size } + 4, n.size)
        for ((name, expected) in listOf(
            desc(R.string.tool_settings) to "⚙",
            desc(R.string.key_emoji) to "😊",
            desc(R.string.tool_undo) to "↶",
            desc(R.string.tool_paste) to "📋",
        )) {
            val (id, _) = n[name]!!
            onMain { provider.performAction(id, AccessibilityNodeInfo.ACTION_CLICK, null) }
            assertEquals(expected, typed.last())
        }
        // Un mot proposé prend la place de la barre d'outils.
        onMain { view.suggestions = listOf("salam") }
        assertEquals(null, nodes()[desc(R.string.tool_undo)])
        assertTrue(nodes().keys.any { it.contains("salam") })
    }

    @Test
    fun symbols_haveAnEmojiKey() {
        onMain { view.layout = Layouts.SYMBOLS }
        val key = nodes()[desc(R.string.key_emoji)]
        assertNotNull(key)
        onMain { provider.performAction(key!!.first, AccessibilityNodeInfo.ACTION_CLICK, null) }
        assertEquals(listOf("😊"), typed)
    }

    @Test
    fun doubleTap_typesTheKey() {
        val (id, _) = nodes()["x"]!!
        onMain { provider.performAction(id, AccessibilityNodeInfo.ACTION_CLICK, null) }
        assertEquals(listOf("x"), typed)
        // ẋ : action TalkBack de la touche x.
        onMain { provider.performAction(id, R.id.a11y_alt_0, null) }
        assertEquals(listOf("x", "ẋ"), typed)
    }

    @Test
    fun longPressLetters_areTalkBackActions() {
        val (id, node) = nodes()["c"]!!
        val labels = node.actionList.mapNotNull { it.label?.toString() }
        val typeLetter = desc(R.string.key_type_letter)
        assertEquals(listOf(typeLetter.replace("%1\$s", "ċ")), labels)
        onMain { provider.performAction(id, R.id.a11y_alt_0, null) }
        assertEquals(listOf("ċ"), typed)

        // ç a sa touche ; ç̇ en action TalkBack.
        val (cedilla, _) = nodes()["ç"]!!
        onMain { provider.performAction(cedilla, R.id.a11y_alt_0, null) }
        assertEquals(listOf("ċ", "ç̇"), typed)
    }

    @Test
    fun cyrillic_palochkaHasAName() {
        onMain { view.layout = Layouts.CYRILLIC }
        val n = nodes()
        assertNotNull(n[desc(R.string.key_palochka)])
    }

    @Test
    fun shift_isAnnouncedAndCapitalisesNames() {
        onMain { view.shift = ShiftState.LOCKED }
        val n = nodes()
        assertNotNull(n[desc(R.string.key_shift_locked)])
        assertNotNull(n["X"])
        assertNotNull(n["Q"])
    }

    @Test
    fun suggestions_areNamedNodes_andTapping_sendsTheWord() {
        onMain { view.suggestions = listOf("noxçiyŋ", "ç̇ara") }
        val n = nodes()
        val label = desc(R.string.key_suggestion).replace("%1\$s", "noxçiyŋ")
        val (id, node) = n[label]!!
        assertTrue(node.isClickable)
        onMain { provider.performAction(id, AccessibilityNodeInfo.ACTION_CLICK, null) }
        assertEquals(listOf("[noxçiyŋ]"), typed)
    }

    @Test
    fun noStrip_inPasswordFields() {
        onMain { view.showStrip = false; view.suggestions = listOf("noxçiyŋ") }
        val label = desc(R.string.key_suggestion).replace("%1\$s", "noxçiyŋ")
        assertTrue(nodes()[label] == null)
    }

    // ─── Frappe rapide (plusieurs doigts) ───────────────────────
    private fun center(name: String): Pair<Float, Float> {
        val (_, node) = nodes()[name]!!
        val r = android.graphics.Rect()
        @Suppress("DEPRECATION")
        node.getBoundsInParent(r)
        return r.exactCenterX() to r.exactCenterY()
    }

    private fun event(action: Int, vararg pointers: Pair<Int, Pair<Float, Float>>): MotionEvent {
        val props = pointers.map { (id, _) ->
            MotionEvent.PointerProperties().apply { this.id = id; toolType = MotionEvent.TOOL_TYPE_FINGER }
        }.toTypedArray()
        val coords = pointers.map { (_, xy) ->
            MotionEvent.PointerCoords().apply { x = xy.first; y = xy.second; pressure = 1f; size = 1f }
        }.toTypedArray()
        return MotionEvent.obtain(0, 0, action, pointers.size, props, coords, 0, 0, 1f, 1f, 0, 0, 0, 0)
    }

    @Test
    fun secondFinger_typesTheFirstKeyAtOnce_noLetterLost() {
        val q = center("q")
        val w = center("w")
        onMain {
            view.onTouchEvent(event(MotionEvent.ACTION_DOWN, 0 to q))
            // Le pouce droit se pose sur w avant que le gauche ait quitté q.
            view.onTouchEvent(event(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 0 to q, 1 to w))
        }
        assertEquals("q part dès que le deuxième doigt se pose", listOf("q"), typed)
        onMain {
            view.onTouchEvent(event(MotionEvent.ACTION_POINTER_UP or (0 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 0 to q, 1 to w))
            view.onTouchEvent(event(MotionEvent.ACTION_UP, 1 to w))
        }
        assertEquals(listOf("q", "w"), typed)
    }

    @Test
    fun slidingToANeighbourKey_typesThatKey() {
        val q = center("q")
        val w = center("w")
        onMain {
            view.onTouchEvent(event(MotionEvent.ACTION_DOWN, 0 to q))
            view.onTouchEvent(event(MotionEvent.ACTION_MOVE, 0 to w))
            view.onTouchEvent(event(MotionEvent.ACTION_UP, 0 to w))
        }
        assertEquals(listOf("w"), typed)
    }

    private fun AccessibilityNodeInfo.boundsInScreenNonEmpty(): Boolean {
        val r = android.graphics.Rect()
        getBoundsInScreen(r)
        return !r.isEmpty
    }
}
