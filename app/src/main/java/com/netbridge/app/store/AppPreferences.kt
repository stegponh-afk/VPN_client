package com.netbridge.app.store

import android.content.Context

/** Thin wrapper over SharedPreferences — the only persisted state this app has. */
class AppPreferences(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("netbridge_prefs", Context.MODE_PRIVATE)

    var deviceId: String?
        get() = prefs.getString(KEY_DEVICE_ID, null)
        set(value) = prefs.edit().putString(KEY_DEVICE_ID, value).apply()

    var subscriptionUrl: String?
        get() = prefs.getString(KEY_SUBSCRIPTION_URL, null)
        set(value) = prefs.edit().putString(KEY_SUBSCRIPTION_URL, value).apply()

    var selectedServerKey: String?
        get() = prefs.getString(KEY_SELECTED_SERVER, null)
        set(value) = prefs.edit().putString(KEY_SELECTED_SERVER, value).apply()

    /** Raw vless:// links from the last successful subscription fetch, one per line — lets the UI restore the list without a network call on every launch. */
    var cachedServersRaw: String?
        get() = prefs.getString(KEY_CACHED_SERVERS, null)
        set(value) = prefs.edit().putString(KEY_CACHED_SERVERS, value).apply()

    var subscriptionCardExpanded: Boolean
        get() = prefs.getBoolean(KEY_CARD_EXPANDED, true)
        set(value) = prefs.edit().putBoolean(KEY_CARD_EXPANDED, value).apply()

    /** Last-fetched subscription metadata (title/traffic/expiry/etc.), so the card survives an app restart without a network call. */
    var subscriptionInfo: com.netbridge.app.subscription.SubscriptionInfo?
        get() {
            if (!prefs.contains(KEY_SUB_TITLE) && !prefs.contains(KEY_SUB_ANNOUNCE)) return null
            return com.netbridge.app.subscription.SubscriptionInfo(
                title = prefs.getString(KEY_SUB_TITLE, null),
                announce = prefs.getString(KEY_SUB_ANNOUNCE, null),
                supportUrl = prefs.getString(KEY_SUB_SUPPORT_URL, null),
                uploadBytes = prefs.getLong(KEY_SUB_UPLOAD, -1).takeIf { it >= 0 },
                downloadBytes = prefs.getLong(KEY_SUB_DOWNLOAD, -1).takeIf { it >= 0 },
                totalBytes = prefs.getLong(KEY_SUB_TOTAL, -1).takeIf { it >= 0 },
                expireEpochSeconds = prefs.getLong(KEY_SUB_EXPIRE, -1).takeIf { it >= 0 },
                updateIntervalHours = prefs.getInt(KEY_SUB_UPDATE_INTERVAL, -1).takeIf { it >= 0 },
            )
        }
        set(value) = prefs.edit().apply {
            putString(KEY_SUB_TITLE, value?.title)
            putString(KEY_SUB_ANNOUNCE, value?.announce)
            putString(KEY_SUB_SUPPORT_URL, value?.supportUrl)
            putLong(KEY_SUB_UPLOAD, value?.uploadBytes ?: -1)
            putLong(KEY_SUB_DOWNLOAD, value?.downloadBytes ?: -1)
            putLong(KEY_SUB_TOTAL, value?.totalBytes ?: -1)
            putLong(KEY_SUB_EXPIRE, value?.expireEpochSeconds ?: -1)
            putInt(KEY_SUB_UPDATE_INTERVAL, value?.updateIntervalHours ?: -1)
        }.apply()

    private companion object {
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_SUBSCRIPTION_URL = "subscription_url"
        const val KEY_SELECTED_SERVER = "selected_server_key"
        const val KEY_CACHED_SERVERS = "cached_servers_raw"
        const val KEY_CARD_EXPANDED = "subscription_card_expanded"
        const val KEY_SUB_TITLE = "sub_title"
        const val KEY_SUB_ANNOUNCE = "sub_announce"
        const val KEY_SUB_SUPPORT_URL = "sub_support_url"
        const val KEY_SUB_UPLOAD = "sub_upload"
        const val KEY_SUB_DOWNLOAD = "sub_download"
        const val KEY_SUB_TOTAL = "sub_total"
        const val KEY_SUB_EXPIRE = "sub_expire"
        const val KEY_SUB_UPDATE_INTERVAL = "sub_update_interval"
    }
}
