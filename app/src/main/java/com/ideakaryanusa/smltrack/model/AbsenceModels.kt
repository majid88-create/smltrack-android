package com.ideakaryanusa.smltrack.model

import com.google.gson.annotations.SerializedName

/**
 * CATATAN: endpoint mobile untuk ini ditemukan sebagai "/api/absence" (tunggal)
 * di dalam binary. Ini SEDIKIT beda dengan endpoint "/api/absences" (jamak,
 * dengan query ?date=...) yang dipakai dashboard monitoring gabungan Mas Majid
 * yang sudah pernah dibahas sebelumnya. Kemungkinan dua endpoint yang beda
 * (satu untuk rekap milik sendiri di app mobile, satu untuk admin/dashboard) -
 * kalau endpoint singular ini ternyata 404, coba ganti ke "/api/absences".
 */

data class AbsenceRecord(
    @SerializedName("id") val id: String?,
    @SerializedName("date") val date: String?,
    @SerializedName("status") val status: String?,
    @SerializedName("check_in") val checkIn: String?,
    @SerializedName("check_out") val checkOut: String?
)

data class AbsenceListResponse(
    @SerializedName("data") val data: List<AbsenceRecord>?
)
