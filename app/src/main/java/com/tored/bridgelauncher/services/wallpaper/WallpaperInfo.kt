package com.tored.bridgelauncher.services.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.Base64
import android.view.ContextThemeWrapper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class WallpaperInfo(private val context: Context) {
    private val _wallpaperBytes = MutableStateFlow<ByteArray?>(null)
    val wallpaperBytes = _wallpaperBytes.asStateFlow()
    private val _wallpaperBase64 = MutableStateFlow<String?>(null)
    val wallpaperBase64 = _wallpaperBase64.asStateFlow()
    private val _monetPaletteJson = MutableStateFlow<String?>(null)
    val monetPaletteJson = _monetPaletteJson.asStateFlow()

    fun getWallpaper() {
        val wallpaperManager = WallpaperManager.getInstance(context)
        val drawable = try {
            wallpaperManager.drawable
        } catch (e: SecurityException) {
            e.printStackTrace()
            return
        }

        if (drawable is BitmapDrawable) {
            val bitmap: Bitmap = drawable.bitmap
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            val byteArray = outputStream.toByteArray()
            _wallpaperBytes.value = byteArray
            val base64String = Base64.encodeToString(byteArray, Base64.NO_WRAP)
            _wallpaperBase64.value = base64String

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val attrs = intArrayOf(
                    android.R.attr.colorAccent,
                    android.R.attr.colorBackground,
                    android.R.attr.textColorPrimary,
                    android.R.attr.textColorSecondary,
                    android.R.attr.colorPrimary,
                    android.R.attr.colorPrimaryDark,
                    android.R.attr.colorForeground,
                    android.R.attr.colorButtonNormal,
                    android.R.attr.colorEdgeEffect,
                    android.R.attr.colorFocusedHighlight,
                    android.R.attr.colorLongPressedHighlight,
                    android.R.attr.colorMultiSelectHighlight,
                    android.R.attr.colorSecondary,
                    android.R.attr.textColor,
                    android.R.attr.textColorTertiary,
                    android.R.attr.textColorPrimaryInverse,
                    android.R.attr.textColorSecondaryInverse,
                    android.R.attr.textColorHint,
                    android.R.attr.textColorHighlight,
                    android.R.attr.textColorLink
                )

                val uiMode = context.resources.configuration.uiMode
                val isNight = (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES

                val themedContext = ContextThemeWrapper(context,
                    if (isNight) android.R.style.Theme_DeviceDefault else android.R.style.Theme_DeviceDefault_Light
                )

                val ta = themedContext.obtainStyledAttributes(attrs)

                @Suppress("ResourceType")
                val monetPalette = mapOf(
                    "accent" to toHex(ta.getColor(0, 0)),
                    "background" to toHex(ta.getColor(1, 0)),
                    "textPrimary" to toHex(ta.getColor(2, 0)),
                    "textSecondary" to toHex(ta.getColor(3, 0)),
                    "primary" to toHex(ta.getColor(4, 0)),
                    "primaryDark" to toHex(ta.getColor(5, 0)),
                    "foreground" to toHex(ta.getColor(6, 0)),
                    "button" to toHex(ta.getColor(7, 0)),
                    "edgeEffect" to toHex(ta.getColor(8, 0)),
                    "focusedHighlight" to toHex(ta.getColor(9, 0)),
                    "longPressedHighlight" to toHex(ta.getColor(10, 0)),
                    "multiSelectHighlight" to toHex(ta.getColor(11, 0)),
                    "secondary" to toHex(ta.getColor(12, 0)),
                    "text" to toHex(ta.getColor(13, 0)),
                    "textTertiary" to toHex(ta.getColor(14, 0)),
                    "textPrimaryInverse" to toHex(ta.getColor(15, 0)),
                    "textSecondaryInverse" to toHex(ta.getColor(16, 0)),
                    "textHint" to toHex(ta.getColor(17, 0)),
                    "textHighlight" to toHex(ta.getColor(18, 0)),
                    "textLink" to toHex(ta.getColor(19, 0))
                )

                ta.recycle()

                _monetPaletteJson.value = Json.encodeToString(monetPalette)
            }
        }
    }

    fun startup() {
        getWallpaper()
    }

    fun refresh() {
        getWallpaper()
    }

    private fun toHex(color: Int): String {
        return String.format("#%06X", 0xFFFFFF and color)
    }
}
