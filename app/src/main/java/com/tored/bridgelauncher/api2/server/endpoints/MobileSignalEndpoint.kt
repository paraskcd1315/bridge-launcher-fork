package com.tored.bridgelauncher.api2.server.endpoints

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.tored.bridgelauncher.api2.server.BridgeAPIEndpointMobileSignalResponse
import com.tored.bridgelauncher.api2.server.IBridgeServerEndpoint
import com.tored.bridgelauncher.api2.server.jsonResponse
import com.tored.bridgelauncher.services.mobilesignal.MobileSignal
import kotlinx.serialization.json.Json

class MobileSignalEndpoint(private val _mobileSignal: MobileSignal): IBridgeServerEndpoint {
    override suspend fun handle(req: WebResourceRequest): WebResourceResponse {
        return jsonResponse(
            Json.encodeToString(
                BridgeAPIEndpointMobileSignalResponse.serializer(),
                BridgeAPIEndpointMobileSignalResponse(
                    signalStrength = _mobileSignal.mobileSignalStrength.value,
                    networkType = _mobileSignal.networkType.value,
                    signalLevel = _mobileSignal.mobileSignalLevel.value
                )
            )
        )
    }
}