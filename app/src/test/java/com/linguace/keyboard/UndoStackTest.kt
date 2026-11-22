package com.linguace.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UndoStackTest {

    @Test
    fun lettersOfOneWord_formOneEntry() {
        val u = UndoStack()
        for (c in listOf("s", "a", "l", "a", "m")) u.record("", c)
        assertEquals(UndoStack.Edit("", "salam"), u.pop())
        assertTrue(u.isEmpty)
    }

    @Test
    fun aSpace_startsANewEntry() {
        val u = UndoStack()
        for (c in listOf("d", "a", " ", "n", "a")) u.record("", c)
        assertEquals(UndoStack.Edit("", "na"), u.pop())
        assertEquals(UndoStack.Edit("", " "), u.pop())
        assertEquals(UndoStack.Edit("", "da"), u.pop())
        assertNull(u.pop())
    }

    @Test
    fun letterWithCombiningDot_staysInTheWord() {
        val u = UndoStack()
        u.record("", "k")
        u.record("", "ç̇")
        assertEquals(UndoStack.Edit("", "kç̇"), u.pop())
    }

    @Test
    fun consecutiveDeletions_areRestoredInOrder() {
        val u = UndoStack()
        // « salam » effacé de la droite vers la gauche : m, a, l.
        u.record("m", "")
        u.record("a", "")
        u.record("l", "")
        assertEquals(UndoStack.Edit("lam", ""), u.pop())
    }

    @Test
    fun typingAfterADeletion_isAnotherEntry() {
        val u = UndoStack()
        u.record("m", "")
        u.record("", "n")
        assertEquals(UndoStack.Edit("", "n"), u.pop())
        assertEquals(UndoStack.Edit("m", ""), u.pop())
    }

    @Test
    fun replacements_areKeptWhole() {
        val u = UndoStack()
        u.record("noxciyn", "noxçiyŋ ")       // suggestion choisie
        u.record(" ", ". ")                   // double espace
        assertEquals(UndoStack.Edit(" ", ". "), u.pop())
        assertEquals(UndoStack.Edit("noxciyn", "noxçiyŋ "), u.pop())
    }

    @Test
    fun emojiAndPaste_areEntriesOfTheirOwn() {
        val u = UndoStack()
        u.record("", "😀")
        u.record("", "salam")
        assertEquals(UndoStack.Edit("", "salam"), u.pop())
        assertEquals(UndoStack.Edit("", "😀"), u.pop())
    }

    @Test
    fun historyIsBounded_andClearable() {
        val u = UndoStack(max = 5)
        for (i in 0 until 20) u.record("", "😀") // chaque emoji : une entrée
        var n = 0
        while (u.pop() != null) n++
        assertEquals(5, n)
        u.record("", "x")
        u.clear()
        assertTrue(u.isEmpty)
    }

    @Test
    fun emptyEdit_isIgnored() {
        val u = UndoStack()
        u.record("", "")
        assertTrue(u.isEmpty)
    }
}
