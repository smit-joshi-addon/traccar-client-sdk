package org.traccar.client

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService
import kotlinx.coroutines.flow.MutableStateFlow

class AndroidNetworkMonitor(context: Context) : NetworkMonitor {

    private val connectivityManager: ConnectivityManager =
        checkNotNull(context.applicationContext.getSystemService())
    override val isOnline = MutableStateFlow(currentStatus())

    init {
        connectivityManager.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(
                    network: Network,
                    capabilities: NetworkCapabilities,
                ) {
                    isOnline.value = capabilities.isUsable()
                }

                override fun onLost(network: Network) {
                    isOnline.value = false
                }

                override fun onUnavailable() {
                    isOnline.value = false
                }
            },
        )
    }

    private fun currentStatus(): Boolean =
        connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
            ?.isUsable() == true

    private fun NetworkCapabilities.isUsable(): Boolean =
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
