package com.angelvpn.app.domain

import java.util.UUID

/** Supported tunnel engines */
enum class Engine(val label: String, val schemes: List<String>) {
    SLIPNET("SlipNet", listOf("slipnet")),
    DNSTT("DNSTT", listOf("dnst", "dnstt")),
    SOAR("Soar", listOf("soar")),
    COTTEN("Cotten", listOf("cotten", "cottendns")),
    MASTERDNS("MasterDNS", listOf("masterdns", "stormdns")),
    UNKNOWN("Unknown", emptyList());

    companion object {
        fun fromScheme(scheme: String): Engine {
            val s = scheme.lowercase().removeSuffix(":")
            return entries.firstOrNull { s in it.schemes } ?: UNKNOWN
        }
    }
}

/** Connection state for UI + service */
enum class ConnState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ERROR
}

/**
 * Core profile model.
 * [pipe] keeps original SlipNet pipe fields for lossless re-export.
 */
data class Profile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val engine: Engine,
    val subtype: String? = null,          // e.g. dnstt, slipstream, noizdns, cottenmax
    val domain: String,                   // tunnel domain / server name
    val publicKey: String? = null,        // server pubkey (base64)
    val mkey: String? = null,             // master/encryption key (Cotten/MasterDNS)
    val resolvers: List<String> = emptyList(),
    val pipe: List<String>? = null,       // raw SlipNet fields (index-based)
    val rawLink: String? = null,          // original imported link
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastConnectedAt: Long? = null,
    val fingerprint: String = ""
)

data class ProbeResult(
    val ok: Boolean,
    val latencyMs: Long? = null,
    val message: String = "",
    val nsRecords: List<String> = emptyList(),
    val aRecords: List<String> = emptyList()
)

data class ImportResult(
    val profiles: List<Profile>,
    val warnings: List<String> = emptyList(),
    val errors: List<String> = emptyList()
)

/** Runtime connection snapshot shared between Service and UI */
data class VpnSnapshot(
    val state: ConnState = ConnState.DISCONNECTED,
    val profileId: String? = null,
    val profileName: String? = null,
    val engine: Engine? = null,
    val bytesIn: Long = 0,
    val bytesOut: Long = 0,
    val connectedSince: Long? = null,
    val errorMessage: String? = null
)
