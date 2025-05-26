package com.tored.bridgelauncher.services.mediaplayback

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.util.Log
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive

class MediaPlayback(private val context: Context) {
    private val mediaSessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private var mediaController: android.media.session.MediaController? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _position = MutableStateFlow(0L)
    val position = _position.asStateFlow()

    private val _metadata = MutableStateFlow<android.media.MediaMetadata?>(null)
    val metadata = _metadata.asStateFlow()

    private val _mediaPackageName = MutableStateFlow<String?>(null)
    val packageName = _mediaPackageName.asStateFlow()

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

                    _mediaPackageName.value = mediaController?.packageName

                    mediaController?.registerCallback(mediaCallback)
                    mediaController?.metadata?.let { _metadata.value = it }
                    mediaController?.playbackState?.let { updatePlaybackState(it) }

                    mediaSessionManager.addOnActiveSessionsChangedListener(sessionListener, component, Handler(Looper.getMainLooper()))

                    startFallbackPollingIfNeeded()

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

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            updatePlaybackState(state)
        }
    }

    private fun updatePlaybackState(state: PlaybackState?) {
        _isPlaying.value = state?.state == PlaybackState.STATE_PLAYING
        _position.value = state?.position ?: 0L
    }

    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        scope.launch {
            Log.d("MediaPlayback", "⚡ Active sessions changed")
            val newController = controllers?.firstOrNull {
                it.playbackState?.state == PlaybackState.STATE_PLAYING ||
                        it.metadata != null
            }
            Log.d("MediaPlayback", "🎯 Switching to: ${newController?.packageName}")

            _mediaPackageName.value = newController?.packageName

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
                    startFallbackPollingIfNeeded()
                }
            }
        }
    }

    private var pollingJob: Job? = null

    private fun startFallbackPollingIfNeeded() {
        if (pollingJob?.isActive == true) return

        pollingJob = scope.launch {
            while (isActive) {
                var recovered = false

                repeat(6) { // Intentos rápidos durante 30s
                    delay(5000)
                    val service = NotificationBadgesService.instance ?: return@launch
                    val component = ComponentName(service, NotificationBadgesService::class.java)
                    val controllers = mediaSessionManager.getActiveSessions(component)

                    val newController = controllers.firstOrNull {
                        it.playbackState?.state == PlaybackState.STATE_PLAYING || it.metadata != null
                    }

                    if (newController != null) {
                        Log.d("MediaPlayback", "🔁 Polling recovered controller: ${newController.packageName}")

                        withContext(Dispatchers.Main) {
                            mediaController?.unregisterCallback(mediaCallback)
                            mediaController = newController
                            mediaController?.registerCallback(mediaCallback)
                            _metadata.value = mediaController?.metadata
                            updatePlaybackState(mediaController?.playbackState)
                            _mediaPackageName.value = mediaController?.packageName
                        }

                        recovered = true
                        return@launch
                    }
                }

                if (!recovered) {
                    Log.w("MediaPlayback", "😴 No session recovered, entering cooldown")
                    delay(60000) // Espera 1 minuto antes de reintentar
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