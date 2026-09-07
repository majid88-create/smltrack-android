package com.ideakaryanusa.smltrack.model

import com.google.gson.annotations.SerializedName

/**
 * KONFIRMASI dari menangkap request asli (via DevTools browser saat login
 * berhasil di dashboard web): field-nya "Username" dan "Password" (huruf besar
 * di depan), TANPA deviceId. Password yang dikirim adalah hash SHA-256 dari
 * password asli - bukan teks polos. Ini beda total dari tebakan awal
 * (username/password/deviceId huruf kecil semua) yang disusun dari analisa
 * string di APK.
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
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("accuracy") val accuracy: Float,
    @SerializedName("altitude") val altitude: Double,
    @SerializedName("heading") val heading: Float,
    @SerializedName("speed") val speed: Float,
    @SerializedName("timestamp") val timestamp: String,
    @SerializedName("deviceId") val deviceId: String
)
