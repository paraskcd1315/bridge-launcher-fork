package com.tored.bridgelauncher.api2.server.endpoints

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.tored.bridgelauncher.api2.server.BridgeAPIEndpointWallpaperInfoSignalResponse
import com.tored.bridgelauncher.api2.server.IBridgeServerEndpoint
import com.tored.bridgelauncher.api2.server.jsonResponse
import com.tored.bridgelauncher.services.wallpaper.WallpaperInfo
import kotlinx.serialization.json.Json

class WallpaperInfoEndpoint(private val _wallpaperInfo: WallpaperInfo): IBridgeServerEndpoint {
    override suspend fun handle(req: WebResourceRequest): WebResourceResponse {
        return jsonResponse(
            Json.encodeToString(
                BridgeAPIEndpointWallpaperInfoSignalResponse.serializer(),
                BridgeAPIEndpointWallpaperInfoSignalResponse(
                    wallpaperBase64 = _wallpaperInfo.wallpaperBase64.value.toString()
                )
            )
        )
    }
}