package com.netbridge.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.netbridge.app.databinding.ActivityMainBinding
import com.netbridge.app.databinding.DialogSubscriptionBinding
import com.netbridge.app.device.DeviceIdentity
import com.netbridge.app.model.VlessConfig
import com.netbridge.app.store.AppPreferences
import com.netbridge.app.subscription.SubscriptionInfo
import com.netbridge.app.subscription.SubscriptionParser
import com.netbridge.app.subscription.SubscriptionRepository
import com.netbridge.app.ui.ButtonStateAnimator
import com.netbridge.app.ui.ServerDividerDecoration
import com.netbridge.app.ui.ServerSwipeCallback
import com.netbridge.app.ui.ServersAdapter
import com.netbridge.app.vpn.TunnelController
import com.netbridge.app.vpn.TunnelState
import com.netbridge.app.vpn.TunnelStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
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
    private var subscriptionInfo: SubscriptionInfo? = null
    private var isCardExpanded = true
    private var isAnnounceExpanded = false
    private var announceOverflows = false
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
        binding.serverList.addItemDecoration(ServerDividerDecoration(this))
        // Default ItemAnimator's "changed" transition fights our own swipe-back
        // animation on the same view (ping refresh calls notifyItemChanged right as
        // the card is animating back to rest) — the card visibly snapped twice.
        binding.serverList.itemAnimator = null
        ItemTouchHelper(
            ServerSwipeCallback(
                context = this,
                getServer = adapter::getServer,
                onSwipeLeft = { server -> adapter.refreshPing(server.key) },
                onSwipeRight = { server -> connectToServer(server) },
            )
        ).attachToRecyclerView(binding.serverList)

        binding.subscriptionButton.setOnClickListener { showSubscriptionDialog() }
        binding.refreshServersButton.setOnClickListener { refreshServers() }
        binding.connectButton.setOnClickListener { onConnectClicked() }
        binding.subCardHeader.setOnClickListener { toggleCardExpanded() }
        binding.supportLinkText.setOnClickListener { openSupportLink() }
        binding.announceToggle.setOnClickListener { toggleAnnounceExpanded() }

        isCardExpanded = prefs.subscriptionCardExpanded
        applyCardExpanded(animate = false)

        loadCachedServers()
        loadCachedSubscriptionInfo()
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

    private fun loadCachedSubscriptionInfo() {
        subscriptionInfo = prefs.subscriptionInfo
        renderSubscriptionCard()
    }

    private fun renderServers() {
        binding.emptyServersText.visibility = if (servers.isEmpty()) View.VISIBLE else View.GONE
        adapter.submit(servers, prefs.selectedServerKey)
    }

    private fun onServerSelected(config: VlessConfig) {
        prefs.selectedServerKey = config.key
        adapter.submit(servers, config.key)
    }

    private fun showSubscriptionDialog() {
        val dialogBinding = DialogSubscriptionBinding.inflate(layoutInflater)
        dialogBinding.subscriptionInput.setText(prefs.subscriptionUrl.orEmpty())

        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogBinding.dialogCancelButton.setOnClickListener { dialog.dismiss() }
        dialogBinding.dialogSaveButton.setOnClickListener {
            val url = dialogBinding.subscriptionInput.text?.toString()?.trim().orEmpty()
            if (url.isNotBlank()) {
                prefs.subscriptionUrl = url
                refreshServers()
            }
            dialog.dismiss()
        }
        dialog.show()
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
                servers = fetched.servers
                prefs.cachedServersRaw = fetched.servers.joinToString("\n") { it.rawUri }
                subscriptionInfo = fetched.info
                prefs.subscriptionInfo = fetched.info
                renderServers()
                renderSubscriptionCard()
            }.onFailure { error ->
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.error_subscription_fetch_failed, error.message),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun renderSubscriptionCard() {
        val info = subscriptionInfo
        binding.subCardTitle.text = info?.title?.takeIf { it.isNotBlank() } ?: getString(R.string.label_server)

        val total = info?.totalBytes
        val used = info?.downloadBytes?.let { d -> (info.uploadBytes ?: 0) + d }
        if (total != null && total > 0 && used != null) {
            binding.trafficBar.visibility = View.VISIBLE
            binding.trafficBar.progress = ((used.toDouble() / total.toDouble()) * 100).toInt().coerceIn(0, 100)
            binding.trafficText.visibility = View.VISIBLE
            binding.trafficText.text = getString(
                R.string.traffic_usage_format,
                formatGb(used),
                formatGb(total)
            )
        } else {
            binding.trafficBar.visibility = View.GONE
            binding.trafficText.visibility = View.GONE
        }

        val expire = info?.expireEpochSeconds
        if (expire != null && expire > 0) {
            binding.expiryText.visibility = View.VISIBLE
            binding.expiryText.text = getString(R.string.expiry_format, formatDate(expire))
        } else {
            binding.expiryText.visibility = View.GONE
        }

        val announce = info?.announce?.trim()
        if (!announce.isNullOrBlank()) {
            binding.announceText.visibility = View.VISIBLE
            binding.announceText.text = announce
            isAnnounceExpanded = false
            announceOverflows = false
            applyAnnounceExpanded(animate = false)
            binding.announceText.post { detectAnnounceOverflow() }
        } else {
            binding.announceText.visibility = View.GONE
            binding.announceToggle.visibility = View.GONE
        }

        if (info?.supportUrl.isNullOrBlank()) {
            binding.supportLinkText.visibility = View.GONE
        } else {
            binding.supportLinkText.visibility = View.VISIBLE
            binding.supportLinkText.text = getString(R.string.support_link)
        }
    }

    /** Runs once, right after the text is set and collapsed to 2 lines, to decide whether a toggle is needed at all. */
    private fun detectAnnounceOverflow() {
        val layout = binding.announceText.layout ?: return
        announceOverflows = layout.lineCount > 0 && layout.getEllipsisCount(layout.lineCount - 1) > 0
        updateAnnounceToggleView()
    }

    private fun toggleAnnounceExpanded() {
        isAnnounceExpanded = !isAnnounceExpanded
        applyAnnounceExpanded(animate = true)
    }

    private fun applyAnnounceExpanded(animate: Boolean) {
        if (animate) {
            TransitionManager.beginDelayedTransition(binding.root, AutoTransition().apply { duration = 200 })
        }
        if (isAnnounceExpanded) {
            binding.announceText.maxLines = Integer.MAX_VALUE
            binding.announceText.ellipsize = null
        } else {
            binding.announceText.maxLines = 2
            binding.announceText.ellipsize = android.text.TextUtils.TruncateAt.END
        }
        updateAnnounceToggleView()
    }

    private fun updateAnnounceToggleView() {
        binding.announceToggle.visibility = if (announceOverflows) View.VISIBLE else View.GONE
        binding.announceToggle.text = getString(
            if (isAnnounceExpanded) R.string.announce_collapse else R.string.announce_expand
        )
    }

    private fun formatGb(bytes: Long): String = String.format(Locale("ru"), "%.1f", bytes / 1_000_000_000.0)

    private fun formatDate(epochSeconds: Long): String =
        SimpleDateFormat("dd.MM.yyyy", Locale("ru")).format(Date(epochSeconds * 1000))

    private fun openSupportLink() {
        val url = subscriptionInfo?.supportUrl ?: return
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    private fun toggleCardExpanded() {
        isCardExpanded = !isCardExpanded
        prefs.subscriptionCardExpanded = isCardExpanded
        applyCardExpanded(animate = true)
    }

    private fun applyCardExpanded(animate: Boolean) {
        if (animate) {
            TransitionManager.beginDelayedTransition(binding.root, AutoTransition().apply { duration = 220 })
        }
        binding.subCardExpandable.visibility = if (isCardExpanded) View.VISIBLE else View.GONE
        binding.subCardChevron.animate().rotation(if (isCardExpanded) 180f else 0f).setDuration(220).start()
        if (isCardExpanded && binding.announceText.text.isNotBlank()) {
            if (isAnnounceExpanded) {
                updateAnnounceToggleView()
            } else {
                binding.announceText.post { detectAnnounceOverflow() }
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
        connectToServer(config)
    }

    /** Selects [config] and connects to it — shared by the connect button and swipe-right-to-connect. */
    private fun connectToServer(config: VlessConfig) {
        prefs.selectedServerKey = config.key
        adapter.submit(servers, config.key)

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
            binding.sessionTimerText.visibility = View.VISIBLE
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
            binding.sessionTimerText.visibility = View.GONE
        }
    }

    private fun formatElapsed(totalSeconds: Long): String {
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
    }
}
