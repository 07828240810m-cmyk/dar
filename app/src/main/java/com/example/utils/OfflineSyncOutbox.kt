package com.example.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class OutboxOpType(val raw: String) {
    POLL_VOTE("POLL_VOTE"),
    RATING("RATING"),
    FEEDBACK("FEEDBACK"),
    REFERRAL_CLAIM("REFERRAL_CLAIM"),
    TELEMETRY("TELEMETRY");

    companion object {
        fun fromRaw(raw: String): OutboxOpType? = entries.firstOrNull { it.raw == raw }
    }
}

data class OutboxEntry(
    val id: String,
    val type: OutboxOpType,
    val payloadJson: String,
    val createdAtMillis: Long
)

class OfflineSyncOutbox(context: Context) {
    private val appContext = context.applicationContext
    private val file: File
        get() = File(appContext.filesDir, "offline_cloud_outbox.json")

    @Synchronized
    fun enqueue(type: OutboxOpType, payload: JSONObject) {
        val current = getPendingEntries().toMutableList()
        if (type == OutboxOpType.TELEMETRY || type == OutboxOpType.RATING) {
            current.removeAll { it.type == type }
        }
        current += OutboxEntry(
            id = "ob_${System.currentTimeMillis()}_${current.size}",
            type = type,
            payloadJson = payload.toString(),
            createdAtMillis = System.currentTimeMillis()
        )
        saveAll(current)
    }

    @Synchronized
    fun getPendingEntries(): List<OutboxEntry> {
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText(Charsets.UTF_8))
            (0 until arr.length()).mapNotNull { idx ->
                val obj = arr.optJSONObject(idx) ?: return@mapNotNull null
                val type = OutboxOpType.fromRaw(obj.optString("type")) ?: return@mapNotNull null
                OutboxEntry(
                    id = obj.optString("id"),
                    type = type,
                    payloadJson = obj.optString("payloadJson", "{}"),
                    createdAtMillis = obj.optLong("createdAtMillis", 0L)
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun removeByIds(ids: Set<String>) {
        if (ids.isEmpty()) return
        val remaining = getPendingEntries().filterNot { it.id in ids }
        saveAll(remaining)
    }

    @Synchronized
    fun pendingCount(): Int = getPendingEntries().size

    private fun saveAll(entries: List<OutboxEntry>) {
        try {
            val arr = JSONArray()
            entries.forEach { e ->
                arr.put(JSONObject().apply {
                    put("id", e.id)
                    put("type", e.type.raw)
                    put("payloadJson", e.payloadJson)
                    put("createdAtMillis", e.createdAtMillis)
                })
            }
            file.writeText(arr.toString(), Charsets.UTF_8)
        } catch (_: Exception) {
        }
    }

    companion object {
        fun isOnlineNow(context: Context): Boolean {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }

        fun observeOnlineStatus(context: Context): Flow<Boolean> = callbackFlow {
            val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm == null) {
                trySend(false)
                close()
                return@callbackFlow
            }
            trySend(isOnlineNow(context))
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    trySend(true)
                }

                override fun onLost(network: Network) {
                    trySend(isOnlineNow(context))
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    trySend(networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
                }
            }
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            runCatching { cm.registerNetworkCallback(request, callback) }
            awaitClose {
                runCatching { cm.unregisterNetworkCallback(callback) }
            }
        }.distinctUntilChanged()
    }
}
