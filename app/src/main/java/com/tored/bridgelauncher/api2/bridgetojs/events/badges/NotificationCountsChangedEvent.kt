package com.tored.bridgelauncher.api2.bridgetojs.events.badges

import com.tored.bridgelauncher.api2.bridgetojs.BridgeEventModel
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
class NotificationCountsChangedEvent(val value: Map<String, Int>): BridgeEventModel("notificationCountsChanged") {
    override fun getJson(): String {
        return Json.encodeToString(serializer(), this)
    }
}