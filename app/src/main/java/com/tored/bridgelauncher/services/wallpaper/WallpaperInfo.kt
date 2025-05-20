package com.tored.bridgelauncher.services.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream

class WallpaperInfo(private val context: Context) {
    private val _wallpaperBytes = MutableStateFlow<ByteArray?>(null)
    val wallpaperBytes = _wallpaperBytes.asStateFlow()

    fun getWallpaper() {
        val wallpaperManager = WallpaperManager.getInstance(context)
        val drawable = wallpaperManager.drawable

        if (drawable is BitmapDrawable) {
            val bitmap: Bitmap = drawable.bitmap
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            val byteArray = outputStream.toByteArray()
            _wallpaperBytes.value = byteArray
        }
    }

    fun startup() {
        getWallpaper()
    }

    fun refresh() {
        getWallpaper()
    }
}