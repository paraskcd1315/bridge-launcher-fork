package com.tored.bridgelauncher.services.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream

class WallpaperInfo(private val context: Context) {
    private val _wallpaperBase64 = MutableStateFlow<String?>(null)
    val wallpaperBase64 = _wallpaperBase64.asStateFlow()

    fun getWallpaper() {
        val wallpaperManager = WallpaperManager.getInstance(context)
        val drawable = wallpaperManager.drawable

        if (drawable is BitmapDrawable) {
            val bitmap: Bitmap = drawable.bitmap
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            val byteArray = outputStream.toByteArray()
            val base64String = Base64.encodeToString(byteArray, Base64.NO_WRAP)
            _wallpaperBase64.value = base64String
        }
    }

    fun startup() {
        getWallpaper()
    }

    fun refresh() {
        getWallpaper()
    }
}