package com.agendamedica.app.ui.splash

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Story 4.1's splash, exercised for real (spec-7-4): fixed 1600ms, no tap-to-skip, remaining time after
 * recreation. Time is the Compose test clock (`mainClock`, the same one that drives `delay`), offset to a
 * realistic epoch value so a "0 means unset" mistake would show.
 */
@RunWith(RobolectricTestRunner::class)
class SplashScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private var finished = 0

    private val agora: () -> Long = { EPOCH + rule.mainClock.currentTime }

    // ignoreFrameDuration: without it advanceTimeBy rounds up to a 16ms frame, so "1599" would silently become 1600.
    private fun avancarAte(tempoAbsoluto: Long) =
        rule.mainClock.advanceTimeBy(tempoAbsoluto - rule.mainClock.currentTime, ignoreFrameDuration = true)

    @Test
    fun `shows the app name and does not finish before 1600ms`() {
        rule.mainClock.autoAdvance = false
        rule.setContent { SplashScreen(onFinished = { finished++ }, nowMillis = agora) }
        rule.onNodeWithText("Agenda Médica").assertIsDisplayed()
        avancarAte(1599)
        assertEquals(0, finished)
    }

    @Test
    fun `finishes exactly once, right at 1600ms`() {
        rule.mainClock.autoAdvance = false
        rule.setContent { SplashScreen(onFinished = { finished++ }, nowMillis = agora) }
        avancarAte(1599)
        assertEquals(0, finished)
        avancarAte(1600)
        assertEquals(1, finished)
        rule.mainClock.advanceTimeBy(3000)
        assertEquals(1, finished)
    }

    @Test
    fun `tapping the screen does not skip the splash`() {
        rule.mainClock.autoAdvance = false
        rule.setContent { SplashScreen(onFinished = { finished++ }, nowMillis = agora) }
        rule.onNodeWithText("Agenda Médica").performClick()
        avancarAte(800)
        rule.onNodeWithText("Agenda Médica").performClick()
        avancarAte(1599)
        assertEquals(0, finished)
        avancarAte(1600)
        assertEquals(1, finished)
    }

    @Test
    fun `after recreation it counts only the remaining time instead of restarting from zero`() {
        // Legacy junit4 StateRestorationTester (the v2 rule has no equivalent yet); it needs frames to run
        // to dispose and recompose the content, hence autoAdvance is on for that single call.
        val restoration = StateRestorationTester(rule)
        rule.mainClock.autoAdvance = false
        restoration.setContent { SplashScreen(onFinished = { finished++ }, nowMillis = agora) }
        avancarAte(1000)
        assertEquals(0, finished)
        rule.mainClock.autoAdvance = true
        restoration.emulateSavedInstanceStateRestore()
        rule.mainClock.autoAdvance = false
        assertEquals(0, finished)
        // The start mark was saved at 0, so the splash still ends at 1600 (not 1600ms after the recreation).
        avancarAte(1599)
        assertEquals(0, finished)
        avancarAte(1600)
        assertEquals(1, finished)
        rule.mainClock.advanceTimeBy(3000)
        assertEquals(1, finished)
    }

    private companion object {
        const val EPOCH = 1_790_000_000_000L
    }
}
