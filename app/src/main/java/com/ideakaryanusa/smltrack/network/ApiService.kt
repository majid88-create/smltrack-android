package com.ideakaryanusa.smltrack.network

import com.ideakaryanusa.smltrack.model.AbsenceListResponse
import com.ideakaryanusa.smltrack.model.LaporanDetailResponse
import com.ideakaryanusa.smltrack.model.LaporanListResponse
import com.ideakaryanusa.smltrack.model.LoginRequest
import com.ideakaryanusa.smltrack.model.LoginResponse
import com.ideakaryanusa.smltrack.model.ScheduleListResponse
import com.ideakaryanusa.smltrack.model.TraceLogRequest
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface ApiService {

    @POST("api/auth")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    // GPS dikirim ke /api/absence (POST), BUKAN /api/trace-log.
    // Dikonfirmasi lewat tes: POST 8 field PascalCase ke /api/absence -> 200 "Success".
    // Endpoint /api/trace-log ternyata cuma untuk baca (GET), makanya POST ke situ
    // selalu ditolak 400. Ini temuan dari menangkap traffic, bukan tebakan.
    @POST("api/absence")
    suspend fun sendTraceLog(
        @Header("X-Auth-Token") token: String,
        @Body request: TraceLogRequest
    ): Response<Unit>

    // ---- Laporan ----

    @GET("api/laporan")
    suspend fun getLaporanList(@Header("X-Auth-Token") token: String): Response<LaporanListResponse>

    @GET("api/laporan/{id}")
    suspend fun getLaporanDetail(
        @Header("X-Auth-Token") token: String,
        @Path("id") id: String
    ): Response<LaporanDetailResponse>

    @Multipart
    @POST("api/laporan")
    suspend fun createLaporan(
        @Header("X-Auth-Token") token: String,
        @Part("title") title: RequestBody,
        @Part("description") description: RequestBody,
        @Part("category") category: RequestBody,
        @Part("location") location: RequestBody,
        @Part("latitude") latitude: RequestBody,
        @Part("longitude") longitude: RequestBody,
        @Part("project_id") projectId: RequestBody?,
        @Part image: MultipartBody.Part?
    ): Response<LaporanDetailResponse>

    // ---- Schedule ----

    @GET("api/schedules")
    suspend fun getSchedules(@Header("X-Auth-Token") token: String): Response<ScheduleListResponse>

    // ---- Recap absensi ----

    @GET("api/absence")
    suspend fun getAbsenceRecap(@Header("X-Auth-Token") token: String): Response<AbsenceListResponse>
}
