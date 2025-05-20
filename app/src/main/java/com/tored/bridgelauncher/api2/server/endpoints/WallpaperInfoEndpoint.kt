package com.tored.bridgelauncher.api2.server.endpoints

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.tored.bridgelauncher.api2.server.IBridgeServerEndpoint
import com.tored.bridgelauncher.services.wallpaper.WallpaperInfo

class WallpaperInfoEndpoint(private val _wallpaperInfo: WallpaperInfo): IBridgeServerEndpoint {
    override suspend fun handle(req: WebResourceRequest): WebResourceResponse {
        val bytes = _wallpaperInfo.wallpaperBytes.value ?: ByteArray(0)
        return WebResourceResponse(
            "image/png",
            "utf-8",
            200,
            "OK",
            mapOf("Content-Type" to "image/png"),
            bytes.inputStream()
        )
    }
}