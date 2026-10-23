package com.qtekfun.ultimatephone.settings

/**
 * Decides which SIM a call uses. Explicit choice beats the SIM remembered for the number, which beats the SIM the last call
 * with the number used (calling back on the SIM that rang), which beats the default.
 */
object SimChooser {
    /**
     * @return the key of the SIM to use, or null to let the system decide (single SIM, or the user wants to be asked).
     */
    fun choose(explicit: String?, remembered: String?, default: String?, availableKeys: Set<String>, lastUsed: String? = null): String? =
        listOf(explicit, remembered, lastUsed, default).firstOrNull { it != null && it in availableKeys }
}
