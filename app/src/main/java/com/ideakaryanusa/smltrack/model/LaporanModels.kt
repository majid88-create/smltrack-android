package com.ideakaryanusa.smltrack.model

import com.google.gson.annotations.SerializedName

/**
 * CATATAN: field di bawah disusun dari string yang ditemukan di libapp.so
 * versi 1.0.4 (title, description, category, status, image, location,
 * latitude, longitude, project_id, user_id, created_at, updated_at).
 * Sebagian field kelihatan camelCase, sebagian snake_case - itu memang
 * campuran yang terlihat di app aslinya, bukan salah ketik.
 *
 * WAJIB dites ke server asli dulu (lihat README bagian "Laporan / Schedule /
 * Recap - perlu verifikasi") sebelum dipakai beneran, terutama:
 * - apakah response list/detail dibungkus di dalam key "data" atau tidak
 * - nama field multipart saat upload foto (di sini saya pakai "image")
 */

data class Laporan(
    @SerializedName("id") val id: String?,
    @SerializedName("title") val title: String?,
    @SerializedName("description") val description: String?,
    @SerializedName("category") val category: String?,
    @SerializedName("status") val status: String?,
    @SerializedName("image") val imageUrl: String?,
    @SerializedName("location") val location: String?,
    @SerializedName("latitude") val latitude: Double?,
    @SerializedName("longitude") val longitude: Double?,
    @SerializedName("project_id") val projectId: String?,
    @SerializedName("user_id") val userId: String?,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("updated_at") val updatedAt: String?
)

data class LaporanListResponse(
    @SerializedName("data") val data: List<Laporan>?
)

data class LaporanDetailResponse(
    @SerializedName("data") val data: Laporan?
)
