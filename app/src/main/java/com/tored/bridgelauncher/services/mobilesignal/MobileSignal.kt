package com.tored.bridgelauncher.services.mobilesignal

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MobileSignal(context: Context) {
    private val _mobileSignalStrength = MutableStateFlow(-120)
    val mobileSignalStrength = _mobileSignalStrength.asStateFlow()

    private val _networkType = MutableStateFlow("UNKNOWN")
    val networkType = _networkType.asStateFlow()

    private val _mobileSignalLevel = MutableStateFlow(0)
    val mobileSignalLevel = _mobileSignalLevel.asStateFlow()

    init {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        var signalStrength = -120
        try {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    val allCellInfo = telephonyManager.allCellInfo
                    val cellInfo = allCellInfo.firstOrNull { it.isRegistered }
                    signalStrength = when (cellInfo) {
                        is android.telephony.CellInfoGsm -> cellInfo.cellSignalStrength.dbm
                        is android.telephony.CellInfoCdma -> cellInfo.cellSignalStrength.dbm
                        is android.telephony.CellInfoLte -> cellInfo.cellSignalStrength.dbm
                        is android.telephony.CellInfoWcdma -> cellInfo.cellSignalStrength.dbm
                        is android.telephony.CellInfoNr -> cellInfo.cellSignalStrength.dbm
                        else -> -120
                    }
                } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    val signal = telephonyManager.signalStrength
                    if (signal != null) {
                        signalStrength = signal.level * 20 - 100 // escala aproximada
                    }
                } else {
                    // Compatibilidad con API menores a 28 - no se puede acceder directamente al nivel
                    // Intenta usar PhoneStateListener en versiones futuras si es necesario
                    signalStrength = -120
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }

        _mobileSignalStrength.value = signalStrength
        _mobileSignalLevel.value = when {
            signalStrength >= -70 -> 5
            signalStrength >= -85 -> 4
            signalStrength >= -100 -> 3
            signalStrength >= -110 -> 2
            signalStrength >= -120 -> 1
            else -> 0
        }

        val networkType = when (telephonyManager.networkType) {
            TelephonyManager.NETWORK_TYPE_GPRS,
            TelephonyManager.NETWORK_TYPE_EDGE -> "2G"
            TelephonyManager.NETWORK_TYPE_UMTS,
            TelephonyManager.NETWORK_TYPE_HSDPA,
            TelephonyManager.NETWORK_TYPE_HSUPA,
            TelephonyManager.NETWORK_TYPE_HSPA -> "3G"
            TelephonyManager.NETWORK_TYPE_LTE -> "4G"
            TelephonyManager.NETWORK_TYPE_NR -> "5G"
            else -> "UNKNOWN"
        }
        _networkType.value = networkType
    }
}