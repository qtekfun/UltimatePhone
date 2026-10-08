package com.qtekfun.ultimatephone.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

enum class NetworkState { NONE, METERED, UNMETERED }

fun interface NetworkChecker {
    fun state(): NetworkState
}

class ConnectivityNetworkChecker(context: Context) : NetworkChecker {
    private val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    override fun state(): NetworkState {
        val network = manager.activeNetwork ?: return NetworkState.NONE
        val caps = manager.getNetworkCapabilities(network) ?: return NetworkState.NONE
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return NetworkState.NONE
        return if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) NetworkState.UNMETERED else NetworkState.METERED
    }
}

/** Whether a download may start, given the "Wi-Fi only" setting and the current connection. */
enum class DownloadPermission { ALLOWED, NO_NETWORK, BLOCKED_BY_WIFI_ONLY }

object NetworkPolicy {
    fun permission(wifiOnly: Boolean, state: NetworkState): DownloadPermission = when {
        state == NetworkState.NONE -> DownloadPermission.NO_NETWORK
        wifiOnly && state == NetworkState.METERED -> DownloadPermission.BLOCKED_BY_WIFI_ONLY
        else -> DownloadPermission.ALLOWED
    }
}
