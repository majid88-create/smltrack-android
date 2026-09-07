package com.ideakaryanusa.smltrack.network

import com.ideakaryanusa.smltrack.BuildConfig
import com.ideakaryanusa.smltrack.model.BackendResponse
import com.ideakaryanusa.smltrack.model.BackendTraceBatchRequest
import com.ideakaryanusa.smltrack.model.BackendTraceRequest
import com.ideakaryanusa.smltrack.model.GeofenceResponse
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

/**
 * Komunikasi dengan backend SENDIRI (Apps Script Web App).
 *
 * CATATAN soal Apps Script: Web App Apps Script itu suka melakukan REDIRECT
 * (302) ke domain googleusercontent.com saat memproses request. OkHttp secara
 * default sudah otomatis mengikuti redirect, jadi ini aman - tapi itu sebabnya
 * kita pakai client terpisah dengan followRedirects(true) eksplisit.
 */
interface BackendApiService {

    @POST("exec")
    suspend fun sendTrace(@Body request: BackendTraceRequest): Response<BackendResponse>

    @POST("exec")
    suspend fun sendTraceBatch(@Body request: BackendTraceBatchRequest): Response<BackendResponse>

    @GET("exec")
    suspend fun getGeofence(
        @Query("action") action: String = "geofence",
        @Query("secret") secret: String
    ): Response<GeofenceResponse>

    @GET("exec")
    suspend fun getHome(
        @Query("action") action: String = "home",
        @Query("secret") secret: String,
        @Query("username") username: String
    ): Response<com.ideakaryanusa.smltrack.model.HomeResponse>

    @GET("exec")
    suspend fun getRecap(
        @Query("action") action: String = "recap",
        @Query("secret") secret: String,
        @Query("month") month: Int,
        @Query("year") year: Int
    ): Response<com.ideakaryanusa.smltrack.model.RecapResponse>

    @GET("exec")
    suspend fun getLaporanList(
        @Query("action") action: String = "laporan_list",
        @Query("secret") secret: String,
        @Query("username") username: String
    ): Response<com.ideakaryanusa.smltrack.model.LaporanListResponse2>

    @GET("exec")
    suspend fun getJadwal(
        @Query("action") action: String = "jadwal",
        @Query("secret") secret: String,
        @Query("username") username: String
    ): Response<com.ideakaryanusa.smltrack.model.JadwalResponse>

    @POST("exec")
    suspend fun checkInOut(
        @Body request: com.ideakaryanusa.smltrack.model.CheckInOutRequest
    ): Response<BackendResponse>

    @POST("exec")
    suspend fun createLaporan(
        @Body request: com.ideakaryanusa.smltrack.model.LaporanCreateRequest
    ): Response<BackendResponse>
}

object BackendClient {

    val api: BackendApiService by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                    else HttpLoggingInterceptor.Level.NONE
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        // BACKEND_BASE_URL harus diakhiri "/" dan menunjuk ke Web App Apps Script,
        // contoh: https://script.google.com/macros/s/AKfy..../  (tanpa "exec" di ujung)
        Retrofit.Builder()
            .baseUrl(BuildConfig.BACKEND_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BackendApiService::class.java)
    }
}
