package com.qtekfun.ultimatephone.settings

import com.qtekfun.ultimatephone.core.phonenumber.PhoneNormalizer
import com.qtekfun.ultimatephone.core.phonenumber.e164OrNull
import com.qtekfun.ultimatephone.core.telecom.RegionProvider
import com.qtekfun.ultimatephone.core.telecom.SimAccount

/** The SIM preferences as the call screen needs them, for a call that waits for the user to pick a SIM. */
interface CallSimPreferences {
    /** The SIM to use without asking, from the SIM remembered for [number] or the default; null to ask the user. */
    fun preferred(number: String?, choices: List<SimAccount>): SimAccount?

    /** Remembers [sim] for [number], so the next call to it does not ask. */
    suspend fun remember(number: String, sim: SimAccount)
}

/** Nothing preferred, nothing remembered: the user is always asked. */
object NoCallSimPreferences : CallSimPreferences {
    override fun preferred(number: String?, choices: List<SimAccount>): SimAccount? = null

    override suspend fun remember(number: String, sim: SimAccount) = Unit
}

class SettingsCallSimPreferences(private val settings: SettingsRepository, private val normalizer: PhoneNormalizer, private val regions: RegionProvider) :
    CallSimPreferences {
    override fun preferred(number: String?, choices: List<SimAccount>): SimAccount? {
        if (choices.size < 2) return choices.singleOrNull()
        val prefs = settings.settings.value
        val remembered = e164(number)?.let { prefs.simByNumber[it] }
        val key = SimChooser.choose(null, remembered, prefs.defaultSimKey, choices.map { it.key }.toSet())
        return choices.firstOrNull { it.key == key }
    }

    override suspend fun remember(number: String, sim: SimAccount) {
        e164(number)?.let { settings.rememberSim(it, sim.key) }
    }

    private fun e164(number: String?): String? = number?.let { normalizer.normalize(it, regions.defaultRegion()).e164OrNull() }
}
