package com.tored.bridgelauncher.ui2.settings.sections.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.tored.bridgelauncher.ui2.shared.Btn
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.os.Build

@Composable
fun SettingsScreen2PermissionSectionContent(
    context: Context,
    modifier: Modifier
) {
    val locationGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    val phoneStateGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_PHONE_STATE
    ) == PackageManager.PERMISSION_GRANTED

    val extStorageGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_MEDIA_IMAGES
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    val calendarGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_CALENDAR
    ) == PackageManager.PERMISSION_GRANTED

    val contactsGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_CONTACTS
    ) == PackageManager.PERMISSION_GRANTED

    Column(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Btn(
            text = "Allow Bridge to listen to Notifications",
            contentColor = MaterialTheme.colors.onSurface,
            onClick = {
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                context.startActivity(intent)
            }
        )

        if (!locationGranted) {
            Btn(
                text = "Allow Location Access (GPS)",
                contentColor = MaterialTheme.colors.onSurface,
                onClick = {
                    ActivityCompat.requestPermissions(
                        context as Activity,
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                        1001
                    )
                }
            )
        }

        if (!phoneStateGranted) {
            Btn(
                text = "Allow Phone State Access",
                contentColor = MaterialTheme.colors.onSurface,
                onClick = {
                    ActivityCompat.requestPermissions(
                        context as Activity,
                        arrayOf(Manifest.permission.READ_PHONE_STATE),
                        1002
                    )
                }
            )
        }

        if (!calendarGranted) {
            Btn(
                text = "Allow Calendar Access",
                contentColor = MaterialTheme.colors.onSurface,
                onClick = {
                    ActivityCompat.requestPermissions(
                        context as Activity,
                        arrayOf(Manifest.permission.READ_CALENDAR),
                        1004
                    )
                }
            )
        }

        if (!contactsGranted) {
            Btn(
                text = "Allow Contacts Access",
                contentColor = MaterialTheme.colors.onSurface,
                onClick = {
                    ActivityCompat.requestPermissions(
                        context as Activity,
                        arrayOf(Manifest.permission.READ_CONTACTS),
                        1005
                    )
                }
            )
        }

        if (!extStorageGranted) {
            val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_IMAGES
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }

            Btn(
                text = "Allow Image Storage Access",
                contentColor = MaterialTheme.colors.onSurface,
                onClick = {
                    ActivityCompat.requestPermissions(
                        context as Activity,
                        arrayOf(permission),
                        1003
                    )
                }
            )
        }
    }
}