package com.ideakaryanusa.smltrack.model

import com.google.gson.annotations.SerializedName

/**
 * Model untuk komunikasi dengan backend SENDIRI (Apps Script Web App),
 * bukan server SML asli. Semua request membawa "secret" sebagai kunci.
 */

// Kirim 1 titik GPS
data class BackendTraceRequest(
    @SerializedName("secret") val secret: String,
    @SerializedName("action") val action: String = "trace",
    @SerializedName("username") val username: String,
    @SerializedName("deviceId") val deviceId: String,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("accuracy") val accuracy: Float,
    @SerializedName("speed") val speed: Float,
    @SerializedName("timestamp") val timestamp: String,
    @SerializedName("projectId") val projectId: String?,   // area terdaftar (hasil deteksi HP), null kalau di luar
    @SerializedName("projectName") val projectName: String?
)

// Kirim banyak titik sekaligus (saat habis offline)
data class BackendTraceBatchRequest(
    @SerializedName("secret") val secret: String,
    @SerializedName("action") val action: String = "trace_batch",
    @SerializedName("username") val username: String,
    @SerializedName("deviceId") val deviceId: String,
    @SerializedName("points") val points: List<BackendTracePoint>
)

data class BackendTracePoint(
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("accuracy") val accuracy: Float,
    @SerializedName("speed") val speed: Float,
    @SerializedName("timestamp") val timestamp: String,
    @SerializedName("projectId") val projectId: String?,
    @SerializedName("projectName") val projectName: String?
)

data class BackendResponse(
    @SerializedName("status") val status: String?,
    @SerializedName("message") val message: String?
)

// Response saat minta daftar geofence
data class GeofenceResponse(
    @SerializedName("status") val status: String?,
    @SerializedName("data") val data: List<GeofenceAreaDto>?
)

data class GeofenceAreaDto(
    @SerializedName("projectId") val projectId: String?,
    @SerializedName("projectName") val projectName: String?,
    @SerializedName("polygon") val polygon: List<LatLngDto>?
)

data class LatLngDto(
    @SerializedName("lat") val lat: Double,
    @SerializedName("lng") val lng: Double
)
