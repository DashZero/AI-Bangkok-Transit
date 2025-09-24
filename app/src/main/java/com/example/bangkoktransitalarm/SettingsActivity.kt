package com.example.bangkoktransitalarm

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    class SettingsFragment : PreferenceFragmentCompat() {

        private val ringtonePickerLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
            val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            val uriString = uri?.toString().orEmpty()
            PreferenceManager.getDefaultSharedPreferences(requireContext())
                .edit()
                .putString(KEY_ALARM_SOUND, uriString)
                .apply()
            updateRingtoneSummary(uriString)
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)
            findPreference<Preference>(KEY_ALARM_SOUND)?.let { preference ->
                preference.setOnPreferenceClickListener {
                    launchRingtonePicker()
                    true
                }
            }
            val currentUri = PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getString(KEY_ALARM_SOUND, null)
            if (currentUri.isNullOrEmpty()) {
                val defaultUri = Settings.System.DEFAULT_ALARM_ALERT_URI?.toString().orEmpty()
                PreferenceManager.getDefaultSharedPreferences(requireContext())
                    .edit()
                    .putString(KEY_ALARM_SOUND, defaultUri)
                    .apply()
                updateRingtoneSummary(defaultUri)
            } else {
                updateRingtoneSummary(currentUri)
            }
        }

        private fun launchRingtonePicker() {
            val context = requireContext()
            val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
            val existingUri = sharedPrefs.getString(KEY_ALARM_SOUND, null)
                ?.takeIf { it.isNotEmpty() }
                ?.let(Uri::parse)
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                putExtra(
                    RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                    existingUri ?: Settings.System.DEFAULT_ALARM_ALERT_URI
                )
            }
            ringtonePickerLauncher.launch(intent)
        }

        private fun updateRingtoneSummary(uriString: String?) {
            val preference = findPreference<Preference>(KEY_ALARM_SOUND) ?: return
            val context = context ?: return
            if (uriString.isNullOrEmpty()) {
                preference.summary = context.getString(R.string.settings_alarm_sound_default)
                return
            }
            val ringtone = runCatching {
                RingtoneManager.getRingtone(context, Uri.parse(uriString))
            }.getOrNull()
            preference.summary = ringtone?.getTitle(context)
                ?: context.getString(R.string.settings_alarm_sound_unknown)
        }

        companion object {
            private const val KEY_ALARM_SOUND = "alarm_sound"
        }
    }
}
