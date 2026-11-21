package com.linguace.keyboard

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

    private fun AccessibilityNodeInfo.boundsInScreenNonEmpty(): Boolean {
        val r = android.graphics.Rect()
        getBoundsInScreen(r)
        return !r.isEmpty
    }
}
