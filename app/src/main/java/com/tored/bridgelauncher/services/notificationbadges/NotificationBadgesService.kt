package com.tored.bridgelauncher.services.notificationbadges

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class NotificationBadgesService : NotificationListenerService() {
    private val _notificationCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val notificationCounts = _notificationCounts.asStateFlow()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        _notificationCounts.value = _notificationCounts.value.toMutableMap().apply {
            put(pkg, getOrDefault(pkg, 0) + 1)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        _notificationCounts.value = _notificationCounts.value.toMutableMap().apply {
            val current = getOrDefault(pkg, 1)
            if (current <= 1) remove(pkg)
            else put(pkg, current - 1)
        }
    }
}