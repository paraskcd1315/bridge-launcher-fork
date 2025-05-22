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

    val extStorageGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_EXTERNAL_STORAGE
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

        if (!extStorageGranted) {
            Btn(
                text = "Allow Storage Access",
                contentColor = MaterialTheme.colors.onSurface,
                onClick = {
                    ActivityCompat.requestPermissions(
                        context as Activity,
                        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                        1003
                    )
                }
            )
        }
    }
}