package com.tored.bridgelauncher.api2.bridgetojs.events.wifi

import com.tored.bridgelauncher.api2.bridgetojs.BridgeEventModel
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
class WifiStrengthEvent(val value: Int) : BridgeEventModel("wifiStrengthIsChanged") {
    override fun getJson() = Json.encodeToString(serializer(),this)
}