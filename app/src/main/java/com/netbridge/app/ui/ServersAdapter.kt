package com.netbridge.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.netbridge.app.databinding.ItemServerBinding
import com.netbridge.app.model.VlessConfig

class ServersAdapter(
    private val onSelect: (VlessConfig) -> Unit,
) : RecyclerView.Adapter<ServersAdapter.ServerViewHolder>() {

    private var servers: List<VlessConfig> = emptyList()
    private var selectedKey: String? = null

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
    }

    override fun getItemCount(): Int = servers.size

    class ServerViewHolder(private val binding: ItemServerBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(server: VlessConfig, isSelected: Boolean, onSelect: (VlessConfig) -> Unit) {
            binding.serverName.text = server.remark
            binding.serverAddress.text = "${server.address}:${server.port}"
            binding.serverRadio.isChecked = isSelected
            binding.root.setOnClickListener { onSelect(server) }
        }
    }
}
