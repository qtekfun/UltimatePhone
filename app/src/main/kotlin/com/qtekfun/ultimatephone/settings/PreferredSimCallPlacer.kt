package com.qtekfun.ultimatephone.settings

import com.qtekfun.ultimatephone.core.phonenumber.PhoneNormalizer
import com.qtekfun.ultimatephone.core.phonenumber.e164OrNull
import com.qtekfun.ultimatephone.core.telecom.CallPlacer
import com.qtekfun.ultimatephone.core.telecom.EmergencyNumbers
import com.qtekfun.ultimatephone.core.telecom.PostDial
import com.qtekfun.ultimatephone.core.telecom.RegionProvider
import com.qtekfun.ultimatephone.core.telecom.SimAccount
import com.qtekfun.ultimatephone.core.telecom.SimRepository

/**
 * Applies the SIM preferences to every call the app starts (keypad, history, contacts), not only to the keypad. When no
 * SIM can be chosen the call goes out without one: Telecom then uses the system default or, with "ask every time", hands
 * the call to the call screen to pick the SIM.
 */
class PreferredSimCallPlacer(
    private val delegate: CallPlacer,
    private val sims: SimRepository,
    private val settings: SettingsRepository,
    private val normalizer: PhoneNormalizer,
    private val regions: RegionProvider,
    private val emergency: EmergencyNumbers
) : CallPlacer {
    override fun placeCall(number: String, sim: SimAccount?, lastUsed: SimAccount?): Boolean {
        if (number.isBlank()) return false
        // Emergency numbers go out at once, on whatever SIM the system picks.
        if (emergency.isEmergency(number)) return delegate.placeCall(number)
        if (sim != null) return delegate.placeCall(number, sim)
        val available = sims.accounts()
        if (available.size < 2) return delegate.placeCall(number)
        val prefs = settings.settings.value
        // "912345678,22": the SIM is remembered for the number, not for the extension after it.
        val e164 = normalizer.normalize(PostDial.networkPart(number), regions.defaultRegion()).e164OrNull()
        val key = SimChooser.choose(null, e164?.let { prefs.simByNumber[it] }, prefs.defaultSimKey, available.map { it.key }.toSet(), lastUsed = lastUsed?.key)
        return delegate.placeCall(number, available.firstOrNull { it.key == key })
    }

    override fun callVoicemail(sim: SimAccount?): Boolean {
        if (sim != null) return delegate.callVoicemail(sim)
        val available = sims.accounts()
        if (available.size < 2) return delegate.callVoicemail()
        val key = SimChooser.choose(null, null, settings.settings.value.defaultSimKey, available.map { it.key }.toSet())
        return delegate.callVoicemail(available.firstOrNull { it.key == key })
    }
}
