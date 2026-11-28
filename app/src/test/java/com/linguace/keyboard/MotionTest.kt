package com.linguace.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionTest {

    @Test
    fun progress_isOneWithoutAStart_andClamped() {
        assertEquals(1f, Motion.progress(1000, 0, 100), 0f)
        assertEquals(0f, Motion.progress(1000, 1000, 100), 0f)
        assertEquals(0.5f, Motion.progress(1050, 1000, 100), 0.001f)
        assertEquals(1f, Motion.progress(5000, 1000, 100), 0f)
    }

    @Test
    fun progress_honoursTheDelay() {
        assertEquals(0f, Motion.progress(1020, 1000, 100, delay = 30), 0f)
        assertEquals(0.5f, Motion.progress(1080, 1000, 100, delay = 30), 0.001f)
    }

    @Test
    fun easeOut_startsFast_endsAtOne() {
        assertEquals(0f, Motion.easeOut(0f), 0f)
        assertEquals(1f, Motion.easeOut(1f), 0f)
        // « slow out » : à mi-parcours, on a déjà fait plus de la moitié du chemin
        assertTrue(Motion.easeOut(0.5f) > 0.5f)
        // jamais en dehors de 0…1, même avec une valeur aberrante
        assertEquals(1f, Motion.easeOut(7f), 0f)
        assertEquals(0f, Motion.easeOut(-3f), 0f)
    }

    @Test
    fun running_stopsOnceDone() {
        assertTrue(Motion.running(1050, 1000, 100))
        assertFalse(Motion.running(1100, 1000, 100))
        assertTrue(Motion.running(1150, 1000, 100, maxDelay = 90))
        assertFalse(Motion.running(1000, 0, 100))
    }

    @Test
    fun animationsAreOffWhenAndroidTurnsThemOff() {
        assertFalse(Motion.enabled(0f))
        assertTrue(Motion.enabled(1f))
        assertTrue(Motion.enabled(0.5f))
    }

    @Test
    fun theThreeAnimationsStayShort() {
        // Doherty : rien ne doit se sentir lent.
        assertTrue(Motion.POPUP_MS <= 120)
        assertTrue(Motion.PANEL_MS <= 200)
        assertTrue(Motion.STRIP_MS + 2 * Motion.STRIP_STAGGER_MS <= 200)
    }
}
