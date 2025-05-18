package com.tored.bridgelauncher.api2.server.endpoints

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.tored.bridgelauncher.api2.server.BridgeAPIEndpointWifiSignalResponse
import com.tored.bridgelauncher.api2.server.IBridgeServerEndpoint
import com.tored.bridgelauncher.api2.server.jsonResponse
import com.tored.bridgelauncher.services.wifisignal.WifiSignal
import kotlinx.serialization.json.Json

class WifiSignalEndpoint(private val _wifiSignal: WifiSignal): IBridgeServerEndpoint {
    override suspend fun handle(req: WebResourceRequest): WebResourceResponse {
        return jsonResponse(
            Json.encodeToString(
                BridgeAPIEndpointWifiSignalResponse.serializer(),
                BridgeAPIEndpointWifiSignalResponse(
                    signalStrength = _wifiSignal.wifiSignalStrength.value,
                    ssid = _wifiSignal.ssid.value,
                    signalLevel = _wifiSignal.wifiSignalLevel.value
                )
            )
        )
    }
}