package com.zonik.app.data.api

import com.zonik.app.data.DebugLog
import com.zonik.app.data.repository.SettingsRepository
import com.zonik.app.util.md5
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

data class JobProgress(
    val jobId: String,
    val status: String = "pending",
    val progress: Long = 0L,
    val total: Long = 0L,
    val pct: Float = 0f,
    val message: String? = null,
    val error: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

@Singleton
class DownloadProgressClient @Inject constructor(
    private val settingsRepository: SettingsRepository
) {

    companion object {
        private const val TAG = "DLProgressWS"
        private const val INITIAL_RECONNECT_MS = 1_500L
        private const val MAX_RECONNECT_MS = 30_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val _progress = MutableStateFlow<Map<String, JobProgress>>(emptyMap())
    val progress: StateFlow<Map<String, JobProgress>> = _progress.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private var refcount = 0
    private var connectJob: Job? = null
    private var webSocket: WebSocket? = null
    private var reconnectMs = INITIAL_RECONNECT_MS

    private val client by lazy {
        OkHttpClient.Builder()
            .pingInterval(20, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    suspend fun acquire() {
        mutex.withLock {
            refcount++
            if (refcount == 1) {
                startConnectLoop()
            }
        }
    }

    suspend fun release() {
        mutex.withLock {
            refcount = max(0, refcount - 1)
            if (refcount == 0) {
                stopConnectLoop()
            }
        }
    }

    fun setJobStatus(jobId: String, status: String, message: String? = null) {
        _progress.update { current ->
            val existing = current[jobId] ?: JobProgress(jobId = jobId)
            current + (jobId to existing.copy(
                status = status,
                message = message ?: existing.message,
                updatedAt = System.currentTimeMillis()
            ))
        }
    }

    fun clearTerminal() {
        _progress.update { current ->
            current.filterValues { p ->
                p.status != "completed" && p.status != "failed" && p.status != "cancelled"
            }
        }
    }

    private fun startConnectLoop() {
        if (connectJob?.isActive == true) return
        connectJob = scope.launch {
            while (refcount > 0) {
                try {
                    val config = settingsRepository.serverConfig.first()
                    if (config == null) {
                        delay(2_000)
                        continue
                    }
                    connectOnce(config.url, config.username, config.apiKey)
                    // connectOnce returns when socket closes
                    if (refcount > 0) {
                        delay(reconnectMs)
                        reconnectMs = min(reconnectMs * 2, MAX_RECONNECT_MS)
                    }
                } catch (e: Exception) {
                    DebugLog.e(TAG, "Connect loop error: ${e.message}")
                    delay(reconnectMs)
                    reconnectMs = min(reconnectMs * 2, MAX_RECONNECT_MS)
                }
            }
        }
    }

    private fun stopConnectLoop() {
        connectJob?.cancel()
        connectJob = null
        webSocket?.close(1000, "client released")
        webSocket = null
        _connected.value = false
        reconnectMs = INITIAL_RECONNECT_MS
    }

    private suspend fun connectOnce(serverUrl: String, username: String, apiKey: String) {
        val wsUrl = buildWsUrl(serverUrl, username, apiKey)
        val request = Request.Builder().url(wsUrl).build()
        val opened = kotlinx.coroutines.CompletableDeferred<Unit>()
        val closed = kotlinx.coroutines.CompletableDeferred<Unit>()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                DebugLog.d(TAG, "WS opened ${response.code}")
                _connected.value = true
                reconnectMs = INITIAL_RECONNECT_MS
                if (!opened.isCompleted) opened.complete(Unit)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                DebugLog.d(TAG, "WS closing $code $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                DebugLog.d(TAG, "WS closed $code $reason")
                _connected.value = false
                if (!closed.isCompleted) closed.complete(Unit)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                DebugLog.e(TAG, "WS failure ${t.message} (code=${response?.code})")
                _connected.value = false
                if (!closed.isCompleted) closed.complete(Unit)
            }
        }

        webSocket = client.newWebSocket(request, listener)
        try {
            closed.await()
        } finally {
            webSocket?.cancel()
            webSocket = null
            _connected.value = false
        }
    }

    private fun buildWsUrl(serverUrl: String, username: String, apiKey: String): String {
        val base = serverUrl.trimEnd('/')
        val wsBase = when {
            base.startsWith("https://") -> "wss://" + base.removePrefix("https://")
            base.startsWith("http://") -> "ws://" + base.removePrefix("http://")
            else -> "ws://$base"
        }
        val salt = (1..16).map { "abcdefghijklmnopqrstuvwxyz0123456789".random() }.joinToString("")
        val token = md5("$apiKey$salt")
        return "$wsBase/api/ws?u=$username&t=$token&s=$salt&v=1.16.1&c=ZonikApp&f=json"
    }

    private fun handleMessage(text: String) {
        try {
            val obj = JSONObject(text)
            val type = obj.optString("type", "")
            when (type) {
                "job_update" -> handleJobUpdate(obj)
                "transfer_progress" -> handleTransferProgress(obj)
                else -> { /* ignore */ }
            }
        } catch (e: Exception) {
            DebugLog.e(TAG, "Parse error: ${e.message}")
        }
    }

    private fun handleJobUpdate(obj: JSONObject) {
        val data = obj.optJSONObject("data") ?: obj
        val jobId = data.optString("job_id", data.optString("id", ""))
        if (jobId.isBlank()) return
        val status = data.optString("status", "")
        val progress = data.optLong("progress", 0L)
        val total = data.optLong("total", 0L)
        val message = if (data.has("message")) data.optString("message").takeIf { it.isNotBlank() } else null
        val error = if (data.has("error")) data.optString("error").takeIf { it.isNotBlank() } else null
        _progress.update { current ->
            val existing = current[jobId] ?: JobProgress(jobId = jobId)
            val pct = computePct(progress, total, existing.pct)
            current + (jobId to existing.copy(
                status = if (status.isNotBlank()) status else existing.status,
                progress = if (progress > 0) progress else existing.progress,
                total = if (total > 0) total else existing.total,
                pct = pct,
                message = message ?: existing.message,
                error = error ?: existing.error,
                updatedAt = System.currentTimeMillis()
            ))
        }
    }

    private fun handleTransferProgress(obj: JSONObject) {
        val data = obj.optJSONObject("data") ?: obj
        val jobId = data.optString("job_id", "")
        if (jobId.isBlank()) return
        val received = data.optLong("received_bytes", data.optLong("progress", 0L))
        val total = data.optLong("total_bytes", data.optLong("total", 0L))
        _progress.update { current ->
            val existing = current[jobId] ?: JobProgress(jobId = jobId)
            val pct = computePct(received, total, existing.pct)
            current + (jobId to existing.copy(
                status = if (existing.status.isBlank() || existing.status == "pending") "running" else existing.status,
                progress = if (received > 0) received else existing.progress,
                total = if (total > 0) total else existing.total,
                pct = pct,
                updatedAt = System.currentTimeMillis()
            ))
        }
    }

    private fun computePct(progress: Long, total: Long, fallback: Float): Float {
        if (total <= 0L) return fallback
        val raw = (progress.toFloat() / total.toFloat()) * 100f
        return raw.coerceIn(0f, 100f)
    }
}
