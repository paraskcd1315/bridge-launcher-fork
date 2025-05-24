package com.tored.bridgelauncher.services.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class LocationInfo(private val context: Context) {
    private val _latitude = MutableStateFlow<Double?>(null)
    val latitude = _latitude.asStateFlow()

    private val _longitude = MutableStateFlow<Double?>(null)
    val longitude = _longitude.asStateFlow()

    private var lastLatitude: Double? = null
    private var lastLongitude: Double? = null

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    private val locationCallback = object : com.google.android.gms.location.LocationCallback() {
        override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
            val location = result.lastLocation
            location?.let {
                Log.d("LocationInfo", "Received location: lat=${it.latitude}, lon=${it.longitude}")
                val newLat = it.latitude
                val newLon = it.longitude

                val shouldUpdate = lastLatitude == null || lastLongitude == null ||
                    distanceBetween(lastLatitude!!, lastLongitude!!, newLat, newLon) > 10000 // 10 km

                if (shouldUpdate) {
                    _latitude.value = newLat
                    _longitude.value = newLon
                    lastLatitude = newLat
                    lastLongitude = newLon
                }
            }
        }
    }

    fun startup() {
        Log.d("LocationInfo", "startup() called")
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            return
        }
        Log.d("LocationInfo", "Permission granted: requesting location")

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 60000L) // every 60s
            .setMinUpdateDistanceMeters(10000f) // only if moved 10km
            .build()

        fusedLocationClient.requestLocationUpdates(
            request,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun distanceBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000.0 // meters

        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)

        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

        return earthRadius * c
    }
}