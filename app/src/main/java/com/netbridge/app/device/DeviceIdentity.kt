package com.netbridge.app.device

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import com.netbridge.app.store.AppPreferences
import java.security.SecureRandom

/**
 * Produces a stable per-install device identifier (hwid) used to enforce "N devices
 * per subscription" on the server side.
 *
 * Android does not expose a real hardware id to apps (IMEI/serial require special,
 * hard-to-obtain permissions and are being phased out). The practical substitute
 * used by every app that needs to count devices is [Settings.Secure.ANDROID_ID]:
 * a 64-bit value unique per app-signing-key + user + device, stable across app
 * reinstalls, and reset only on factory reset.
 *
 * Format matters here, not just uniqueness: panels that gate subscriptions by hwid
 * (e.g. Remnawave-based ones) validate the value against the formats their
 * recognized clients send — confirmed against a live panel to accept, among others,
 * bare 16-hex-char ANDROID_ID (what Happ/v2RayTun send) and standard UUIDs (what
 * v2Box/InCY send). A hashed/truncated id in neither shape got silently rejected.
 * We send the rawest, simplest of the accepted shapes: ANDROID_ID as 16 lowercase
 * hex chars, with no transformation.
 */
object DeviceIdentity {

    private const val KNOWN_BAD_ANDROID_ID = "9774d56d682e549c"

    @SuppressLint("HardwareIds")
    fun getOrCreate(context: Context): String {
        val prefs = AppPreferences(context)
        prefs.deviceId?.let { cached -> if (cached.matches(HEX16)) return cached }

        val androidId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull()

        val id = if (!androidId.isNullOrBlank() && androidId != KNOWN_BAD_ANDROID_ID && androidId.matches(HEX16)) {
            androidId.lowercase()
        } else {
            randomHex16()
        }

        prefs.deviceId = id
        return id
    }

    private fun randomHex16(): String {
        val bytes = ByteArray(8)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private val HEX16 = Regex("[0-9a-fA-F]{16}")
}
