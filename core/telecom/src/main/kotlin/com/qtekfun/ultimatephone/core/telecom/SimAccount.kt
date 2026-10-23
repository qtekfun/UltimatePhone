package com.qtekfun.ultimatephone.core.telecom

import android.telecom.PhoneAccountHandle

/**
 * A SIM (telecom phone account) that can place calls. [slot] is the physical slot index, or -1 when unknown. [key] is a
 * stable key to remember a choice, for example "SIM per contact"; it is derived from the handle.
 */
data class SimAccount(val handle: PhoneAccountHandle, val label: String, val slot: Int, val key: String = keyOf(handle)) {
    companion object {
        fun keyOf(handle: PhoneAccountHandle): String = "${handle.componentName.flattenToShortString()}/${handle.id}"
    }
}
