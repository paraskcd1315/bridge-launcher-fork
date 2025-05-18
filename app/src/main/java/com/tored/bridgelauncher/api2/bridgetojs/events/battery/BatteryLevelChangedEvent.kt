package com.tored.bridgelauncher.api2.bridgetojs.events.battery

import com.tored.bridgelauncher.api2.bridgetojs.BridgeEventModel
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
class BatteryLevelChangedEvent(
    val value: Int,
) : BridgeEventModel("batteryLevelChanged")
{
    override fun getJson() = Json.encodeToString(serializer(),this)
}