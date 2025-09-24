package com.example.bangkoktransitalarm

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import com.example.bangkoktransitalarm.data.StationDataLoader
import android.widget.EditText
import androidx.recyclerview.widget.RecyclerView
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.bangkoktransitalarm.data.Station
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.widget.Toast
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.widget.ImageButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import androidx.preference.PreferenceManager

import org.osmdroid.config.Configuration
import org.osmdroid.views.MapView
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import android.preference.PreferenceManager as AndroidPreferenceManager // To avoid conflict with androidx.preference.PreferenceManager

import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.ConnectionResult


class MainActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var searchEditText: EditText
    private lateinit var searchResultsRecyclerView: RecyclerView
    private lateinit var stationDataLoader: StationDataLoader
    private var allStations: List<Station> = emptyList()
    private lateinit var searchAdapter: SearchResultsAdapter
    private lateinit var settingsButton: ImageButton
    private lateinit var startMonitoringFab: FloatingActionButton

    private lateinit var geofencingClient: GeofencingClient
    private var geofencePendingIntent: PendingIntent? = null

    private var selectedStation: Station? = null

    private val LOCATION_PERMISSION_REQUEST_CODE = 1
    private val ACTIVITY_RECOGNITION_PERMISSION_REQUEST_CODE = 2
    private val PLAY_SERVICES_RESOLUTION_REQUEST = 9000

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize osmdroid configuration
        Configuration.getInstance().load(applicationContext, AndroidPreferenceManager.getDefaultSharedPreferences(applicationContext))

        setContentView(R.layout.activity_main)

        mapView = findViewById(R.id.map)
        searchEditText = findViewById(R.id.searchEditText)
        searchResultsRecyclerView = findViewById(R.id.searchResultsRecyclerView)
        settingsButton = findViewById(R.id.settingsButton)
        startMonitoringFab = findViewById(R.id.startMonitoringFab)

        stationDataLoader = StationDataLoader(this)
        allStations = stationDataLoader.loadStations() ?: emptyList()

        setupMap()

        if (checkPlayServices()) {
            geofencingClient = LocationServices.getGeofencingClient(this)
        } else {
            Toast.makeText(this, "Google Play Services not available. Geofencing may not work.", Toast.LENGTH_LONG).show()
        }

        setupSearch()
        requestLocationPermissions()
        requestActivityRecognitionPermission()

        settingsButton.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        startMonitoringFab.setOnClickListener {
            selectedStation?.let {
                addGeofence(it)
            } ?: Toast.makeText(this, "Please select a station first", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkPlayServices(): Boolean {
        val googleApiAvailability = GoogleApiAvailability.getInstance()
        val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(this)
        if (resultCode != ConnectionResult.SUCCESS) {
            if (googleApiAvailability.isUserResolvableError(resultCode)) {
                googleApiAvailability.getErrorDialog(this, resultCode, PLAY_SERVICES_RESOLUTION_REQUEST)?.show()
            } else {
                Toast.makeText(this, "This device is not supported for Google Play Services.", Toast.LENGTH_LONG).show()
                // finish() // Do not finish, allow app to run without geofencing
            }
            return false
        }
        return true
    }

    private fun setupMap() {
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setBuiltInZoomControls(true)
        mapView.setMultiTouchControls(true)

        val mapController = mapView.controller
        mapController.setZoom(10.0)
        // Move camera to Bangkok
        val bangkok = GeoPoint(13.736717, 100.523186)
        mapController.setCenter(bangkok)

        addStationMarkers()
    }

    private fun addStationMarkers() {
        val markerIcon = AppCompatResources.getDrawable(this, R.drawable.ic_station_marker)
        for (station in allStations) {
            val geoPoint = GeoPoint(station.geoLat.toDouble(), station.geoLng.toDouble())
            val marker = Marker(mapView)
            marker.position = geoPoint
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            marker.title = station.nameEng
            marker.snippet = station.line
            markerIcon?.constantState?.newDrawable()?.mutate()?.let { drawable ->
                marker.icon = drawable
            }
            marker.setOnMarkerClickListener { m, mv ->
                Toast.makeText(this, "Selected: ${m.title} (${m.snippet})", Toast.LENGTH_LONG).show()
                selectedStation = allStations.find { it.stationId == station.stationId }
                true // Consume the event
            }
            mapView.overlays.add(marker)
        }
        mapView.invalidate() // Refresh the map to show markers
    }

    private fun setupSearch() {
        searchAdapter = SearchResultsAdapter(emptyList()) { station ->
            // Handle station selection from search results
            val geoPoint = GeoPoint(station.geoLat.toDouble(), station.geoLng.toDouble())
            mapView.controller.animateTo(geoPoint)
            mapView.controller.setZoom(15.0)
            searchEditText.setText(station.nameEng)
            searchResultsRecyclerView.visibility = View.GONE
            Toast.makeText(this, "Selected: ${station.nameEng} (${station.line})", Toast.LENGTH_LONG).show()
            selectedStation = station
        }
        searchResultsRecyclerView.layoutManager = LinearLayoutManager(this)
        searchResultsRecyclerView.adapter = searchAdapter

        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString()
                if (query.isNotEmpty()) {
                    val filteredStations = allStations.filter {
                        it.nameEng.contains(query, ignoreCase = true) ||
                        it.nameThai.contains(query, ignoreCase = true)
                    }
                    searchAdapter.updateData(filteredStations)
                    searchResultsRecyclerView.visibility = View.VISIBLE
                } else {
                    searchResultsRecyclerView.visibility = View.GONE
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun requestLocationPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            // Permissions already granted
            // For osmdroid, my location overlay needs to be enabled
            // val myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), mapView) // Requires GpsMyLocationProvider
            // myLocationOverlay.enableMyLocation()
            // mapView.overlays.add(myLocationOverlay)
        }
    }

    private fun requestActivityRecognitionPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACTIVITY_RECOGNITION),
                    ACTIVITY_RECOGNITION_PERMISSION_REQUEST_CODE
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED) {
                        // Enable my location overlay if needed
                        // val myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), mapView)
                        // myLocationOverlay.enableMyLocation()
                        // mapView.overlays.add(myLocationOverlay)
                    }
                } else {
                    Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
                }
            }
            ACTIVITY_RECOGNITION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Activity Recognition permission granted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Activity Recognition permission denied", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun addGeofence(station: Station) {
        if (!checkPlayServices()) {
            Toast.makeText(this, "Google Play Services not available. Cannot add geofence.", Toast.LENGTH_SHORT).show()
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "Location permissions not granted. Cannot add geofence.", Toast.LENGTH_SHORT).show()
            requestLocationPermissions()
            return
        }

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
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

        geofencePendingIntent?.cancel() // Cancel any existing geofence

        geofencePendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            Intent(this, GeofenceBroadcastReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        geofencingClient.addGeofences(geofencingRequest, geofencePendingIntent!!).run {
            addOnSuccessListener {
                Toast.makeText(this@MainActivity, "Geofence added for ${station.nameEng}", Toast.LENGTH_SHORT).show()
                sharedPreferences.edit().putString("last_selected_station_id", station.stationId).apply() // Save selected station ID
            }
            addOnFailureListener { e ->
                Toast.makeText(this@MainActivity, "Failed to add geofence: ${e.message}", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }
}
