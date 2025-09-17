package com.example.bangkoktransitalarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.Geofence as GmsGeofence // Alias to avoid conflict

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    private val TAG = "GeofenceReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent)

        if (geofencingEvent == null) {
            Log.e(TAG, "GeofencingEvent is null.")
            return
        }

        if (geofencingEvent.hasError()) {
            val errorMessage = GeofenceStatusCodes
                .getStatusCodeString(geofencingEvent.errorCode)
            Log.e(TAG, errorMessage)
            return
        }

        // Get the transition type.
        val geofenceTransition = geofencingEvent.geofenceTransition

        // Test that the reported transition was of interest.
        if (geofenceTransition == GmsGeofence.GEOFENCE_TRANSITION_ENTER ||
            geofenceTransition == GmsGeofence.GEOFENCE_TRANSITION_EXIT) {

            // Get the geofences that were triggered.
            val triggeringGeofences = geofencingEvent.triggeringGeofences

            // Get the transition details as a String.
            val geofenceTransitionDetails = getGeofenceTransitionDetails(
                geofenceTransition,
                triggeringGeofences
            )

            Log.i(TAG, geofenceTransitionDetails)

            // Send notification and start alarm service
            val serviceIntent = Intent(context, AlarmService::class.java)
            serviceIntent.putExtra("geofenceTransitionDetails", geofenceTransitionDetails)
            context.startService(serviceIntent)

        } else {
            // Log the error. You should only receive TRANSITION_ENTER, TRANSITION_EXIT, and TRANSITION_DWELL.
            Log.e(TAG, "Invalid transition type: $geofenceTransition")
        }
    }

    private fun getGeofenceTransitionDetails(
        geofenceTransition: Int,
        triggeringGeofences: List<GmsGeofence>? // Make it nullable
    ): String {
        val geofenceTransitionString = when (geofenceTransition) {
            GmsGeofence.GEOFENCE_TRANSITION_ENTER -> "Entered"
            GmsGeofence.GEOFENCE_TRANSITION_EXIT -> "Exited"
            else -> "Unknown Transition"
        }

        val triggeringGeofencesIdsList = ArrayList<String>()
        triggeringGeofences?.forEach { geofence -> // Safe call and forEach
            triggeringGeofencesIdsList.add(geofence.requestId)
        }
        val triggeringGeofencesIdsString = triggeringGeofencesIdsList.joinToString(", ")

        return "$geofenceTransitionString: $triggeringGeofencesIdsString"
    }
}