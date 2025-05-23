package com.tored.bridgelauncher.api2.bridgetojs.events.mediaplayback

import android.graphics.Bitmap
import android.media.MediaMetadata
import android.util.Base64
import android.util.Log
import com.tored.bridgelauncher.api2.bridgetojs.BridgeEventModel
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream

@Serializable
data class MediaMetaDataEvent(
    val artist: String? = null,
    val title: String? = null,
    val album: String? = null,
    val duration: Long? = null,
    val artworkBase64: String? = null
) : BridgeEventModel("mediaMetadataChanged") {
    companion object {
        fun fromMediaMetadata(metadata: MediaMetadata?): MediaMetaDataEvent {
            if (metadata == null) return MediaMetaDataEvent()
            val bitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
            val base64Artwork = bitmap?.let { bmp ->
                val stream = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.PNG, 100, stream)
                Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
            }
            return MediaMetaDataEvent(
                artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST),
                title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE),
                album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM),
                duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION).takeIf { it > 0 },
                artworkBase64 = base64Artwork
            )
        }
    }

    override fun getJson() = Json.encodeToString(serializer(), this)
}