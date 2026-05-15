package com.zonik.wear.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Reports whether the watch has actual Internet (Wi-Fi or cellular).
 * Bluetooth-tethered "Internet via phone companion" doesn't qualify — we
 * don't proxy stream traffic through a paired phone and the watch's BT
 * link isn't fast enough for music streaming.
 */
class NetworkMonitor(context: Context) {

    private val _hasInternet = MutableStateFlow(false)
    val hasInternet: StateFlow<Boolean> = _hasInternet.asStateFlow()

    private val cm = context.applicationContext
        .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    init {
        // Initial state from active network.
        _hasInternet.value = isStreamingCapable(cm.activeNetwork)

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .build()
        cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _hasInternet.value = isStreamingCapable(network)
            }
            override fun onLost(network: Network) {
                _hasInternet.value = isStreamingCapable(cm.activeNetwork)
            }
            override fun onCapabilitiesChanged(
                network: Network,
                caps: NetworkCapabilities,
            ) {
                _hasInternet.value = caps.hasStreamingTransport() && caps.hasInternet()
            }
        })
    }

    private fun isStreamingCapable(network: Network?): Boolean {
        if (network == null) return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasStreamingTransport() && caps.hasInternet()
    }

    private fun NetworkCapabilities.hasStreamingTransport(): Boolean =
        hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)

    private fun NetworkCapabilities.hasInternet(): Boolean =
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
