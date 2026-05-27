package com.sms.bridge.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sms.bridge.data.AppDatabase
import com.sms.bridge.data.SharedPreferencesHelper
import com.sms.bridge.network.ApiClient
import com.sms.bridge.network.ClaimRequest
import com.sms.bridge.network.SmsRequest
import com.sms.bridge.network.AdminAuthRequest
import com.sms.bridge.network.DeviceItem
import com.sms.bridge.network.SmsLogItem
import com.sms.bridge.service.SmsSyncService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication<Application>().applicationContext
    private val db = AppDatabase.getDatabase(context)
    val prefs = SharedPreferencesHelper(context)

    val isPaired = MutableStateFlow(prefs.isPaired)
    val deviceId = MutableStateFlow(prefs.deviceId ?: "-")
    val deviceName = MutableStateFlow(prefs.deviceName ?: "-")
    val apiBaseUrl = MutableStateFlow(prefs.baseUrl)
    val lastSyncTime = MutableStateFlow(prefs.lastSync)

    val userRole = MutableStateFlow(prefs.userRole)
    val adminToken = MutableStateFlow(prefs.adminToken)
    val rememberAdminPassword = MutableStateFlow(prefs.rememberAdminPassword)
    val savedAdminPassword = MutableStateFlow(prefs.savedAdminPassword ?: "")
    val adminPairingCode = MutableStateFlow<String?>(null)
    val adminPairingCodeExpires = MutableStateFlow<String?>(null)
    val adminDevices = MutableStateFlow<List<DeviceItem>>(emptyList())
    val selectedDeviceId = MutableStateFlow<String?>(null)
    val adminSmsLogs = MutableStateFlow<List<SmsLogItem>>(emptyList())
    val isAdminLoading = MutableStateFlow(false)
    val isRefreshingAdminData = MutableStateFlow(false)
    val isDeletingAllSms = MutableStateFlow(false)
    val deletingSmsIds = MutableStateFlow<Set<String>>(emptySet())
    val revokingDeviceIds = MutableStateFlow<Set<String>>(emptySet())
    val isGeneratingPairingCode = MutableStateFlow(false)
    val adminError = MutableStateFlow<String?>(null)

    val isPairingLoading = MutableStateFlow(false)
    val pairingError = MutableStateFlow<String?>(null)
    val pairingSuccess = MutableStateFlow(false)

    val isTestingConnection = MutableStateFlow(false)
    val connectionTestResult = MutableStateFlow<String?>(null)

    val pendingQueue: StateFlow<List<com.sms.bridge.data.SmsQueueItem>> = db.smsDao().getAllPendingSms()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private var pollerJob: kotlinx.coroutines.Job? = null
    private val seenSmsIds = mutableSetOf<String>()
    private var isFirstFetch = true

    init {
        if (prefs.userRole == "admin" && prefs.adminToken != null) {
            fetchAdminDevices()
            startSmsPolling()
        }
    }

    fun selectUserRole(role: String?) {
        prefs.userRole = role
        userRole.value = role
        adminError.value = null
        pairingError.value = null
    }

    fun updateRememberMe(remember: Boolean) {
        prefs.rememberAdminPassword = remember
        rememberAdminPassword.value = remember
        if (!remember) {
            prefs.savedAdminPassword = null
            savedAdminPassword.value = ""
        }
    }

    fun loginAdmin(password: String, rememberMe: Boolean) {
        viewModelScope.launch {
            isAdminLoading.value = true
            adminError.value = null

            val apiService = ApiClient.getApiService(prefs.baseUrl)
            val adminEmail = "admin@smsbridge.com"
            val authReq = AdminAuthRequest(adminEmail, password)

            try {
                // Silent register to make sure admin account exists on a fresh MongoDB backend database
                try {
                    apiService.registerAdmin(authReq)
                } catch (e: Exception) {
                    // Ignore, maybe already exists, which is the expected case
                }

                // Now attempt login
                val loginResponse = apiService.loginAdmin(authReq)
                if (loginResponse.isSuccessful && loginResponse.body() != null) {
                    val body = loginResponse.body()!!
                    prefs.adminToken = body.token
                    prefs.userRole = "admin"
                    
                    prefs.rememberAdminPassword = rememberMe
                    rememberAdminPassword.value = rememberMe
                    if (rememberMe) {
                        prefs.savedAdminPassword = password
                        savedAdminPassword.value = password
                    } else {
                        prefs.savedAdminPassword = null
                        savedAdminPassword.value = ""
                    }
                    
                    adminToken.value = body.token
                    userRole.value = "admin"

                    fetchAdminDevices()
                    startSmsPolling()
                } else {
                    val errMsg = loginResponse.errorBody()?.string() ?: "Login admin gagal"
                    val displayError = if (errMsg.contains("error")) {
                        try {
                            org.json.JSONObject(errMsg).optString("error", errMsg)
                        } catch (je: Exception) {
                            errMsg
                        }
                    } else {
                        errMsg
                    }
                    adminError.value = displayError
                }
            } catch (e: Exception) {
                adminError.value = "Koneksi ke backend gagal: ${e.localizedMessage ?: e.message}"
            } finally {
                isAdminLoading.value = false
            }
        }
    }

    fun logoutAdmin() {
        stopSmsPolling()
        seenSmsIds.clear()
        isFirstFetch = true

        prefs.adminToken = null
        prefs.userRole = null
        
        adminToken.value = null
        userRole.value = null
        adminPairingCode.value = null
        adminPairingCodeExpires.value = null
        adminDevices.value = emptyList()
        selectedDeviceId.value = null
        adminSmsLogs.value = emptyList()
        adminError.value = null
    }

    fun generateAdminPairingCode() {
        val token = prefs.adminToken ?: return
        viewModelScope.launch {
            isGeneratingPairingCode.value = true
            adminError.value = null
            try {
                val apiService = ApiClient.getApiService(prefs.baseUrl)
                val response = apiService.createPairingCode("Bearer $token")
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    adminPairingCode.value = body.code
                    adminPairingCodeExpires.value = body.expiresAt
                } else {
                    val errMsg = response.errorBody()?.string() ?: "Gagal membuat kode"
                    adminError.value = "Gagal membuat kode pairing: $errMsg"
                }
            } catch (e: Exception) {
                adminError.value = "Koneksi gagal: ${e.localizedMessage ?: e.message}"
            } finally {
                isGeneratingPairingCode.value = false
            }
        }
    }

    fun refreshAdminData() {
        val token = prefs.adminToken ?: return
        viewModelScope.launch {
            isRefreshingAdminData.value = true
            adminError.value = null
            try {
                val apiService = ApiClient.getApiService(prefs.baseUrl)
                
                // Fetch devices
                val responseDevices = apiService.getDevices("Bearer $token")
                if (responseDevices.isSuccessful && responseDevices.body() != null) {
                    adminDevices.value = responseDevices.body()!!
                }
                
                // Fetch SMS logs
                val responseSms = apiService.getSmsLogs("Bearer $token", selectedDeviceId.value)
                if (responseSms.isSuccessful && responseSms.body() != null) {
                    adminSmsLogs.value = responseSms.body()!!
                    
                    // Mark logs as seen to avoid triggering double notifications
                    for (log in responseSms.body()!!) {
                        seenSmsIds.add(log.id)
                    }
                }
            } catch (e: Exception) {
                adminError.value = "Refresh gagal: ${e.localizedMessage ?: e.message}"
            } finally {
                isRefreshingAdminData.value = false
            }
        }
    }

    fun fetchAdminDevices() {
        val token = prefs.adminToken ?: return
        viewModelScope.launch {
            try {
                val apiService = ApiClient.getApiService(prefs.baseUrl)
                val response = apiService.getDevices("Bearer $token")
                if (response.isSuccessful && response.body() != null) {
                    adminDevices.value = response.body()!!
                }
            } catch (e: Exception) {
                // Silent
            }
        }
    }

    fun selectDevice(id: String?) {
        selectedDeviceId.value = id
        fetchSmsLogs()
    }

    fun fetchSmsLogs() {
        val token = prefs.adminToken ?: return
        viewModelScope.launch {
            try {
                val apiService = ApiClient.getApiService(prefs.baseUrl)
                val response = apiService.getSmsLogs("Bearer $token", selectedDeviceId.value)
                if (response.isSuccessful && response.body() != null) {
                    adminSmsLogs.value = response.body()!!
                }
            } catch (e: Exception) {
                // Silent
            }
        }
    }

    fun deleteSmsLog(smsId: String) {
        val token = prefs.adminToken ?: return
        viewModelScope.launch {
            deletingSmsIds.value += smsId
            try {
                val apiService = ApiClient.getApiService(prefs.baseUrl)
                val response = apiService.deleteSmsLog("Bearer $token", smsId)
                if (response.isSuccessful) {
                    fetchSmsLogs()
                }
            } catch (e: Exception) {
                // Silent
            } finally {
                deletingSmsIds.value -= smsId
            }
        }
    }

    fun deleteAllSmsLogs() {
        val token = prefs.adminToken ?: return
        viewModelScope.launch {
            isDeletingAllSms.value = true
            adminError.value = null
            try {
                val apiService = ApiClient.getApiService(prefs.baseUrl)
                val response = apiService.deleteAllSmsLogs("Bearer $token", selectedDeviceId.value)
                if (response.isSuccessful) {
                    fetchSmsLogs()
                } else {
                    val errMsg = response.errorBody()?.string() ?: "Gagal menghapus semua SMS"
                    adminError.value = "Gagal menghapus: $errMsg"
                }
            } catch (e: Exception) {
                adminError.value = "Error: ${e.localizedMessage ?: e.message}"
            } finally {
                isDeletingAllSms.value = false
            }
        }
    }

    fun revokeDevice(id: String) {
        val token = prefs.adminToken ?: return
        viewModelScope.launch {
            revokingDeviceIds.value += id
            adminError.value = null
            try {
                val apiService = ApiClient.getApiService(prefs.baseUrl)
                val response = apiService.deleteDevice("Bearer $token", id)
                if (response.isSuccessful) {
                    fetchAdminDevices()
                    fetchSmsLogs()
                } else {
                    val errMsg = response.errorBody()?.string() ?: "Gagal menghapus device"
                    adminError.value = "Gagal menghapus device: $errMsg"
                }
            } catch (e: Exception) {
                adminError.value = "Koneksi gagal: ${e.localizedMessage ?: e.message}"
            } finally {
                revokingDeviceIds.value -= id
            }
        }
    }

    fun updateBaseUrl(url: String) {
        prefs.baseUrl = url
        apiBaseUrl.value = url
    }

    fun pairDevice(code: String, customDeviceName: String) {
        if (code != "AUTO_PAIR" && code.length != 6) {
            pairingError.value = "Kode pairing harus 6 digit angka."
            return
        }

        viewModelScope.launch {
            isPairingLoading.value = true
            pairingError.value = null
            pairingSuccess.value = false

            val modelString = "${Build.MANUFACTURER} ${Build.MODEL}"
            val finalDeviceName = customDeviceName.ifBlank { modelString }
            val client = ApiClient.getApiService(prefs.baseUrl)

            try {
                val response = client.claimDevice(
                    ClaimRequest(
                        code = code,
                        deviceName = finalDeviceName,
                        deviceModel = modelString
                    )
                )

                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    prefs.deviceId = body.deviceId
                    prefs.deviceToken = body.deviceToken
                    prefs.deviceName = finalDeviceName
                    
                    deviceId.value = body.deviceId
                    deviceName.value = finalDeviceName
                    isPaired.value = true
                    pairingSuccess.value = true

                    startForegroundSyncService()
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Pairing gagal"
                    pairingError.value = "Server error: $errorBody"
                }
            } catch (e: Exception) {
                pairingError.value = "Koneksi gagal: ${e.localizedMessage ?: e.message}"
            } finally {
                isPairingLoading.value = false
            }
        }
    }

    fun testConnection() {
        if (!prefs.isPaired) {
            connectionTestResult.value = "Belum dipasangkan (Unpaired)."
            return
        }

        viewModelScope.launch {
            isTestingConnection.value = true
            connectionTestResult.value = null

            val token = prefs.deviceToken ?: ""
            val client = ApiClient.getApiService(prefs.baseUrl)

            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val nowStr = sdf.format(Date())

            try {
                val response = client.sendSms(
                    authHeader = "Bearer $token",
                    request = SmsRequest(
                        sender = "SMS_LINK_TEST",
                        message = "Koneksi tes berhasil dari ${prefs.deviceName ?: "Device"}.",
                        receivedAt = nowStr
                    )
                )

                if (response.isSuccessful) {
                    connectionTestResult.value = "Koneksi Berhasil! API merespons dengan sukses."
                    prefs.lastSync = System.currentTimeMillis()
                    lastSyncTime.value = prefs.lastSync
                    
                    triggerQueueSync()
                } else {
                    connectionTestResult.value = "Koneksi Gagal: HTTP ${response.code()}"
                }
            } catch (e: Exception) {
                connectionTestResult.value = "Koneksi Gagal: ${e.localizedMessage ?: e.message}"
            } finally {
                isTestingConnection.value = false
            }
        }
    }

    fun unpairDevice() {
        viewModelScope.launch {
            val token = prefs.deviceToken
            val baseUrl = prefs.baseUrl
            if (!token.isNullOrBlank()) {
                try {
                    val client = ApiClient.getApiService(baseUrl)
                    client.unpairSelf("Bearer $token")
                } catch (e: Exception) {
                    // Ignore API call errors on unpair so client state cleanup always succeeds
                }
            }

            stopSyncService()
            db.smsDao().clearQueue()
            prefs.clear()

            isPaired.value = false
            deviceId.value = "-"
            deviceName.value = "-"
            lastSyncTime.value = 0L
            pairingSuccess.value = false
            pairingError.value = null
            connectionTestResult.value = null
        }
    }

    fun triggerQueueSync() {
        val intent = Intent(context, SmsSyncService::class.java).apply {
            action = SmsSyncService.ACTION_RETRY_QUEUE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun startForegroundSyncService() {
        val intent = Intent(context, SmsSyncService::class.java).apply {
            action = SmsSyncService.ACTION_START_SERVICE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    private fun stopSyncService() {
        val intent = Intent(context, SmsSyncService::class.java).apply {
            action = SmsSyncService.ACTION_STOP_SERVICE
        }
        context.startService(intent)
    }

    fun startSmsPolling() {
        stopSmsPolling()
        val token = prefs.adminToken ?: return
        pollerJob = viewModelScope.launch {
            while (true) {
                try {
                    val apiService = ApiClient.getApiService(prefs.baseUrl)
                    
                    // 1. Fetch ALL logs to check for new SMS notifications
                    val responseAll = apiService.getSmsLogs("Bearer $token", null)
                    if (responseAll.isSuccessful && responseAll.body() != null) {
                        val allLogs = responseAll.body()!!
                        
                        for (log in allLogs) {
                            if (!seenSmsIds.contains(log.id)) {
                                seenSmsIds.add(log.id)
                            }
                        }
                        
                        if (isFirstFetch) {
                            isFirstFetch = false
                        }
                        
                        // 2. Also refresh the currently selected device's logs on screen
                        val currentSelectedId = selectedDeviceId.value
                        if (currentSelectedId == null) {
                            // If viewing all logs, update directly from our fetched list
                            adminSmsLogs.value = allLogs
                        } else {
                            // If viewing specific device, filter locally
                            val filteredLogs = allLogs.filter { it.deviceId?.id == currentSelectedId }
                            adminSmsLogs.value = filteredLogs
                        }
                    }
                    
                    // 3. Routinely fetch devices as well to see if new devices are paired
                    val responseDevices = apiService.getDevices("Bearer $token")
                    if (responseDevices.isSuccessful && responseDevices.body() != null) {
                        adminDevices.value = responseDevices.body()!!
                    }
                    
                } catch (e: Exception) {
                    // Silent retry
                }
                kotlinx.coroutines.delay(30_000) // Poll every 30 seconds
            }
        }
    }

    fun stopSmsPolling() {
        pollerJob?.cancel()
        pollerJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopSmsPolling()
    }
}
