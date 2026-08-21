package com.netbridge.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.RecyclerView
import com.netbridge.app.R
import com.netbridge.app.databinding.ItemServerBinding
import com.netbridge.app.model.VlessConfig
import kotlinx.coroutines.launch

class ServersAdapter(
    private val scope: LifecycleCoroutineScope,
    private val onSelect: (VlessConfig) -> Unit,
) : RecyclerView.Adapter<ServersAdapter.ServerViewHolder>() {

    private var servers: List<VlessConfig> = emptyList()
    private var selectedKey: String? = null
    private val pingCache = mutableMapOf<String, Int?>()
    private val pingInFlight = mutableSetOf<String>()

    fun submit(newServers: List<VlessConfig>, selected: String?) {
        servers = newServers
        selectedKey = selected
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServerViewHolder {
        val binding = ItemServerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ServerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ServerViewHolder, position: Int) {
        val server = servers[position]
        holder.bind(server, server.key == selectedKey, onSelect)
        bindPing(holder, server)
    }

    override fun getItemCount(): Int = servers.size

    fun getServer(position: Int): VlessConfig? = servers.getOrNull(position)

    /** Drops the cached ping for [serverKey] and re-binds it, which makes [bindPing] re-measure. */
    fun refreshPing(serverKey: String) {
        pingCache.remove(serverKey)
        val index = servers.indexOfFirst { it.key == serverKey }
        if (index != -1) notifyItemChanged(index)
    }

    private fun bindPing(holder: ServerViewHolder, server: VlessConfig) {
        val cached = pingCache[server.key]
        if (cached != null || pingCache.containsKey(server.key)) {
            holder.showPing(cached)
            return
        }
        holder.showPingLoading()
        if (pingInFlight.add(server.key)) {
            scope.launch {
                val result = PingChecker.measure(server.address, server.port)
                pingCache[server.key] = result
                pingInFlight.remove(server.key)
                val index = servers.indexOfFirst { it.key == server.key }
                if (index != -1) notifyItemChanged(index)
            }
        }
    }

    class ServerViewHolder(private val binding: ItemServerBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(server: VlessConfig, isSelected: Boolean, onSelect: (VlessConfig) -> Unit) {
            binding.serverName.text = server.remark
            binding.serverAddress.text = "${server.address}:${server.port}"
            binding.serverRadio.isChecked = isSelected
            binding.root.isSelected = isSelected
            binding.root.setOnClickListener { onSelect(server) }
        }

        fun showPingLoading() {
            binding.pingBadge.text = "…"
            binding.pingBadge.setTextColor(ContextCompat.getColor(binding.root.context, R.color.text_secondary))
        }

        fun showPing(ms: Int?) {
            val context = binding.root.context
            if (ms == null) {
                binding.pingBadge.text = "—"
                binding.pingBadge.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                return
            }
            binding.pingBadge.text = "$ms ms"
            val colorRes = when {
                ms < 150 -> R.color.status_connected
                ms < 400 -> R.color.status_connecting
                else -> R.color.status_disconnected
            }
            binding.pingBadge.setTextColor(ContextCompat.getColor(context, colorRes))
        }
    }
}
