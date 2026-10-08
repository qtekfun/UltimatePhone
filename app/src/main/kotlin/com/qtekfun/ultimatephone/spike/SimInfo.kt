package com.qtekfun.ultimatephone.spike

import android.annotation.SuppressLint
import android.content.Context
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager

data class SimAccount(val handle: PhoneAccountHandle, val label: String, val slot: Int)

/** Maps telecom phone accounts to SIM slots. Needs READ_PHONE_STATE; without it everything degrades to "unknown". */
object SimInfo {
    @SuppressLint("MissingPermission")
    fun accounts(context: Context): List<SimAccount> = try {
        val telecom = context.getSystemService(TelecomManager::class.java)
        telecom.callCapablePhoneAccounts.map { handle ->
            SimAccount(handle, telecom.getPhoneAccount(handle)?.label?.toString() ?: handle.id, slotOf(context, handle))
        }
    } catch (_: SecurityException) {
        emptyList()
    }

    @SuppressLint("MissingPermission")
    fun slotOf(context: Context, handle: PhoneAccountHandle?): Int {
        if (handle == null) return -1
        return try {
            val subId = context.getSystemService(TelephonyManager::class.java).getSubscriptionId(handle)
            context.getSystemService(SubscriptionManager::class.java).getActiveSubscriptionInfo(subId)?.simSlotIndex ?: -1
        } catch (_: SecurityException) {
            -1
        }
    }

    fun describe(context: Context, handle: PhoneAccountHandle?): String {
        if (handle == null) return "sim=none"
        val slot = slotOf(context, handle)
        return "sim=${if (slot >= 0) "slot$slot" else "slot?"}(${handle.id.takeLast(ID_TAIL)})"
    }

    private const val ID_TAIL = 6
}
