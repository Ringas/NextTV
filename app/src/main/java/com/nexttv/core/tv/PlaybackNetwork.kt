// SPDX-License-Identifier: GPL-3.0-only
package com.nexttv.core.tv

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build

internal class PlaybackNetwork(context: Context) {
    private val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val offline get() = connectivity.activeNetwork == null

    fun callback(onLost: () -> Unit, onAvailable: () -> Unit) =
        object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) = onLost()
            override fun onAvailable(network: Network) = onAvailable()
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = onAvailable()
        }

    fun register(callback: ConnectivityManager.NetworkCallback) {
        runCatching {
            if (Build.VERSION.SDK_INT >= 24) connectivity.registerDefaultNetworkCallback(callback)
            else connectivity.registerNetworkCallback(
                NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),
                callback,
            )
        }
    }

    fun unregister(callback: ConnectivityManager.NetworkCallback) {
        runCatching { connectivity.unregisterNetworkCallback(callback) }
    }
}
