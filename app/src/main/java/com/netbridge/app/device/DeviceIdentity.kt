package com.netbridge.app.device

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import com.netbridge.app.store.AppPreferences
import java.security.MessageDigest
import java.util.UUID

/**
 * Produces a stable per-install device identifier used to enforce "N devices per
 * subscription" on the server side.
 *
 * Android does not expose a real hardware id to apps (IMEI/serial require special,
 * hard-to-obtain permissions and are being phased out). The practical substitute
 * used by every app that needs to count devices is [Settings.Secure.ANDROID_ID]:
 * a 64-bit value unique per app-signing-key + user + device, stable across app
 * reinstalls, and reset only on factory reset. A small number of very old/rooted
 * devices report a known-bad constant for it, so we fall back to a random UUID
 * generated once and persisted locally.
 */
object DeviceIdentity {

    private const val KNOWN_BAD_ANDROID_ID = "9774d56d682e549c"

    @SuppressLint("HardwareIds")
    fun getOrCreate(context: Context): String {
        val prefs = AppPreferences(context)
        prefs.deviceId?.let { return it }

        val androidId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull()

        val source = if (!androidId.isNullOrBlank() && androidId != KNOWN_BAD_ANDROID_ID) {
            "android:$androidId"
        } else {
            "generated:${UUID.randomUUID()}"
        }

        val id = sha256Hex(source).take(32)
        prefs.deviceId = id
        return id
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
