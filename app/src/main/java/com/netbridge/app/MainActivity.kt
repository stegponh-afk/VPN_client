package com.netbridge.app

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.netbridge.app.databinding.ActivityMainBinding
import com.netbridge.app.databinding.DialogSubscriptionBinding
import com.netbridge.app.device.DeviceIdentity
import com.netbridge.app.model.VlessConfig
import com.netbridge.app.store.AppPreferences
import com.netbridge.app.subscription.SubscriptionParser
import com.netbridge.app.subscription.SubscriptionRepository
import com.netbridge.app.ui.ButtonStateAnimator
import com.netbridge.app.ui.ServersAdapter
import com.netbridge.app.vpn.TunnelController
import com.netbridge.app.vpn.TunnelState
import com.netbridge.app.vpn.TunnelStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: AppPreferences
    private lateinit var deviceId: String
    private lateinit var controller: TunnelController
    private lateinit var buttonAnimator: ButtonStateAnimator
    private val repository = SubscriptionRepository()
    private val adapter by lazy { ServersAdapter(scope = lifecycleScope, onSelect = ::onServerSelected) }

    private var servers: List<VlessConfig> = emptyList()
    private var pendingConnectConfig: VlessConfig? = null
    private var sessionTimerJob: Job? = null
    private var sessionStartMillis: Long = 0L

    private val vpnConsentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingConnectConfig?.let { controller.connect(it) }
        } else {
            Toast.makeText(this, "VPN permission denied", Toast.LENGTH_SHORT).show()
        }
        pendingConnectConfig = null
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op: connecting proceeds regardless, notification just won't show without it */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = AppPreferences(this)
        deviceId = DeviceIdentity.getOrCreate(this)
        controller = TunnelController(this)
        buttonAnimator = ButtonStateAnimator(binding.connectButton, binding.spinnerRing, binding.pulseRing)

        binding.deviceIdText.text = getString(R.string.device_id_label, deviceId.take(12))
        binding.serverList.layoutManager = LinearLayoutManager(this)
        binding.serverList.adapter = adapter

        binding.subscriptionButton.setOnClickListener { showSubscriptionDialog() }
        binding.refreshServersButton.setOnClickListener { refreshServers() }
        binding.connectButton.setOnClickListener { onConnectClicked() }

        loadCachedServers()
        observeStatus()

        if (prefs.subscriptionUrl.isNullOrBlank()) {
            showSubscriptionDialog()
        }

        requestNotificationPermissionIfNeeded()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun loadCachedServers() {
        val cached = prefs.cachedServersRaw ?: return
        servers = SubscriptionParser.parse(cached)
        renderServers()
    }

    private fun renderServers() {
        binding.emptyServersText.visibility = if (servers.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        adapter.submit(servers, prefs.selectedServerKey)
    }

    private fun onServerSelected(config: VlessConfig) {
        prefs.selectedServerKey = config.key
        adapter.submit(servers, config.key)
    }

    private fun showSubscriptionDialog() {
        val dialogBinding = DialogSubscriptionBinding.inflate(layoutInflater)
        dialogBinding.subscriptionInput.setText(prefs.subscriptionUrl.orEmpty())

        AlertDialog.Builder(this)
            .setTitle(R.string.subscription_dialog_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.action_save_subscription) { _, _ ->
                val url = dialogBinding.subscriptionInput.text?.toString()?.trim().orEmpty()
                if (url.isNotBlank()) {
                    prefs.subscriptionUrl = url
                    refreshServers()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun refreshServers() {
        val url = prefs.subscriptionUrl
        if (url.isNullOrBlank()) {
            Toast.makeText(this, R.string.error_no_subscription, Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            val result = repository.fetchServers(url, deviceId)
            result.onSuccess { fetched ->
                servers = fetched
                prefs.cachedServersRaw = fetched.joinToString("\n") { it.rawUri }
                renderServers()
            }.onFailure { error ->
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.error_subscription_fetch_failed, error.message),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun onConnectClicked() {
        val currentState = TunnelStatus.status.value.state
        if (currentState != TunnelState.DISCONNECTED) {
            controller.disconnect()
            return
        }

        val selectedKey = prefs.selectedServerKey
        val config = servers.firstOrNull { it.key == selectedKey } ?: servers.firstOrNull()
        if (config == null) {
            Toast.makeText(this, R.string.error_no_server_selected, Toast.LENGTH_SHORT).show()
            return
        }

        val consentIntent = controller.prepareConsentIntent()
        if (consentIntent != null) {
            pendingConnectConfig = config
            vpnConsentLauncher.launch(consentIntent)
        } else {
            controller.connect(config)
        }
    }

    private fun observeStatus() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                TunnelStatus.status.collect { value ->
                    updateStatusUi(value.state)
                    value.error?.let {
                        Toast.makeText(this@MainActivity, it, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun updateStatusUi(state: TunnelState) {
        val (textRes, colorRes) = when (state) {
            TunnelState.DISCONNECTED -> R.string.status_disconnected to R.color.status_disconnected
            TunnelState.CONNECTING -> R.string.status_connecting to R.color.status_connecting
            TunnelState.CONNECTED -> R.string.status_connected to R.color.status_connected
        }
        binding.statusText.setText(textRes)
        binding.statusText.setTextColor(ActivityCompat.getColor(this, colorRes))
        binding.connectButton.isEnabled = state != TunnelState.CONNECTING
        buttonAnimator.setState(state)
        updateSessionTimer(state)
    }

    private fun updateSessionTimer(state: TunnelState) {
        if (state == TunnelState.CONNECTED) {
            if (sessionTimerJob != null) return
            sessionStartMillis = System.currentTimeMillis()
            binding.sessionTimerText.visibility = android.view.View.VISIBLE
            sessionTimerJob = lifecycleScope.launch {
                while (isActive) {
                    val elapsedSec = (System.currentTimeMillis() - sessionStartMillis) / 1000
                    binding.sessionTimerText.text = formatElapsed(elapsedSec)
                    delay(1000)
                }
            }
        } else {
            sessionTimerJob?.cancel()
            sessionTimerJob = null
            binding.sessionTimerText.visibility = android.view.View.GONE
        }
    }

    private fun formatElapsed(totalSeconds: Long): String {
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
    }
}
