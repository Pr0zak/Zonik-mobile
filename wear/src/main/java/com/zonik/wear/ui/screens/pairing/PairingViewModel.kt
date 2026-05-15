package com.zonik.wear.ui.screens.pairing

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zonik.core.model.ServerConfig
import com.zonik.wear.data.repository.WearSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Drives the watch's first-launch pairing flow:
 *   EnterUrl -> Pending(code) -> Paired (or Expired / Error)
 *
 * Reuses the server-side /api/pair endpoint already used by the TV app.
 */
class PairingViewModel(
    private val settings: WearSettingsRepository,
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val _state = MutableStateFlow<PairingState>(PairingState.EnterUrl(""))
    val state: StateFlow<PairingState> = _state.asStateFlow()

    fun updateUrl(url: String) {
        val current = _state.value
        if (current is PairingState.EnterUrl) _state.value = current.copy(url = url)
    }

    fun requestCode() {
        val st = _state.value as? PairingState.EnterUrl ?: return
        val normalized = normalizeUrl(st.url)
        if (normalized.isBlank()) {
            _state.value = st.copy(error = "Enter a URL like http://zonik:3000")
            return
        }
        _state.value = PairingState.Requesting(normalized)
        viewModelScope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    http.newCall(
                        Request.Builder()
                            .url("$normalized/api/pair")
                            .post(okhttp3.internal.EMPTY_REQUEST)
                            .build()
                    ).execute()
                }
                resp.use {
                    if (!it.isSuccessful) {
                        _state.value = PairingState.EnterUrl(
                            url = st.url,
                            error = "Server responded ${it.code} — wrong URL?"
                        )
                        return@launch
                    }
                    val body = it.body?.string().orEmpty()
                    val payload = json.decodeFromString(CodeResponse.serializer(), body)
                    _state.value = PairingState.Pending(normalized, payload.code)
                    pollUntilReady(normalized, payload.code)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Pair request failed: ${e.message}")
                _state.value = PairingState.EnterUrl(
                    url = st.url,
                    error = "Couldn't reach server: ${e.javaClass.simpleName}"
                )
            }
        }
    }

    fun cancelAndStartOver() {
        val st = _state.value
        val url = when (st) {
            is PairingState.Pending -> st.url.removePrefix("http://").removePrefix("https://")
            is PairingState.Requesting -> st.url.removePrefix("http://").removePrefix("https://")
            is PairingState.EnterUrl -> st.url
            else -> ""
        }
        _state.value = PairingState.EnterUrl(url)
    }

    private suspend fun pollUntilReady(serverUrl: String, code: String) {
        while (true) {
            delay(2_000)
            val st = _state.value
            if (st !is PairingState.Pending || st.code != code) return
            try {
                val resp = withContext(Dispatchers.IO) {
                    http.newCall(Request.Builder().url("$serverUrl/api/pair/$code").build()).execute()
                }
                resp.use {
                    val body = it.body?.string().orEmpty()
                    val payload = json.decodeFromString(StatusResponse.serializer(), body)
                    when (payload.status) {
                        "ready" -> {
                            val u = payload.url ?: serverUrl
                            val user = payload.username
                            val key = payload.api_key
                            if (user.isNullOrBlank() || key.isNullOrBlank()) {
                                _state.value = PairingState.EnterUrl(
                                    url = serverUrl,
                                    error = "Server returned ready but missing credentials"
                                )
                                return
                            }
                            settings.save(ServerConfig(url = u, username = user, apiKey = key))
                            _state.value = PairingState.Paired(u, user)
                            return
                        }
                        "expired" -> {
                            _state.value = PairingState.EnterUrl(
                                url = serverUrl,
                                error = "Code expired — try again"
                            )
                            return
                        }
                        else -> { /* still pending */ }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Poll failed: ${e.message}")
                // Keep polling — transient network blip.
            }
        }
    }

    private fun normalizeUrl(input: String): String {
        val trimmed = input.trim().trimEnd('/')
        if (trimmed.isEmpty()) return ""
        return when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            else -> "http://$trimmed"
        }
    }

    @Serializable
    private data class CodeResponse(val code: String, val expires: String? = null)

    @Serializable
    private data class StatusResponse(
        val status: String,
        val url: String? = null,
        val username: String? = null,
        val api_key: String? = null,
    )

    private companion object {
        const val TAG = "WearPairing"
    }
}

sealed class PairingState {
    data class EnterUrl(val url: String, val error: String? = null) : PairingState()
    data class Requesting(val url: String) : PairingState()
    data class Pending(val url: String, val code: String) : PairingState()
    data class Paired(val url: String, val username: String) : PairingState()
}
