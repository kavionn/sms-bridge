package com.sms.bridge.data

import android.content.Context
import android.content.SharedPreferences

class SharedPreferencesHelper(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("sms_sync_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_DEVICE_TOKEN = "device_token"
        private const val KEY_DEVICE_NAME = "device_name"
        private const val KEY_LAST_SYNC = "last_sync"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_USER_ROLE = "user_role"
        private const val KEY_ADMIN_TOKEN = "admin_token"
        private const val KEY_REMEMBER_ADMIN_PASSWORD = "remember_admin_password"
        private const val KEY_SAVED_ADMIN_PASSWORD = "saved_admin_password"
        private const val DEFAULT_BASE_URL = "https://your-api-url.com/"
    }

    var rememberAdminPassword: Boolean
        get() = prefs.getBoolean(KEY_REMEMBER_ADMIN_PASSWORD, false)
        set(value) = prefs.edit().putBoolean(KEY_REMEMBER_ADMIN_PASSWORD, value).apply()

    var savedAdminPassword: String?
        get() = prefs.getString(KEY_SAVED_ADMIN_PASSWORD, null)
        set(value) = prefs.edit().putString(KEY_SAVED_ADMIN_PASSWORD, value).apply()

    var userRole: String?
        get() = prefs.getString(KEY_USER_ROLE, null)
        set(value) = prefs.edit().putString(KEY_USER_ROLE, value).apply()

    var adminToken: String?
        get() = prefs.getString(KEY_ADMIN_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_ADMIN_TOKEN, value).apply()

    var deviceId: String?
        get() = prefs.getString(KEY_DEVICE_ID, null)
        set(value) = prefs.edit().putString(KEY_DEVICE_ID, value).apply()

    var deviceToken: String?
        get() = prefs.getString(KEY_DEVICE_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_DEVICE_TOKEN, value).apply()

    var deviceName: String?
        get() = prefs.getString(KEY_DEVICE_NAME, null)
        set(value) = prefs.edit().putString(KEY_DEVICE_NAME, value).apply()

    var lastSync: Long
        get() = prefs.getLong(KEY_LAST_SYNC, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC, value).apply()

    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        set(value) = prefs.edit().putString(KEY_BASE_URL, value).apply()

    val isPaired: Boolean
        get() = !deviceToken.isNullOrEmpty()

    fun clear() {
        prefs.edit().clear().apply()
    }
}
