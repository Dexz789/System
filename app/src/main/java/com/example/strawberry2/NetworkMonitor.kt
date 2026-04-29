package com.example.strawberry2

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest

class NetworkMonitor(
    context: Context,
    private val onStatusChange: (Boolean) -> Unit,
    private val onSlowConnection: ((Boolean) -> Unit)? = null
) {

    companion object {
        // Anything below this threshold (in Kbps) is treated as a slow connection.
        // 512 Kbps (0.5 Mbps) is a reasonable floor for an app that sends images to an AI.
        private const val SLOW_THRESHOLD_KBPS = 512
    }

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            onStatusChange(true)   // internet back
        }
        override fun onLost(network: Network) {
            onStatusChange(false)  // internet gone
            onSlowConnection?.invoke(false)  // clear slow banner when fully disconnected
        }
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            val downKbps = capabilities.getLinkDownstreamBandwidthKbps()
            // Only fire the slow-connection signal when we have *some* connectivity (downKbps > 0)
            // so we don't overlap with the no-internet dialog.
            when {
                downKbps in 1 until SLOW_THRESHOLD_KBPS -> onSlowConnection?.invoke(true)
                downKbps >= SLOW_THRESHOLD_KBPS          -> onSlowConnection?.invoke(false)
                // downKbps == 0 means unknown/no data — don't change state
            }
        }
    }

    fun start() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
    }

    fun stop() {
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }

    fun isConnected(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}