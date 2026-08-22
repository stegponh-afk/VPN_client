package com.netbridge.app.vpn

import android.content.Intent
import com.netbridge.app.model.VlessConfig

/** Packs/unpacks a [VlessConfig] as [Intent] extras — avoids pulling in a serialization library for one small struct. */
object ConfigExtras {

    private const val E_ID = "id"
    private const val E_REMARK = "remark"
    private const val E_ADDRESS = "address"
    private const val E_PORT = "port"
    private const val E_ENCRYPTION = "encryption"
    private const val E_FLOW = "flow"
    private const val E_SECURITY = "security"
    private const val E_SNI = "sni"
    private const val E_FP = "fp"
    private const val E_PBK = "pbk"
    private const val E_SID = "sid"
    private const val E_SPX = "spx"
    private const val E_NETWORK = "network"
    private const val E_HEADER_TYPE = "headerType"
    private const val E_HOST = "host"
    private const val E_PATH = "path"
    private const val E_SERVICE_NAME = "serviceName"
    private const val E_ALPN = "alpn"
    private const val E_MODE = "mode"
    private const val E_EXTRA_JSON = "extraJson"
    private const val E_RAW = "rawUri"

    fun write(intent: Intent, config: VlessConfig): Intent = intent.apply {
        putExtra(E_ID, config.id)
        putExtra(E_REMARK, config.remark)
        putExtra(E_ADDRESS, config.address)
        putExtra(E_PORT, config.port)
        putExtra(E_ENCRYPTION, config.encryption)
        putExtra(E_FLOW, config.flow)
        putExtra(E_SECURITY, config.security)
        putExtra(E_SNI, config.sni)
        putExtra(E_FP, config.fingerprint)
        putExtra(E_PBK, config.publicKey)
        putExtra(E_SID, config.shortId)
        putExtra(E_SPX, config.spiderX)
        putExtra(E_NETWORK, config.network)
        putExtra(E_HEADER_TYPE, config.headerType)
        putExtra(E_HOST, config.host)
        putExtra(E_PATH, config.path)
        putExtra(E_SERVICE_NAME, config.serviceName)
        putExtra(E_ALPN, config.alpn)
        putExtra(E_MODE, config.mode)
        putExtra(E_EXTRA_JSON, config.extraJson)
        putExtra(E_RAW, config.rawUri)
    }

    fun read(intent: Intent): VlessConfig? {
        val id = intent.getStringExtra(E_ID) ?: return null
        val address = intent.getStringExtra(E_ADDRESS) ?: return null
        return VlessConfig(
            id = id,
            remark = intent.getStringExtra(E_REMARK) ?: address,
            address = address,
            port = intent.getIntExtra(E_PORT, 443),
            encryption = intent.getStringExtra(E_ENCRYPTION) ?: "none",
            flow = intent.getStringExtra(E_FLOW) ?: "",
            security = intent.getStringExtra(E_SECURITY) ?: "none",
            sni = intent.getStringExtra(E_SNI) ?: "",
            fingerprint = intent.getStringExtra(E_FP) ?: "",
            publicKey = intent.getStringExtra(E_PBK) ?: "",
            shortId = intent.getStringExtra(E_SID) ?: "",
            spiderX = intent.getStringExtra(E_SPX) ?: "",
            network = intent.getStringExtra(E_NETWORK) ?: "tcp",
            headerType = intent.getStringExtra(E_HEADER_TYPE) ?: "",
            host = intent.getStringExtra(E_HOST) ?: "",
            path = intent.getStringExtra(E_PATH) ?: "",
            serviceName = intent.getStringExtra(E_SERVICE_NAME) ?: "",
            alpn = intent.getStringExtra(E_ALPN) ?: "",
            mode = intent.getStringExtra(E_MODE) ?: "",
            extraJson = intent.getStringExtra(E_EXTRA_JSON) ?: "",
            rawUri = intent.getStringExtra(E_RAW) ?: "",
        )
    }
}
