package com.tored.bridgelauncher.services.wifisignal

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class WifiSignal(private val context: Context) {
    private val _wifiSignalStrength = MutableStateFlow(-100)
    val wifiSignalStrength = _wifiSignalStrength.asStateFlow()

    private val _ssid = MutableStateFlow("<unknown ssid>")
    val ssid = _ssid.asStateFlow()

    private val _wifiSignalLevel = MutableStateFlow(0)
    val wifiSignalLevel = _wifiSignalLevel.asStateFlow()

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val networkCallback = @RequiresApi(Build.VERSION_CODES.S)
    object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            super.onCapabilitiesChanged(network, networkCapabilities)
            val wifiInfo = networkCapabilities.transportInfo as? WifiInfo
            wifiInfo?.let {
                val rssi = it.rssi
                _wifiSignalStrength.value = rssi
                _wifiSignalLevel.value = when {
                    rssi >= -60 -> 4
                    rssi >= -70 -> 3
                    rssi >= -80 -> 2
                    rssi >= -90 -> 1
                    else -> 0
                }
                val rawSsid = it.ssid
                _ssid.value = if (rawSsid != null && rawSsid != "<unknown ssid>" && rawSsid.isNotBlank()) {
                    rawSsid.trim('"')
                } else {
                    ""
                }
            }
        }

        override fun onLost(network: Network) {
            super.onLost(network)
            _wifiSignalStrength.value = -100
            _wifiSignalLevel.value = 0
            _ssid.value = ""
        }
    }

    fun startup() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
        } else {
            val wifiManager =
                context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiInfo = wifiManager.connectionInfo
            val rssi = wifiInfo.rssi
            _wifiSignalStrength.value = rssi
            _wifiSignalLevel.value = when {
                rssi >= -60 -> 4
                rssi >= -70 -> 3
                rssi >= -80 -> 2
                rssi >= -90 -> 1
                else -> 0
            }
            val rawSsid = wifiInfo.ssid
            _ssid.value = if (rawSsid != null && rawSsid != "<unknown ssid>" && rawSsid.isNotBlank()) {
                rawSsid.trim('"')
            } else {
                ""
            }
        }
    }
}