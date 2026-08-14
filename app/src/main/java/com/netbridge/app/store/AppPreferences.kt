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

    private companion object {
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_SUBSCRIPTION_URL = "subscription_url"
        const val KEY_SELECTED_SERVER = "selected_server_key"
        const val KEY_CACHED_SERVERS = "cached_servers_raw"
    }
}
