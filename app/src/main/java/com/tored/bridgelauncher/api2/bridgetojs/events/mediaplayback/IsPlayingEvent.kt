package com.tored.bridgelauncher.api2.bridgetojs.events.mediaplayback

import com.tored.bridgelauncher.api2.bridgetojs.BridgeEventModel
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
class IsPlayingEvent(val value: Boolean) : BridgeEventModel("isPlayingChanged") {
    override fun getJson() = Json.encodeToString(serializer(),this)
}