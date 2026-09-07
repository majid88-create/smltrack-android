package com.ideakaryanusa.smltrack.model

import com.google.gson.annotations.SerializedName

// ---- Home ----
data class HomeResponse(
    @SerializedName("status") val status: String?,
    @SerializedName("data") val data: HomeData?
)

data class HomeData(
    @SerializedName("lastLat") val lastLat: Double?,
    @SerializedName("lastLng") val lastLng: Double?,
    @SerializedName("lastTime") val lastTime: String?,
    @SerializedName("lastArea") val lastArea: String?,
    @SerializedName("aktivitas") val aktivitas: List<AktivitasItem>?,
    @SerializedName("sites") val sites: List<SiteItem>?
)

data class AktivitasItem(
    @SerializedName("projectName") val projectName: String?,
    @SerializedName("jamMasuk") val jamMasuk: String?,
    @SerializedName("jamAkhir") val jamAkhir: String?
)

data class SiteItem(
    @SerializedName("projectName") val projectName: String?,
    @SerializedName("address") val address: String?,
    @SerializedName("role") val role: String?
)

// ---- Check in/out ----
data class CheckInOutRequest(
    @SerializedName("secret") val secret: String,
    @SerializedName("action") val action: String,   // "checkin" / "checkout"
    @SerializedName("username") val username: String,
    @SerializedName("latitude") val latitude: Double?,
    @SerializedName("longitude") val longitude: Double?,
    @SerializedName("projectId") val projectId: String?,
    @SerializedName("projectName") val projectName: String?,
    @SerializedName("address") val address: String?
)

// ---- Laporan ----
data class LaporanCreateRequest(
    @SerializedName("secret") val secret: String,
    @SerializedName("action") val action: String = "laporan",
    @SerializedName("username") val username: String,
    @SerializedName("projectId") val projectId: String?,
    @SerializedName("projectName") val projectName: String?,
    @SerializedName("tanggal") val tanggal: String,
    @SerializedName("deskripsi") val deskripsi: String,
    @SerializedName("photoBase64") val photoBase64: String?,
    @SerializedName("photoMime") val photoMime: String?
)

data class LaporanListResponse2(
    @SerializedName("status") val status: String?,
    @SerializedName("data") val data: List<LaporanItem>?
)

data class LaporanItem(
    @SerializedName("id") val id: String?,
    @SerializedName("projectName") val projectName: String?,
    @SerializedName("tanggal") val tanggal: String?,
    @SerializedName("deskripsi") val deskripsi: String?,
    @SerializedName("fileUrl") val fileUrl: String?,
    @SerializedName("status") val status: String?
)

// ---- Jadwal ----
data class JadwalResponse(
    @SerializedName("status") val status: String?,
    @SerializedName("data") val data: List<JadwalItem>?
)

data class JadwalItem(
    @SerializedName("tanggal") val tanggal: String?,
    @SerializedName("judul") val judul: String?,
    @SerializedName("projectName") val projectName: String?,
    @SerializedName("keterangan") val keterangan: String?
)

// ---- Recap ----
data class RecapResponse(
    @SerializedName("status") val status: String?,
    @SerializedName("data") val data: RecapData?
)

data class RecapData(
    // perUser: { "username": { "1": 7.3, "2": 20.4, ... } }
    @SerializedName("perUser") val perUser: Map<String, Map<String, Double>>?,
    @SerializedName("perProyek") val perProyek: Map<String, Map<String, Double>>?,
    @SerializedName("month") val month: Int?,
    @SerializedName("year") val year: Int?
)
