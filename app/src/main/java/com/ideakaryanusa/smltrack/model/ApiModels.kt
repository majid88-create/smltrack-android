package com.ideakaryanusa.smltrack.model

import com.google.gson.annotations.SerializedName

/**
 * CATATAN PENTING:
 * Nama field di bawah ini disusun berdasarkan penelusuran string di dalam APK asli
 * (nama field model: username, password, latitude, longitude, accuracy, altitude,
 * heading, speed, timestamp, deviceId, token). Ini kemungkinan besar sudah benar,
 * TAPI belum diverifikasi langsung terhadap response nyata dari server.
 *
 * Sebelum dipakai produksi: coba login manual via curl/Postman dulu pakai akun asli,
 * lalu cocokkan bentuk JSON response-nya dengan LoginResponse di bawah. Kalau ada
 * field yang beda (misal nested di dalam "data": {...}), sesuaikan class ini.
 */

data class LoginRequest(
    @SerializedName("username") val username: String,
    @SerializedName("password") val password: String,
    @SerializedName("deviceId") val deviceId: String
)

data class LoginResponse(
    @SerializedName("token") val token: String?,
    @SerializedName("user") val user: UserDto?
)

data class UserDto(
    @SerializedName("id") val id: String?,
    @SerializedName("name") val name: String?,
    @SerializedName("username") val username: String?,
    @SerializedName("role") val role: String?
)

data class TraceLogRequest(
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("accuracy") val accuracy: Float,
    @SerializedName("altitude") val altitude: Double,
    @SerializedName("heading") val heading: Float,
    @SerializedName("speed") val speed: Float,
    @SerializedName("timestamp") val timestamp: String,
    @SerializedName("deviceId") val deviceId: String
)
