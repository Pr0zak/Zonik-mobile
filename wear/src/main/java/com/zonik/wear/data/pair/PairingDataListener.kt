package com.zonik.wear.data.pair

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.zonik.core.model.ServerConfig
import com.zonik.wear.WearApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Receives a ServerConfig pushed from a paired phone over the Wear OS Data
 * Layer. The phone calls MessageClient.sendMessage(nodeId, PATH, json bytes)
 * and we save the decoded config to WearSettingsRepository — the nav host's
 * config observer then routes us out of the pairing screen automatically.
 */
class PairingDataListener : WearableListenerService() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != PATH) return
        val payload = try {
            json.decodeFromString(WirePayload.serializer(), String(event.data, Charsets.UTF_8))
        } catch (e: Exception) {
            Log.w(TAG, "Bad pairing payload from ${event.sourceNodeId}: ${e.message}")
            return
        }
        if (payload.url.isBlank() || payload.username.isBlank() || payload.apiKey.isBlank()) {
            Log.w(TAG, "Pairing payload missing fields — ignoring")
            return
        }
        Log.i(TAG, "Pairing config received from ${event.sourceNodeId} (url=${payload.url})")
        val app = applicationContext as WearApp
        scope.launch {
            app.settings.save(
                ServerConfig(
                    url = payload.url.trimEnd('/'),
                    username = payload.username,
                    apiKey = payload.apiKey,
                )
            )
        }
    }

    @Serializable
    private data class WirePayload(
        val url: String,
        val username: String,
        val apiKey: String,
    )

    private companion object {
        const val TAG = "PairingDataListener"
        const val PATH = "/zonik/pair"
    }
}
