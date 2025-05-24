package com.tored.bridgelauncher.services.mediaplayback

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.util.Log
import android.widget.MediaController
import android.os.Handler
import android.os.Looper
import com.tored.bridgelauncher.services.notificationbadges.NotificationBadgesService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class MediaPlayback(private val context: Context) {
    private val mediaSessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private var mediaController: android.media.session.MediaController? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _position = MutableStateFlow(0L)
    val position = _position.asStateFlow()

    private val _metadata = MutableStateFlow<android.media.MediaMetadata?>(null)
    val metadata = _metadata.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun startup() {
        scope.launch(Dispatchers.Main) {
            repeat(20) {
                delay(500)
                val service = NotificationBadgesService.instance
                if (service != null) {
                    val component = ComponentName(service, NotificationBadgesService::class.java)

                    val controllers = mediaSessionManager.getActiveSessions(component)

                    mediaController = controllers.firstOrNull {
                        it.playbackState?.state == PlaybackState.STATE_PLAYING || it.metadata != null
                    }

                    Log.d("MediaPlayback", "Controller selected: ${mediaController?.packageName}")

                    mediaController?.registerCallback(mediaCallback)
                    mediaController?.metadata?.let { _metadata.value = it }
                    mediaController?.playbackState?.let { updatePlaybackState(it) }

                    mediaSessionManager.addOnActiveSessionsChangedListener(sessionListener, component, Handler(Looper.getMainLooper()))

                    return@launch
                }
            }
        }
    }

    private val mediaCallback = object : android.media.session.MediaController.Callback() {
        override fun onMetadataChanged(metadata: android.media.MediaMetadata?) {
            Log.d("MediaPlayback", "Controller selected: ${mediaController?.packageName}")
            _metadata.value = metadata
        }

        override fun onPlaybackStateChanged(state: android.media.session.PlaybackState?) {
            updatePlaybackState(state)
        }
    }

    private fun updatePlaybackState(state: android.media.session.PlaybackState?) {
        _isPlaying.value = state?.state == android.media.session.PlaybackState.STATE_PLAYING
        _position.value = state?.position ?: 0L
    }

    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        scope.launch {
            Log.d("MediaPlayback", "⚡ Active sessions changed")
            val newController = controllers?.firstOrNull {
                it.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING ||
                        it.metadata != null
            }
            Log.d("MediaPlayback", "🎯 Switching to: ${newController?.packageName}")

            withContext(Dispatchers.Main) {
                mediaController?.unregisterCallback(mediaCallback)
                mediaController = newController
                if (mediaController != null) {
                    mediaController?.registerCallback(mediaCallback)
                    mediaController?.metadata?.let { _metadata.value = it }
                    mediaController?.playbackState?.let { updatePlaybackState(it) }
                } else {
                    _metadata.value = null
                    updatePlaybackState(null)
                }
            }
        }
    }


    fun play() = mediaController?.transportControls?.play()
    fun pause() = mediaController?.transportControls?.pause()
    fun seekTo(ms: Long) = mediaController?.transportControls?.seekTo(ms)
    fun skipNext() = mediaController?.transportControls?.skipToNext()
    fun skipPrevious() = mediaController?.transportControls?.skipToPrevious()
}