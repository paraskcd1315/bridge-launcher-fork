package com.tored.bridgelauncher.api2.server.endpoints

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.tored.bridgelauncher.api2.server.ContactInfoSerializable
import com.tored.bridgelauncher.api2.server.IBridgeServerEndpoint
import com.tored.bridgelauncher.api2.server.jsonResponse
import com.tored.bridgelauncher.services.contacts.ContactInfo
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.first

class ContactInfoEndpoint(private val _contactInfo: ContactInfo): IBridgeServerEndpoint {
    override suspend fun handle(req: WebResourceRequest): WebResourceResponse {
        // Espera a que la carga inicial termine
        _contactInfo.initialLoadingFinished.first { it }

        val contactList = _contactInfo.contacts.value // Es un StateFlow, pillas el value
        val json = Json.encodeToString(
            ListSerializer(ContactInfoSerializable.serializer()),
            contactList
        )
        return jsonResponse(json)
    }
}