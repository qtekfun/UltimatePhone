package com.qtekfun.ultimatephone.settings

/** Decides which SIM a call uses. Explicit choice beats the SIM remembered for the number, which beats the default. */
object SimChooser {
    /**
     * @return the key of the SIM to use, or null to let the system decide (single SIM, or the user wants to be asked).
     */
    fun choose(explicit: String?, remembered: String?, default: String?, availableKeys: Set<String>): String? =
        listOf(explicit, remembered, default).firstOrNull { it != null && it in availableKeys }
}
