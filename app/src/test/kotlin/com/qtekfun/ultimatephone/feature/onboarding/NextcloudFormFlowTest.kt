package com.qtekfun.ultimatephone.feature.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NextcloudFormFlowTest {
    private fun atNextcloud() = (1 until OnboardingStep.NEXTCLOUD.ordinal + 1).fold(OnboardingState()) { s, _ -> OnboardingFlow.next(s) }

    @Test
    fun theFormOpensOnlyOnTheNextcloudStep() {
        val elsewhere = OnboardingState()
        assertEquals(elsewhere, OnboardingFlow.openNextcloudForm(elsewhere))
        val state = atNextcloud()
        assertEquals(OnboardingStep.NEXTCLOUD, state.step)
        assertTrue(OnboardingFlow.openNextcloudForm(state).nextcloudFormOpen)
    }

    @Test
    fun closingTheFormKeepsTheStepAndMarksNothing() {
        val open = OnboardingFlow.openNextcloudForm(atNextcloud())
        val closed = OnboardingFlow.closeNextcloudForm(open)
        assertFalse(closed.nextcloudFormOpen)
        assertEquals(open.step, closed.step)
        assertEquals(open.skipped, closed.skipped)
        assertEquals(open.completed, closed.completed)
    }

    @Test
    fun connectingContinuesAndCompletesTheStepWithTheFormClosed() {
        val after = OnboardingFlow.next(OnboardingFlow.openNextcloudForm(atNextcloud()))
        assertEquals(OnboardingStep.BATTERY, after.step)
        assertTrue(OnboardingStep.NEXTCLOUD in after.completed)
        assertFalse(after.nextcloudFormOpen)
    }

    @Test
    fun skippingOrGoingBackClosesTheFormAndSkipStaysTheDefault() {
        val open = OnboardingFlow.openNextcloudForm(atNextcloud())
        val skipped = OnboardingFlow.skip(open)
        assertFalse(skipped.nextcloudFormOpen)
        assertEquals(listOf(OnboardingStep.NEXTCLOUD), OnboardingFlow.pendingForLater(skipped))
        assertFalse(OnboardingFlow.back(open).nextcloudFormOpen)
    }

    @Test
    fun theOpenFormSurvivesProcessDeathOnlyOnItsStep() {
        val open = OnboardingFlow.restore("NEXTCLOUD", emptyList(), listOf("WELCOME"), nextcloudFormOpen = true)
        assertTrue(open.nextcloudFormOpen)
        assertFalse(OnboardingFlow.restore("BATTERY", emptyList(), emptyList(), nextcloudFormOpen = true).nextcloudFormOpen)
    }
}
