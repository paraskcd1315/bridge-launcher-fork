package com.tored.bridgelauncher.services.notificationbadges

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class NotificationBadgesService : NotificationListenerService() {
    companion object {
        var instance: NotificationBadgesService? = null
    }

    private val _notificationCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val notificationCounts = _notificationCounts.asStateFlow()

    // Service coroutine scope for launching jobs
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Log.d("NotifService", "NotificationBadgesService created")
        instance = this
        serviceScope.launch {
            repeat(5) { attempt ->
                delay(300)
                try {
                    val actives = activeNotifications
                    if (actives != null) {
                        Log.d("NotifService", "Attempt #$attempt: ${actives.size} active notifs")
                        recalculateActiveNotifications()
                        return@launch
                    } else {
                        Log.d("NotifService", "Attempt #$attempt: activeNotifications is null")
                    }
                } catch (e: SecurityException) {
                    Log.w("NotifService", "Permission issue while reading notifications", e)
                    return@launch
                }
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        recalculateActiveNotifications()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        recalculateActiveNotifications()
    }

    private fun recalculateActiveNotifications() {
        try {
            val actives = activeNotifications
            val counts = mutableMapOf<String, Int>()

            actives?.forEach { sbn ->
                val pkg = sbn.packageName
                counts[pkg] = counts.getOrDefault(pkg, 0) + 1
            }

            _notificationCounts.value = counts
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        instance = null
        serviceScope.cancel()
    }
}