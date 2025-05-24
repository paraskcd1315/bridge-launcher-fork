package com.tored.bridgelauncher.api2.bridgetojs.events.location

import com.tored.bridgelauncher.api2.bridgetojs.BridgeEventModel
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class LocationInfoEvent(
    val latitude: Double? = null,
    val longitude: Double? = null
) : BridgeEventModel("locationChanged") {
    companion object {
        fun fromLatLon(lat: Double?, lon: Double?): LocationInfoEvent {
            return LocationInfoEvent(latitude = lat, longitude = lon)
        }
    }

    override fun getJson() = Json.encodeToString(serializer(), this)
}