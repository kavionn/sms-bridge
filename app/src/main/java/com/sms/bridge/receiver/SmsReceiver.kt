package com.sms.bridge.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.sms.bridge.service.SmsSyncService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            try {
                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                if (messages.isEmpty()) return

                val senderMap = HashMap<String, java.lang.StringBuilder>()
                var smsTime = System.currentTimeMillis()

                for (msg in messages) {
                    val sender = msg.displayOriginatingAddress ?: msg.originatingAddress ?: "Unknown"
                    val body = msg.displayMessageBody ?: msg.messageBody ?: ""
                    smsTime = msg.timestampMillis

                    if (senderMap.containsKey(sender)) {
                        senderMap[sender]?.append(body)
                    } else {
                        senderMap[sender] = java.lang.StringBuilder(body)
                    }
                }

                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val formattedTime = sdf.format(Date(smsTime))

                for ((sender, bodyBuilder) in senderMap) {
                    val serviceIntent = Intent(context, SmsSyncService::class.java).apply {
                        action = SmsSyncService.ACTION_FORWARD_SMS
                        putExtra(SmsSyncService.EXTRA_SENDER, sender)
                        putExtra(SmsSyncService.EXTRA_MESSAGE, bodyBuilder.toString())
                        putExtra(SmsSyncService.EXTRA_RECEIVED_AT, formattedTime)
                    }
                    var serviceStarted = false
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        try {
                            context.startForegroundService(serviceIntent)
                            serviceStarted = true
                        } catch (e: Exception) {
                            Log.e("SmsReceiver", "Failed starting foreground service: ${e.message}. Trying normal background service start.")
                            try {
                                context.startService(serviceIntent)
                                serviceStarted = true
                            } catch (e2: Exception) {
                                Log.e("SmsReceiver", "Failed starting service completely: ${e2.message}")
                            }
                        }
                    } else {
                        try {
                            context.startService(serviceIntent)
                            serviceStarted = true
                        } catch (e: Exception) {
                            Log.e("SmsReceiver", "Failed starting background service: ${e.message}")
                        }
                    }

                    if (!serviceStarted) {
                        // Fail-safe: Save directly to local database queue to prevent data loss
                        try {
                            val db = com.sms.bridge.data.AppDatabase.getDatabase(context)
                            val dao = db.smsDao()
                            CoroutineScope(Dispatchers.IO).launch {
                                dao.insertSms(
                                    com.sms.bridge.data.SmsQueueItem(
                                        sender = sender,
                                        message = bodyBuilder.toString(),
                                        receivedAt = formattedTime
                                    )
                                )
                                Log.d("SmsReceiver", "Saved SMS directly to DB because service could not be started in background.")
                            }
                        } catch (dbEx: Exception) {
                            Log.e("SmsReceiver", "Failed direct DB insert fallback: ${dbEx.message}", dbEx)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("SmsReceiver", "Error receiving SMS: ${e.message}", e)
            }
        }
    }
}
