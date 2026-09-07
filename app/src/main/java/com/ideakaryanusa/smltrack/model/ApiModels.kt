package com.ideakaryanusa.smltrack.model

import com.google.gson.annotations.SerializedName

/**
 * KONFIRMASI dari membaca main.dart.js versi web: backend ini pakai gaya
 * PascalCase untuk field JSON (contoh: Login pakai "Username"/"Password",
 * bukan "username"/"password"). Field TraceLog di bawah ini disesuaikan
 * mengikuti pola yang sama - "Latitude", "Longitude", dst - karena web app
 * tidak punya fitur kirim GPS sendiri (cuma baca/tampilkan), jadi tidak bisa
 * dikonfirmasi 100% seperti Login kemarin. Kalau masih gagal, app sekarang
 * akan menampilkan kode HTTP + pesan error asli di notifikasi, jadi lebih
 * mudah didiagnosis lagi.
 */

data class LoginRequest(
    @SerializedName("Username") val username: String,
    @SerializedName("Password") val passwordHash: String
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
    @SerializedName("Latitude") val latitude: Double,
    @SerializedName("Longitude") val longitude: Double,
    @SerializedName("Accuracy") val accuracy: Float,
    @SerializedName("Altitude") val altitude: Double,
    @SerializedName("Heading") val heading: Float,
    @SerializedName("Speed") val speed: Float,
    @SerializedName("Timestamp") val timestamp: String,
    @SerializedName("DeviceId") val deviceId: String
)
