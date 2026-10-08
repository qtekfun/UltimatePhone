package com.qtekfun.ultimatephone.core.telecom

import android.content.Context
import android.telephony.TelephonyManager
import java.util.Locale

/** The ISO region used to read numbers that have no country code: the SIM's country, else the phone's locale. */
interface RegionProvider {
    fun defaultRegion(): String
}

class SimRegionProvider(private val context: Context) : RegionProvider {
    override fun defaultRegion(): String {
        val telephony = context.getSystemService(TelephonyManager::class.java)
        val fromSim = telephony.simCountryIso.takeIf { it.isNotBlank() } ?: telephony.networkCountryIso.takeIf { it.isNotBlank() }
        return (fromSim ?: Locale.getDefault().country.takeIf { it.isNotBlank() } ?: FALLBACK).uppercase()
    }

    private companion object {
        const val FALLBACK = "US"
    }
}
