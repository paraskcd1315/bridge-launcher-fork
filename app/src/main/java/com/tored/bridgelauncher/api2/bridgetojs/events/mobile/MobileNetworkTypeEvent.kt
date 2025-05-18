package com.tored.bridgelauncher.api2.bridgetojs.events.mobile

import com.tored.bridgelauncher.api2.bridgetojs.BridgeEventModel
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
class MobileNetworkTypeEvent(val value: String) : BridgeEventModel("mobileNetworkTypeIsChanged") {
    override fun getJson() = Json.encodeToString(serializer(),this)
}