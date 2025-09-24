# Bangkok Transit Alarm

Bangkok Transit Alarm is a native Android application that keeps sleepy or distracted commuters from missing their stop on Bangkok's rail network. Search for any BTS, MRT, or ARL station, drop a geofence on the map, and the app will wake you with a loud, looping alarm and vibration as soon as you approach the station.

## Features

- OpenStreetMap-based map (via osmdroid) with markers for every supported station in Bangkok.
- Instant search across Thai and English station names, with tap-to-center navigation.
- One-tap geofence registration backed by Google Play Services to alert on enter/exit events.
- Foreground `AlarmService` that plays a configurable ringtone, vibrates the device, and keeps a persistent notification until dismissed.
- Settings screen for fine-tuning geofence radius, alarm sound, vibration, and volume; selections persist between sessions.
- Boot receiver that re-registers the last monitored station after a device restart so commuters stay protected.

## Tech Stack & Key Libraries

- Kotlin + Android SDK (minSdk 21, targetSdk 36)
- AndroidX (AppCompat, RecyclerView, Preference, Lifecycle)
- Google Play Services Location for geofencing APIs
- osmdroid for rendering OpenStreetMap tiles
- Gson for loading bundled station metadata
- Material Components for UI elements

## Project Structure

```text
AI-Bangkok-Transit/
├── app/                  # Android application module
│   ├── src/main/java/    # Kotlin sources
│   │   └── com/example/bangkoktransitalarm/
│   │       ├── MainActivity.kt            # Map, search, geofence control
│   │       ├── AlarmService.kt            # Foreground alarm playback
│   │       ├── GeofenceBroadcastReceiver.kt
│   │       ├── BootReceiver.kt            # Re-registers geofence on boot
│   │       ├── AlarmActivity.kt           # UI to stop/snooze the alarm
│   │       ├── SearchResultsAdapter.kt
│   │       └── data/                      # Station model + JSON loader
│   ├── src/main/assets/stations.json      # BTS/MRT/ARL station catalog
│   ├── src/main/res/layout/               # Activity layouts
│   └── src/main/res/xml/preferences.xml   # Settings definitions
├── backend/              # Prototype scripts (not used by the Android app)
└── frontend/             # Misc tooling assets (not used by the Android app)
```

## Getting Started

1. Install [Android Studio](https://developer.android.com/studio) Flamingo or newer with Android SDK 36.
2. Clone this repository and open the project in Android Studio:

   ```bash
   git clone https://github.com/<your-account>/AI-Bangkok-Transit.git
   ```

3. Let Android Studio sync Gradle dependencies. No additional API keys are required because osmdroid serves OSM tiles.
4. Connect a physical Android device with Google Play Services (strongly recommended). Geofencing APIs are unreliable on emulators.
5. Build & run the `app` configuration.

## Using the App

- Pan/zoom the map to explore Bangkok. Station markers show English names and line colors in the snippet.
- Use the search bar to filter stations by Thai or English name, then tap a result to center the map and select that station.
- Tap the play Floating Action Button to start monitoring the selected station. A toast confirms the geofence registration.
- When you enter or exit the geofenced radius, the foreground alarm starts and launches the alarm screen so you can stop or snooze it.

## Settings

Access the settings screen via the top-right gear button. Available options are defined in `app/src/main/res/xml/preferences.xml`:

- **Geofence radius** (`100–500 m` suggested) – controls how early the alert fires.
- **Alert one station before** – reserved for future logic (preference is stored but not yet applied).
- **Vibrate alarm** – toggles vibration when the alarm fires.
- **Alarm sound** – choose any ringtone for the alarm service.
- **Alarm volume** – scales the alarm stream volume before playback.

Preference values are stored in `SharedPreferences` and reused when the alarm restarts after boot.

## Permissions

The application requests several runtime permissions in line with its functionality:

- `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, and `ACCESS_BACKGROUND_LOCATION` – required for geofencing.
- `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_LOCATION` – needed to run the alarm as a foreground service.
- `RECEIVE_BOOT_COMPLETED` – allows the app to re-register geofences after a restart.
- `VIBRATE` – enables haptic feedback during the alarm.
- (Android 10+) `ACTIVITY_RECOGNITION` – currently reserved for potential motion-aware optimisations.

Grant all requested permissions to ensure alarms trigger reliably, especially while the app is in the background.

## Station Data

The bundled `stations.json` file lists Bangkok’s BTS, MRT, and ARL stations with both Thai and English names, line identifiers, and WGS84 coordinates. Update this file if new stations open:

1. Replace or extend the JSON entries in `app/src/main/assets/stations.json`.
2. Ensure every station has a unique `stationId` (`String`) and valid latitude/longitude strings.
3. Rebuild the app; the dataset loads at startup via `StationDataLoader`.

## Geofencing Lifecycle

- `MainActivity` registers a circular geofence around the chosen station. The default radius is `250 m`, configurable in Settings.
- `GeofenceBroadcastReceiver` listens for enter/exit transitions and starts `AlarmService` with event details.
- `AlarmService` promotes itself to a foreground service, plays the selected ringtone on loop, vibrates (optional), and launches `AlarmActivity` so users can stop or snooze the alert.
- `BootReceiver` restores the last selected station when the device boots, provided location permissions are still granted.

## Known Limitations & Next Steps

- The “Alert one station before” preference is not yet wired to geofencing logic.
- Activity recognition data is not consumed; future versions could use it to reduce false alarms when the user is already stationary.
- Background geofencing depends on device-specific battery optimisation settings; document exemptions for OEM variants as needed.
- Consider persisting multiple stations or daily routes, and adding notification-only alerts as a gentler option before the full alarm.

## Troubleshooting

- **Geofence fails to add**: ensure Google Play Services is available and all location permissions (including background) are granted.
- **No alarm sound**: verify the chosen ringtone exists, raise the alarm volume slider, and ensure the device is not in Do Not Disturb.
- **Map tiles missing**: osmdroid respects the app’s user-agent. Confirm `Configuration.getInstance().load(...)` runs before `setContentView` (already handled in `MainActivity`).

## Contributing

Issues and pull requests are welcome. Please explain the scenario you are solving and include repro steps or testing notes for any behaviour changes.

## License

No explicit license file is present. Treat the contents as "all rights reserved" unless a license is added in the future.
