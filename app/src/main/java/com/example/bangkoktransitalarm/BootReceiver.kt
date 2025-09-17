package com.example.bangkoktransitalarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import android.app.PendingIntent
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.example.bangkoktransitalarm.data.StationDataLoader
import com.example.bangkoktransitalarm.data.Station
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat


class BootReceiver : BroadcastReceiver() {

    private val TAG = "BootReceiver" // Renamed to satisfy linter

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Boot completed, attempting to re-register geofences.")

            val sharedPreferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
            val lastSelectedStationId = sharedPreferences.getString("last_selected_station_id", null)

            if (lastSelectedStationId != null) {
                val stationDataLoader = StationDataLoader(context)
                val allStations = stationDataLoader.loadStations() ?: emptyList()
                val lastSelectedStation = allStations.find { it.stationId == lastSelectedStationId }

                lastSelectedStation?.let { station ->
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED) {
                        reRegisterGeofence(context, station)
                    } else {
                        Log.e(TAG, "Location permissions not granted, cannot re-register geofence.")
                    }
                } ?: Log.e(TAG, "Last selected station not found in data.")
            } else {
                Log.d(TAG, "No last selected station found, no geofence to re-register.")
            }
        }
    }

    private fun reRegisterGeofence(context: Context, station: Station) {
        val geofencingClient: GeofencingClient = LocationServices.getGeofencingClient(context)
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val geofenceRadius = sharedPreferences.getString("geofence_radius", "250")?.toFloatOrNull() ?: 250f

        val geofence = Geofence.Builder()
            .setRequestId(station.stationId)
            .setCircularRegion(
                station.geoLat.toDouble(),
                station.geoLng.toDouble(),
                geofenceRadius
            )
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
            .build()

        val geofencingRequest = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        val geofencePendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, GeofenceBroadcastReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Add permission check here
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            geofencingClient.addGeofences(geofencingRequest, geofencePendingIntent).run {
                addOnSuccessListener {
                    Log.d(TAG, "Geofence re-registered for ${station.nameEng} on boot.")
                }
                addOnFailureListener { e ->
                    Log.e(TAG, "Failed to re-register geofence on boot: ${e.message}", e)
                }
            }
        } else {
            Log.e(TAG, "ACCESS_FINE_LOCATION permission not granted. Cannot re-register geofence.")
        }
    }
}