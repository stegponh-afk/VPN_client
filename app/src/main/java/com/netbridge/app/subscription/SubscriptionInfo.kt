package com.netbridge.app.subscription

import com.netbridge.app.model.VlessConfig

/**
 * Subscription metadata a panel sends as response headers alongside the server
 * list itself (`profile-title`, `announce`, `subscription-userinfo`, etc. — the
 * de-facto standard used by Xray/sing-box/Clash-compatible subscription APIs).
 */
data class SubscriptionInfo(
    val title: String?,
    val announce: String?,
    val supportUrl: String?,
    val uploadBytes: Long?,
    val downloadBytes: Long?,
    val totalBytes: Long?,
    val expireEpochSeconds: Long?,
    val updateIntervalHours: Int?,
)

data class SubscriptionFetchResult(
    val servers: List<VlessConfig>,
    val info: SubscriptionInfo?,
)
