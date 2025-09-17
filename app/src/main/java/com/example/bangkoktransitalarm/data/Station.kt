package com.example.bangkoktransitalarm.data

import com.google.gson.annotations.SerializedName

data class Station(
    @SerializedName("stationId") val stationId: String,
    @SerializedName("name") val nameThai: String,
    @SerializedName("nameEng") val nameEng: String,
    @SerializedName("transportationId") val transportationId: String,
    @SerializedName("subdistrictId") val subdistrictId: String,
    @SerializedName("geoLat") val geoLat: String,
    @SerializedName("geoLng") val geoLng: String,
    @SerializedName("line") val line: String
)