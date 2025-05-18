package com.tored.bridgelauncher.api2.bridgetojs.events.wifi

import com.tored.bridgelauncher.api2.bridgetojs.BridgeEventModel
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
class WifiSSIDEvent(val value: String) : BridgeEventModel("wifiSSIDIsChanged") {
    override fun getJson() = Json.encodeToString(serializer(),this)
}