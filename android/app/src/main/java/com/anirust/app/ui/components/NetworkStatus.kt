package com.anirust.app.ui.components

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

@Composable
fun rememberOnline(): Boolean {
    val context = LocalContext.current
    val manager =
        remember(context) {
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        }
    fun online() =
        manager
            .getNetworkCapabilities(manager.activeNetwork)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    var connected by remember { mutableStateOf(online()) }
    DisposableEffect(manager) {
        val callback =
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(
                    network: Network,
                    capabilities: NetworkCapabilities,
                ) {
                    connected = online()
                }

                override fun onLost(network: Network) {
                    connected = online()
                }
            }
        manager.registerDefaultNetworkCallback(callback)
        onDispose { manager.unregisterNetworkCallback(callback) }
    }
    return connected
}
