package com.qtekfun.ultimatephone.settings

import android.content.ComponentName
import android.telecom.PhoneAccountHandle
import com.qtekfun.ultimatephone.core.designsystem.ThemeMode
import com.qtekfun.ultimatephone.core.telecom.CallPlacer
import com.qtekfun.ultimatephone.core.telecom.SimAccount
import com.qtekfun.ultimatephone.feature.recents.FakeNormalizer
import com.qtekfun.ultimatephone.feature.recents.FakeRegion
import com.qtekfun.ultimatephone.feature.recents.FakeSims
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Android's classes are stubs in JVM tests: the handle is a placeholder and the key is given. */
fun sim(key: String, slot: Int = 0) = SimAccount(PhoneAccountHandle(ComponentName("pkg", "cls"), key), key, slot, key = key)

class FakeSimSettings(initial: UserSettings = UserSettings()) : SettingsRepository {
    override val settings = MutableStateFlow(initial)

    override suspend fun setThemeMode(mode: ThemeMode) = Unit

    override suspend fun setDefaultSim(key: String?) {
        settings.value = settings.value.copy(defaultSimKey = key)
    }

    override suspend fun rememberSim(numberE164: String, simKey: String) {
        settings.value = settings.value.copy(simByNumber = settings.value.simByNumber + (numberE164 to simKey))
    }

    override suspend fun setRegionOverride(region: String?) = Unit

    override suspend fun setUseSystemColors(enabled: Boolean) = Unit

    override suspend fun setLockPortrait(enabled: Boolean) = Unit
}

private class RecordingPlacer : CallPlacer {
    val calls = mutableListOf<Pair<String, SimAccount?>>()

    override fun placeCall(number: String, sim: SimAccount?, lastUsed: SimAccount?): Boolean {
        calls += number to sim
        return true
    }
}

class PreferredSimCallPlacerTest {
    private val sim1 = sim("sim1", 0)
    private val sim2 = sim("sim2", 1)
    private val delegate = RecordingPlacer()

    private fun placer(sims: List<SimAccount> = listOf(sim1, sim2), prefs: UserSettings = UserSettings(), emergency: Set<String> = setOf("112")) =
        PreferredSimCallPlacer(delegate, FakeSims(sims), FakeSimSettings(prefs), FakeNormalizer(), FakeRegion()) { it in emergency }

    @Test
    fun withNoPreferenceTheSystemDecides() {
        placer().placeCall("600111222")
        assertNull(delegate.calls.single().second)
    }

    @Test
    fun theDefaultSimIsUsedFromEveryScreen() {
        placer(prefs = UserSettings(defaultSimKey = "sim2")).placeCall("600111222")
        assertSame(sim2, delegate.calls.single().second)
    }

    @Test
    fun theSimRememberedForTheNumberBeatsTheDefault() {
        val prefs = UserSettings(defaultSimKey = "sim2", simByNumber = mapOf("+34600111222" to "sim1"))
        placer(prefs = prefs).placeCall("600 111 222")
        assertSame(sim1, delegate.calls.single().second)
    }

    @Test
    fun callingBackUsesTheSimThatRangBeforeTheDefault() {
        placer(prefs = UserSettings(defaultSimKey = "sim1")).placeCall("600111222", lastUsed = sim2)
        assertSame(sim2, delegate.calls.single().second)
    }

    @Test
    fun anExplicitSimIsKept() {
        placer(prefs = UserSettings(defaultSimKey = "sim1")).placeCall("600111222", sim = sim2)
        assertSame(sim2, delegate.calls.single().second)
    }

    @Test
    fun emergencyNumbersNeverForceASim() {
        placer(prefs = UserSettings(defaultSimKey = "sim1")).placeCall("112", sim = sim2)
        assertNull(delegate.calls.single().second)
    }

    @Test
    fun withOneSimNothingIsForced() {
        placer(sims = listOf(sim1), prefs = UserSettings(defaultSimKey = "sim1")).placeCall("600111222")
        assertNull(delegate.calls.single().second)
    }

    @Test
    fun aRemovedSimIsIgnored() {
        placer(prefs = UserSettings(defaultSimKey = "gone")).placeCall("600111222", lastUsed = sim("gone2"))
        assertNull(delegate.calls.single().second)
    }

    @Test
    fun blankNumbersAreNotCalled() {
        assertEquals(false, placer().placeCall(" "))
        assertTrue(delegate.calls.isEmpty())
    }
}

class CallSimPreferencesTest {
    private val sim1 = sim("sim1", 0)
    private val sim2 = sim("sim2", 1)

    @Test
    fun asksWhenNothingIsKnown() {
        assertNull(SettingsCallSimPreferences(FakeSimSettings(), FakeNormalizer(), FakeRegion()).preferred("600111222", listOf(sim1, sim2)))
    }

    @Test
    fun usesTheRememberedSimThenTheDefault() {
        val settings = FakeSimSettings(UserSettings(defaultSimKey = "sim1", simByNumber = mapOf("+34600111222" to "sim2")))
        val prefs = SettingsCallSimPreferences(settings, FakeNormalizer(), FakeRegion())
        assertSame(sim2, prefs.preferred("600111222", listOf(sim1, sim2)))
        assertSame(sim1, prefs.preferred("699000111", listOf(sim1, sim2)))
        assertSame(sim1, prefs.preferred(null, listOf(sim1, sim2)))
    }

    @Test
    fun aSingleChoiceNeedsNoQuestion() {
        assertSame(sim2, SettingsCallSimPreferences(FakeSimSettings(), FakeNormalizer(), FakeRegion()).preferred("600111222", listOf(sim2)))
    }

    @Test
    fun rememberingStoresTheNumberInE164() = runTest {
        val settings = FakeSimSettings()
        SettingsCallSimPreferences(settings, FakeNormalizer(), FakeRegion()).remember("600 111 222", sim2)
        assertEquals(mapOf("+34600111222" to "sim2"), settings.settings.value.simByNumber)
    }
}
