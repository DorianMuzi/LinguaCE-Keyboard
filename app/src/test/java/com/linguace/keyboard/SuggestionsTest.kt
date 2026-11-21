package com.linguace.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionsTest {

    private val seed = WordStore.parseSeed(
        """
        # mot<TAB>poids
        noxçiyŋ	50
        barkalla	40
        baṙkalla	1
        ç̇ara	10
        ẋaŋ	30
        нохчийн	50
        цӀе	20
        """.trimIndent(),
    )

    @Test
    fun fold_ignoresChechenDiacritics() {
        assertEquals("noxciyn", Fold.fold("Noxçiyŋ"))
        assertEquals("cara", Fold.fold("Ç̇ara"))
        assertEquals("xan", Fold.fold("ẋaŋ"))
        assertEquals("zeo", Fold.fold("ƶəö"))
        assertEquals("цӏе", Fold.fold("цӀе"))
    }

    @Test
    fun suggest_withoutDiacritics_givesTheRightSpelling() {
        val s = WordStore(seed)
        assertEquals(listOf("noxçiyŋ"), s.suggest("noxc"))
        assertEquals(listOf("ç̇ara"), s.suggest("car"))
        assertEquals("ẋaŋ", s.suggest("xa").first())
        assertEquals(listOf("цӀе"), s.suggest("ц"))
    }

    @Test
    fun suggest_followsTheCaseOfWhatIsTyped() {
        val s = WordStore(seed)
        assertEquals("Noxçiyŋ", s.suggest("Nox").first())
        assertEquals("NOXÇIYŊ", s.suggest("NOX").first())
        assertEquals("Ç̇ara", s.suggest("Car").first())
    }

    @Test
    fun learnedWords_comeFirst_andNeverTheWordAlreadyTyped() {
        val s = WordStore(seed)
        s.learn("baq̇o")
        s.learn("baq̇o")
        assertEquals("baq̇o", s.suggest("ba").first())
        assertFalse("barkalla" in s.suggest("barkalla"))
        assertTrue(s.suggest("").isEmpty())
    }

    @Test
    fun learn_rejectsNonWords() {
        val s = WordStore()
        s.learn("a")
        s.learn("123")
        s.learn("ab12")
        s.learn("x@y")
        assertEquals(0, s.learnedCount)
        s.learn("Ç̇ara")
        assertEquals(1, s.learnedCount)
    }

    @Test
    fun learnedWords_surviveSaveAndLoad_andCanBeErased() {
        val a = WordStore()
        a.learn("ç̇ara")
        a.learn("ç̇ara")
        a.learn("ẋaŋ")
        val b = WordStore()
        b.load(a.serialize())
        assertEquals(2, b.learnedCount)
        assertEquals("ç̇ara", b.suggest("c").first())
        b.clear()
        assertEquals(0, b.learnedCount)
    }

    @Test
    fun newWords_areLearnedWordsNotInTheSeed_mostUsedFirst() {
        val s = WordStore(seed)
        s.learn("barkalla") // déjà dans la liste de base : pas à proposer
        s.learn("zarg")
        s.learn("baq̇o")
        s.learn("baq̇o")
        assertEquals(listOf("baq̇o", "zarg"), s.newWords())
    }

    @Test
    fun currentWord_isTheLettersBeforeTheCursor() {
        assertEquals("Naxç̇", WordStore.currentWord("Salam, Naxç̇"))
        assertEquals("", WordStore.currentWord("Salam "))
        assertEquals("цӀ", WordStore.currentWord("Хьан цӀ"))
        assertEquals("", WordStore.currentWord(""))
    }
}
