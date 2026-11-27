package com.linguace.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiTest {

    @Test
    fun everyCategory_hasEnoughEmoji_withoutDuplicates() {
        for (c in Emoji.CATEGORIES) {
            assertTrue("${c.id} trop courte", c.emojis.size >= 40)
            assertEquals("doublons dans ${c.id}", c.emojis.size, c.emojis.toSet().size)
            assertTrue("emoji vide dans ${c.id}", c.emojis.none { it.isBlank() })
        }
    }

    @Test
    fun layout_isAGridOfNineColumns_thenOneFixedTabRow() {
        val layout = Emoji.layout("food", Emoji.category("food")!!.emojis)
        val grid = layout.rows.take(layout.scrollRows)
        assertTrue(grid.all { it.size == Emoji.COLUMNS })
        val nav = layout.rows.last()
        assertEquals(KeyType.LETTERS, nav.first().type)
        assertEquals(KeyType.DELETE, nav.last().type)
        assertEquals(Emoji.TABS.map { it.first }, nav.filter { it.type == KeyType.PAGE }.map { it.target })
        // La rangée d'onglets a la largeur de la grille : rien ne déborde.
        assertEquals(Emoji.COLUMNS.toFloat(), nav.sumOf { it.width.toDouble() }.toFloat(), 0.001f)
        assertEquals(Emoji.COLUMNS.toFloat(), layout.units, 0.001f)
    }

    @Test
    fun layout_keepsItsHeight_evenWithNoOrFewRecents() {
        val full = Emoji.layout("food", Emoji.category("food")!!.emojis)
        val few = Emoji.layout(Emoji.RECENT, listOf("😀", "👍"))
        val none = Emoji.layout(Emoji.RECENT, emptyList())
        assertEquals(full.visibleRows, few.visibleRows)
        assertEquals(full.visibleRows, none.visibleRows)
        assertTrue(few.scrollRows >= Layout.VISIBLE_SCROLL_ROWS)
        // Cases vides : rien à taper.
        assertTrue(few.rows[0].drop(2).all { it.label.isEmpty() })
    }

    @Test
    fun recentsTab_isNeverEmpty_andKeepsRealRecentsFirst() {
        val none = Emoji.withDefaults(emptyList())
        assertEquals(Emoji.MAX_RECENT, none.size)
        assertEquals(none.toSet().size, none.size)
        val some = Emoji.withDefaults(listOf("🍏", "😂"))
        assertEquals(listOf("🍏", "😂"), some.take(2))
        assertEquals(1, some.count { it == "😂" })   // pas de doublon avec les emoji courants
        assertEquals(Emoji.MAX_RECENT, some.size)
    }

    @Test
    fun pageOf_readsTheLayoutId() {
        assertEquals("food", Emoji.pageOf(Emoji.layout("food", listOf("🍔")).id))
        assertNull(Emoji.pageOf(Layouts.LATIN.id))
    }

    @Test
    fun recents_comeFirst_noDuplicate_andAreCapped() {
        var r = emptyList<String>()
        r = Emoji.pushRecent(r, "😀")
        r = Emoji.pushRecent(r, "👍")
        r = Emoji.pushRecent(r, "😀")
        assertEquals(listOf("😀", "👍"), r)
        for (i in 0 until 40) r = Emoji.pushRecent(r, "x$i")
        assertEquals(Emoji.MAX_RECENT, r.size)
        assertEquals("x39", r.first())
    }

    @Test
    fun recents_surviveSerialization_andBadInputIsIgnored() {
        val r = listOf("😀", "❤️", "👍")
        assertEquals(r, Emoji.parse(Emoji.serialize(r)))
        assertTrue(Emoji.parse(null).isEmpty())
        assertTrue(Emoji.parse("").isEmpty())
        assertEquals(listOf("😀"), Emoji.parse("\t😀\t \t"))
    }

    @Test
    fun firstPage_isRecentsOnlyWhenThereAreSome() {
        assertEquals("smileys", Emoji.firstPage(emptyList()))
        assertEquals(Emoji.RECENT, Emoji.firstPage(listOf("😀")))
    }

    @Test
    fun entryPoints_exist_inLettersAndSymbols() {
        assertTrue(Layouts.SYMBOLS.rows.flatten().any { it.type == KeyType.EMOJI })
        for (l in listOf(Layouts.LATIN, Layouts.LATIN_AZERTY, Layouts.CYRILLIC)) {
            val comma = l.rows.flatten().first { it.label == "," }
            assertEquals(KeyType.EMOJI, comma.longAction)
        }
        assertFalse(Layouts.SYMBOLS.rows.last().sumOf { it.width.toDouble() } > Layouts.SYMBOLS.units + 0.001)
    }
}
