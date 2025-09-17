package com.example.bangkoktransitalarm.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.IOException

class StationDataLoader(private val context: Context) {

    fun loadStations(): List<Station>? {
        val jsonString: String
        try {
            jsonString = context.assets.open("stations.json").bufferedReader().use { it.readText() }
        } catch (ioException: IOException) {
            ioException.printStackTrace()
            return null
        }

        val listStationType = object : TypeToken<List<Station>>() {}.type
        return Gson().fromJson(jsonString, listStationType)
    }
}