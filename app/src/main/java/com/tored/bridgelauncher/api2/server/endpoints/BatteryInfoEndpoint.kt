package com.tored.bridgelauncher.api2.server.endpoints

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.tored.bridgelauncher.api2.server.BridgeAPIEndpointBatteryResponse
import com.tored.bridgelauncher.api2.server.IBridgeServerEndpoint
import com.tored.bridgelauncher.api2.server.jsonResponse
import com.tored.bridgelauncher.services.battery.BatteryInfo
import kotlinx.serialization.json.Json

class BatteryInfoEndpoint(private val _batteryInfo: BatteryInfo): IBridgeServerEndpoint {
    override suspend fun handle(req: WebResourceRequest): WebResourceResponse {
        return jsonResponse(
            Json.encodeToString(
                BridgeAPIEndpointBatteryResponse.serializer(),
                BridgeAPIEndpointBatteryResponse(
                    level = _batteryInfo.batteryLevel.value,
                    isCharging = _batteryInfo.isCharging.value
                )
            )
        )
    }
}