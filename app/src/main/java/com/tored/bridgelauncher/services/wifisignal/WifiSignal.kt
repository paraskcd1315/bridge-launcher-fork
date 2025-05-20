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

    @RequiresApi(Build.VERSION_CODES.S)
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            super.onCapabilitiesChanged(network, networkCapabilities)
            val wifiInfo = networkCapabilities.transportInfo as? WifiInfo
            wifiInfo?.let {
                updateFromWifiInfo(it)
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
            updateFromWifiInfo(wifiInfo)
        }
    }

    private fun updateFromWifiInfo(wifiInfo: WifiInfo) {
        val rssi = wifiInfo.rssi
        updateSignalStrengthLevels(rssi)
        val rawSsid = wifiInfo.ssid
        _ssid.value = if (rawSsid != null && rawSsid != "<unknown ssid>" && rawSsid.isNotBlank()) {
            rawSsid.trim('"')
        } else {
            ""
        }
    }

    private fun updateSignalStrengthLevels(signalStrength: Int) {
        _wifiSignalStrength.value = signalStrength
        _wifiSignalLevel.value = when {
            signalStrength >= -60 -> 4
            signalStrength >= -70 -> 3
            signalStrength >= -80 -> 2
            signalStrength >= -90 -> 1
            else -> 0
        }
    }
}