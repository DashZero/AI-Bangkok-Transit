package com.example.bangkoktransitalarm

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.bangkoktransitalarm.data.Station
import com.example.bangkoktransitalarm.data.StationDataLoader
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Tasks
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import android.preference.PreferenceManager as AndroidPreferenceManager
import kotlin.math.max

class MainActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var searchEditText: EditText
    private lateinit var searchResultsRecyclerView: RecyclerView
    private lateinit var stationDataLoader: StationDataLoader
    private var allStations: List<Station> = emptyList()
    private lateinit var searchAdapter: SearchResultsAdapter
    private lateinit var settingsButton: ImageView
    private lateinit var startMonitoringFab: ExtendedFloatingActionButton
    private lateinit var searchCard: MaterialCardView
    private lateinit var settingsCard: MaterialCardView
    private lateinit var searchResultsCard: MaterialCardView
    private lateinit var bottomActionCard: MaterialCardView

    private lateinit var geofencingClient: GeofencingClient
    private var geofencePendingIntent: PendingIntent? = null

    private var selectedStation: Station? = null
    private var systemBarInsets: Insets = Insets.NONE
    private var selectedMarker: Marker? = null
    private val markerByStationId = mutableMapOf<String, Marker>()
    private var defaultMarkerIcon: Drawable? = null
    private var selectedMarkerIcon: Drawable? = null

    private val LOCATION_PERMISSION_REQUEST_CODE = 1
    private val ACTIVITY_RECOGNITION_PERMISSION_REQUEST_CODE = 2
    private val BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE = 3
    private val PLAY_SERVICES_RESOLUTION_REQUEST = 9000

    private val TAG = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().load(
            applicationContext,
            AndroidPreferenceManager.getDefaultSharedPreferences(applicationContext)
        )

        setContentView(R.layout.activity_main)

        mapView = findViewById(R.id.map)
        searchCard = findViewById(R.id.searchCard)
        settingsCard = findViewById(R.id.settingsCard)
        searchResultsCard = findViewById(R.id.searchResultsCard)
        bottomActionCard = findViewById(R.id.bottomActionCard)
        searchEditText = findViewById(R.id.searchEditText)
        searchResultsRecyclerView = findViewById(R.id.searchResultsRecyclerView)
        settingsButton = findViewById(R.id.settingsButton)
        startMonitoringFab = findViewById(R.id.startMonitoringFab)

        defaultMarkerIcon = AppCompatResources.getDrawable(this, R.drawable.ic_station_marker)
        selectedMarkerIcon = AppCompatResources.getDrawable(this, R.drawable.ic_station_marker_selected)

        val rootContainer = findViewById<ConstraintLayout>(R.id.mainContainer)
        ViewCompat.setOnApplyWindowInsetsListener(rootContainer) { _, insets ->
            systemBarInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            applyWindowInsets()
            insets
        }
        ViewCompat.requestApplyInsets(rootContainer)

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

        val openSettings = View.OnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        settingsButton.setOnClickListener(openSettings)
        settingsCard.setOnClickListener(openSettings)

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
            }
            return false
        }
        return true
    }

    private fun setupMap() {
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setBuiltInZoomControls(true)
        mapView.setMultiTouchControls(true)
        applyWindowInsets()

        val mapController = mapView.controller
        mapController.setZoom(10.0)
        mapController.setCenter(GeoPoint(13.736717, 100.523186))

        addStationMarkers()
    }

    private fun addStationMarkers() {
        markerByStationId.clear()
        val defaultIcon = defaultMarkerIcon

        for (station in allStations) {
            val geoPoint = GeoPoint(station.geoLat.toDouble(), station.geoLng.toDouble())
            val marker = Marker(mapView).apply {
                position = geoPoint
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = station.nameEng
                snippet = station.line
                icon = freshDrawable(defaultIcon)
                setOnMarkerClickListener { clickedMarker, _ ->
                    handleStationSelection(station, triggeredFromMarker = true)
                    clickedMarker.showInfoWindow()
                    true
                }
            }
            mapView.overlays.add(marker)
            markerByStationId[station.stationId] = marker
        }
        mapView.invalidate()
    }

    private fun setupSearch() {
        searchAdapter = SearchResultsAdapter(emptyList()) { station ->
            handleStationSelection(station, triggeredFromMarker = false)
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
                    searchResultsCard.visibility = if (filteredStations.isNotEmpty()) View.VISIBLE else View.GONE
                } else {
                    searchAdapter.updateData(emptyList())
                    searchResultsCard.visibility = View.GONE
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun handleStationSelection(station: Station, triggeredFromMarker: Boolean) {
        selectedStation = station
        val geoPoint = GeoPoint(station.geoLat.toDouble(), station.geoLng.toDouble())
        val desiredZoom = max(mapView.zoomLevelDouble, 16.0)
        mapView.controller.animateTo(geoPoint, desiredZoom, 1000L)

        searchEditText.setText(station.nameEng)
        searchResultsCard.visibility = View.GONE

        updateSelectedMarker(markerByStationId[station.stationId])

        Toast.makeText(
            this,
            getString(R.string.selected_station_message, station.nameEng, station.line),
            Toast.LENGTH_LONG
        ).show()
    }

    private fun updateSelectedMarker(marker: Marker?) {
        if (selectedMarker == marker) {
            marker?.showInfoWindow()
            mapView.invalidate()
            return
        }

        selectedMarker?.let {
            it.icon = freshDrawable(defaultMarkerIcon)
            it.closeInfoWindow()
        }

        selectedMarker = marker

        selectedMarker?.let {
            it.icon = freshDrawable(selectedMarkerIcon)
            it.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            it.showInfoWindow()
        }

        mapView.invalidate()
    }

    private fun freshDrawable(base: Drawable?): Drawable? = base?.constantState?.newDrawable()?.mutate()

    private fun requestLocationPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            requestBackgroundLocationPermission()
        }
    }

    private fun requestBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                    BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE
                )
            }
        }
    }

    private fun requestActivityRecognitionPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                != PackageManager.PERMISSION_GRANTED
            ) {
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
                    requestBackgroundLocationPermission()
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

            BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Background location permission granted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Background location permission denied", Toast.LENGTH_SHORT).show()
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "Background location permission required for geofences.", Toast.LENGTH_LONG).show()
            requestBackgroundLocationPermission()
            return
        }

        if (!isLocationEnabled()) {
            Toast.makeText(this, "Location services are disabled. Please enable location and try again.", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
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

        val geofenceList = listOf(geofence)

        val geofencingRequest = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofences(geofenceList)
            .build()

        val previousGeofenceId = sharedPreferences.getString(PREF_LAST_GEOFENCE_ID, null)
        val removalTask = when {
            previousGeofenceId != null -> geofencingClient.removeGeofences(listOf(previousGeofenceId))
            geofencePendingIntent != null -> geofencingClient.removeGeofences(geofencePendingIntent!!)
            else -> Tasks.forResult(null)
        }

        removalTask.addOnCompleteListener {
            geofencePendingIntent?.cancel()

            geofencePendingIntent = PendingIntent.getBroadcast(
                this,
                0,
                Intent(this, GeofenceBroadcastReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            geofencingClient.addGeofences(geofencingRequest, geofencePendingIntent!!).run {
                addOnSuccessListener {
                    Toast.makeText(this@MainActivity, "Geofence added for ${station.nameEng}", Toast.LENGTH_SHORT).show()
                    sharedPreferences.edit()
                        .putString("last_selected_station_id", station.stationId)
                        .putString(PREF_LAST_GEOFENCE_ID, station.stationId)
                        .apply()
                }
                addOnFailureListener { e ->
                    val message = geofenceErrorMessage(e)
                    Toast.makeText(this@MainActivity, "Failed to add geofence: $message", Toast.LENGTH_LONG).show()
                    Log.e(TAG, "Failed to add geofence", e)
                }
            }
        }
    }

    private fun geofenceErrorMessage(exception: Exception): String {
        if (exception is ApiException) {
            return when (exception.statusCode) {
                GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE ->
                    "Location services are unavailable. Please ensure GPS or network location is enabled."

                GeofenceStatusCodes.GEOFENCE_TOO_MANY_GEOFENCES ->
                    "Too many geofences are active. Remove an existing one and try again."

                GeofenceStatusCodes.GEOFENCE_TOO_MANY_PENDING_INTENTS ->
                    "Too many geofence requests are pending. Try again in a moment."

                else -> GeofenceStatusCodes.getStatusCodeString(exception.statusCode)
            }
        }
        return exception.message ?: "Unknown error"
    }

    private fun isLocationEnabled(): Boolean {
        val manager = getSystemService(LocationManager::class.java)
        return manager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
            manager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
    }

    private fun applyWindowInsets() {
        if (!this::mapView.isInitialized ||
            !this::searchCard.isInitialized ||
            !this::settingsCard.isInitialized ||
            !this::bottomActionCard.isInitialized ||
            !this::searchResultsCard.isInitialized) {
            return
        }
        val bottomInset = systemBarInsets.bottom
        val topInset = systemBarInsets.top
        val mapBottomExtra = resources.getDimensionPixelSize(R.dimen.map_bottom_padding_extra)
        val bottomCardBase = resources.getDimensionPixelSize(R.dimen.bottom_card_margin)
        val searchTopBase = resources.getDimensionPixelSize(R.dimen.search_card_margin_top)
        val settingsTopBase = resources.getDimensionPixelSize(R.dimen.settings_card_margin_top)
        val settingsEndBase = resources.getDimensionPixelSize(R.dimen.settings_card_margin_end)
        val horizontalMargin = resources.getDimensionPixelSize(R.dimen.screen_horizontal_margin)

        bottomActionCard.updateLayoutParams<ConstraintLayout.LayoutParams> {
            bottomMargin = bottomInset + bottomCardBase
            marginStart = systemBarInsets.left + horizontalMargin
            marginEnd = systemBarInsets.right + horizontalMargin
        }

        searchCard.updateLayoutParams<ConstraintLayout.LayoutParams> {
            topMargin = topInset + searchTopBase
            marginStart = systemBarInsets.left + horizontalMargin
        }

        settingsCard.updateLayoutParams<ConstraintLayout.LayoutParams> {
            topMargin = topInset + settingsTopBase
            marginEnd = systemBarInsets.right + settingsEndBase
        }

        searchResultsCard.updateLayoutParams<ConstraintLayout.LayoutParams> {
            marginStart = systemBarInsets.left + horizontalMargin
            marginEnd = systemBarInsets.right + horizontalMargin
        }

        mapView.setPadding(systemBarInsets.left, topInset, systemBarInsets.right, bottomInset + mapBottomExtra)
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    companion object {
        private const val PREF_LAST_GEOFENCE_ID = "last_geofence_id"
    }
}
