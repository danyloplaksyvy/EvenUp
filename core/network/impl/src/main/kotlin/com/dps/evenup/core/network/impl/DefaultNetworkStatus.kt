package com.dps.evenup.core.network.impl

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.dps.evenup.core.network.api.NetworkStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DefaultNetworkStatus(context: Context) : NetworkStatus {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    private val mutableIsOnline = MutableStateFlow(connectivityManager.currentlyOnline())
    override val isOnline: StateFlow<Boolean> = mutableIsOnline.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refresh()
        override fun onLost(network: Network) = refresh()
        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) = refresh()
    }

    init {
        connectivityManager.registerDefaultNetworkCallback(callback)
    }

    private fun refresh() {
        mutableIsOnline.value = connectivityManager.currentlyOnline()
    }

    private fun ConnectivityManager.currentlyOnline(): Boolean {
        val network = activeNetwork ?: return false
        val capabilities = getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
