package com.qtekfun.ultimatephone.core.telecom

import android.annotation.SuppressLint
import android.content.Context
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager

/** Which SIMs the phone has. Needs READ_PHONE_STATE; without it the list is empty and calls use the default SIM. */
interface SimRepository {
    fun accounts(): List<SimAccount>

    /** The SIM a call to this phone-account handle used, or null when the handle is unknown. */
    fun find(handle: PhoneAccountHandle?): SimAccount?

    /** The call-log account columns identify a SIM by component and id; maps them back to a [SimAccount]. */
    fun find(componentName: String?, id: String?): SimAccount?
}

class TelecomSimRepository(private val context: Context) : SimRepository {
    @SuppressLint("MissingPermission")
    override fun accounts(): List<SimAccount> = try {
        val telecom = context.getSystemService(TelecomManager::class.java)
        telecom.callCapablePhoneAccounts.map { handle ->
            SimAccount(handle, telecom.getPhoneAccount(handle)?.label?.toString() ?: handle.id, slotOf(handle))
        }
    } catch (_: SecurityException) {
        emptyList()
    }

    override fun find(handle: PhoneAccountHandle?): SimAccount? = handle?.let { h ->
        accounts().firstOrNull { it.handle == h } ?: SimAccount(h, h.id, slotOf(h))
    }

    override fun find(componentName: String?, id: String?): SimAccount? {
        if (componentName == null || id == null) return null
        return accounts().firstOrNull { it.handle.componentName.flattenToString() == componentName && it.handle.id == id }
    }

    @SuppressLint("MissingPermission")
    private fun slotOf(handle: PhoneAccountHandle): Int = try {
        val subId = context.getSystemService(TelephonyManager::class.java).getSubscriptionId(handle)
        context.getSystemService(SubscriptionManager::class.java).getActiveSubscriptionInfo(subId)?.simSlotIndex ?: -1
    } catch (_: SecurityException) {
        -1
    }
}
