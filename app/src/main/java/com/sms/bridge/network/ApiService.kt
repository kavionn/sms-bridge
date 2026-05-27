package com.sms.bridge.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.GET
import retrofit2.http.DELETE
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class ClaimRequest(
    @Json(name = "code") val code: String,
    @Json(name = "device_name") val deviceName: String,
    @Json(name = "device_model") val deviceModel: String
)

@JsonClass(generateAdapter = true)
data class ClaimResponse(
    @Json(name = "device_id") val deviceId: String,
    @Json(name = "device_token") val deviceToken: String
)

@JsonClass(generateAdapter = true)
data class SmsRequest(
    @Json(name = "sender") val sender: String,
    @Json(name = "message") val message: String,
    @Json(name = "received_at") val receivedAt: String
)

@JsonClass(generateAdapter = true)
data class SmsResponse(
    @Json(name = "success") val success: Boolean? = null,
    @Json(name = "message") val message: String? = null
)

@JsonClass(generateAdapter = true)
data class AdminAuthRequest(
    @Json(name = "email") val email: String,
    @Json(name = "password") val password: String
)

@JsonClass(generateAdapter = true)
data class AdminAuthResponse(
    @Json(name = "success") val success: Boolean,
    @Json(name = "token") val token: String
)

@JsonClass(generateAdapter = true)
data class CreateCodeResponse(
    @Json(name = "success") val success: Boolean,
    @Json(name = "code") val code: String,
    @Json(name = "expires_at") val expiresAt: String
)

@JsonClass(generateAdapter = true)
data class DeviceItem(
    @Json(name = "_id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "model") val model: String,
    @Json(name = "lastSeen") val lastSeen: String? = null,
    @Json(name = "revoked") val revoked: Boolean? = null,
    @Json(name = "createdAt") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class SmsLogItem(
    @Json(name = "_id") val id: String,
    @Json(name = "sender") val sender: String,
    @Json(name = "message") val message: String,
    @Json(name = "receivedAt") val receivedAt: String,
    @Json(name = "deviceId") val deviceId: DeviceItem? = null
)

interface ApiService {
    @POST("api/pairing/claim")
    suspend fun claimDevice(
        @Body request: ClaimRequest
    ): Response<ClaimResponse>

    @POST("api/sms")
    suspend fun sendSms(
        @Header("Authorization") authHeader: String,
        @Body request: SmsRequest
    ): Response<SmsResponse>

    @POST("api/sms/unpair")
    suspend fun unpairSelf(
        @Header("Authorization") authHeader: String
    ): Response<SmsResponse>

    @POST("api/auth/admin/register")
    suspend fun registerAdmin(
        @Body request: AdminAuthRequest
    ): Response<SmsResponse>

    @POST("api/auth/admin/login")
    suspend fun loginAdmin(
        @Body request: AdminAuthRequest
    ): Response<AdminAuthResponse>

    @POST("api/pairing/create")
    suspend fun createPairingCode(
        @Header("Authorization") authHeader: String
    ): Response<CreateCodeResponse>

    @GET("api/devices")
    suspend fun getDevices(
        @Header("Authorization") authHeader: String
    ): Response<List<DeviceItem>>

    @DELETE("api/devices/{id}")
    suspend fun deleteDevice(
        @Header("Authorization") authHeader: String,
        @Path("id") id: String
    ): Response<SmsResponse>

    @GET("api/sms")
    suspend fun getSmsLogs(
        @Header("Authorization") authHeader: String,
        @Query("device_id") deviceId: String? = null
    ): Response<List<SmsLogItem>>

    @DELETE("api/sms/{id}")
    suspend fun deleteSmsLog(
        @Header("Authorization") authHeader: String,
        @Path("id") id: String
    ): Response<SmsResponse>

    @DELETE("api/sms")
    suspend fun deleteAllSmsLogs(
        @Header("Authorization") authHeader: String,
        @Query("device_id") deviceId: String? = null
    ): Response<SmsResponse>
}

object ApiClient {
    private var currentBaseUrl: String? = null
    private var apiServiceInstance: ApiService? = null

    @Synchronized
    fun getApiService(baseUrl: String): ApiService {
        val sanitizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        if (apiServiceInstance == null || currentBaseUrl != sanitizedUrl) {
            currentBaseUrl = sanitizedUrl
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build()

            val moshi = Moshi.Builder().build()

            apiServiceInstance = Retrofit.Builder()
                .baseUrl(sanitizedUrl)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .client(client)
                .build()
                .create(ApiService::class.java)
        }
        return apiServiceInstance!!
    }
}
