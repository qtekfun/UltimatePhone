package com.qtekfun.ultimatephone.core.telecom

import android.telecom.PhoneAccountHandle

/** A SIM (telecom phone account) that can place calls. [slot] is the physical slot index, or -1 when unknown. */
data class SimAccount(val handle: PhoneAccountHandle, val label: String, val slot: Int) {
    /** Stable key to remember a choice, for example "SIM per contact". */
    val key: String get() = "${handle.componentName.flattenToShortString()}/${handle.id}"
}
