package com.zonik.app.ui.screens.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zonik.app.data.DebugLog
import com.zonik.app.data.repository.SettingsRepository
import com.zonik.app.model.PingResponse
import com.zonik.app.model.ServerConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class LoginUiState(
    val serverUrl: String = "",
    val username: String = "",
    val apiKey: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSuccess: Boolean = false,
    val pairingCode: String? = null,
    val isPairingActive: Boolean = false
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val zonikApi: com.zonik.app.data.api.ZonikApi
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()
    private var pairingJob: kotlinx.coroutines.Job? = null

    fun updateServerUrl(url: String) {
        _uiState.value = _uiState.value.copy(serverUrl = url, error = null)
    }

    fun updateUsername(username: String) {
        _uiState.value = _uiState.value.copy(username = username, error = null)
    }

    fun updateApiKey(apiKey: String) {
        _uiState.value = _uiState.value.copy(apiKey = apiKey, error = null)
    }

    fun login() {
        val state = _uiState.value
        if (state.serverUrl.isBlank() || state.username.isBlank() || state.apiKey.isBlank()) {
            _uiState.value = state.copy(error = "All fields are required")
            return
        }

        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true, error = null)
            try {
                val config = ServerConfig(
                    url = state.serverUrl.trimEnd('/'),
                    username = state.username,
                    apiKey = state.apiKey
                )

                // Test connection before saving
                val result = testConnection(config)
                if (result != null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result
                    )
                    return@launch
                }

                settingsRepository.saveServerConfig(config)
                _uiState.value = _uiState.value.copy(isLoading = false, isSuccess = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Connection failed"
                )
            }
        }
    }

    private suspend fun testConnection(config: ServerConfig): String? =
        withContext(Dispatchers.IO) {
            try {
                DebugLog.d("Login", "Testing connection to ${config.url}")
                val client = OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .build()

                val salt = (1..16).map { "abcdefghijklmnopqrstuvwxyz0123456789".random() }
                    .joinToString("")
                val token = md5("${config.apiKey}$salt")

                val url = "${config.url}/rest/ping.view" +
                    "?u=${config.username}&t=$token&s=$salt&v=1.16.1&c=ZonikApp&f=json"

                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()

                DebugLog.d("Login", "Ping response: ${response.code}")

                if (!response.isSuccessful) {
                    DebugLog.e("Login", "Server returned ${response.code}")
                    return@withContext "Server returned ${response.code}"
                }

                val body = response.body?.string()
                    ?: return@withContext "Empty response from server"

                DebugLog.d("Login", "Ping body: $body")

                val json = Json { ignoreUnknownKeys = true }
                val ping = json.decodeFromString<PingResponse>(body)

                if (!ping.response.isOk) {
                    val error = ping.response.error
                    DebugLog.e("Login", "Auth failed: ${error?.message}")
                    return@withContext error?.message ?: "Authentication failed"
                }

                DebugLog.d("Login", "Connection test passed")
                null // success
            } catch (e: java.net.UnknownHostException) {
                DebugLog.e("Login", "UnknownHostException", e)
                "Server not found. Check the URL."
            } catch (e: java.net.ConnectException) {
                DebugLog.e("Login", "ConnectException", e)
                "Cannot connect to server. Check the URL and port."
            } catch (e: java.net.SocketTimeoutException) {
                DebugLog.e("Login", "SocketTimeoutException", e)
                "Connection timed out. Server may be offline."
            } catch (e: Exception) {
                DebugLog.e("Login", "Exception", e)
                "Connection failed: ${e.message}"
            }
        }

    fun startPairing() {
        pairingJob?.cancel()
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isPairingActive = true, error = null)
                val response = zonikApi.createPairingCode()
                _uiState.value = _uiState.value.copy(pairingCode = response.code)
                DebugLog.d("Login", "Pairing code: ${response.code}")

                // Poll for config every 2 seconds
                pairingJob = launch {
                    while (true) {
                        kotlinx.coroutines.delay(2000)
                        try {
                            val config = zonikApi.checkPairingCode(response.code)
                            if (config.status == "ready" && config.url != null && config.username != null && config.apiKey != null) {
                                val serverConfig = ServerConfig(
                                    url = config.url.trimEnd('/'),
                                    username = config.username,
                                    apiKey = config.apiKey
                                )
                                // Test connection before saving
                                val testResult = testConnection(serverConfig)
                                if (testResult != null) {
                                    _uiState.value = _uiState.value.copy(error = testResult, isPairingActive = false, pairingCode = null)
                                    return@launch
                                }
                                settingsRepository.saveServerConfig(serverConfig)
                                _uiState.value = _uiState.value.copy(isSuccess = true, isPairingActive = false)
                                DebugLog.d("Login", "Paired successfully via code ${response.code}")
                                return@launch
                            } else if (config.status == "expired") {
                                _uiState.value = _uiState.value.copy(error = "Pairing code expired", isPairingActive = false, pairingCode = null)
                                return@launch
                            }
                        } catch (_: Exception) {
                            // Server not reachable or code not found — keep polling
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to get pairing code: ${e.message}",
                    isPairingActive = false
                )
            }
        }
    }

    fun stopPairing() {
        pairingJob?.cancel()
        pairingJob = null
        _uiState.value = _uiState.value.copy(isPairingActive = false, pairingCode = null)
    }

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val bytes = digest.digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var apiKeyVisible by remember { mutableStateOf(false) }
    val isTv = com.zonik.app.ui.util.isTv()

    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) onLoginSuccess()
    }

    // Auto-start pairing on TV
    LaunchedEffect(isTv) {
        if (isTv && uiState.pairingCode == null && !uiState.isPairingActive) {
            viewModel.startPairing()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Zonik",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Self-hosted music player",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(48.dp))

        // TV pairing code mode
        if (uiState.pairingCode != null) {
            Text(
                text = "Enter this code on your Zonik server",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = uiState.pairingCode!!,
                style = MaterialTheme.typography.displayLarge.copy(
                    letterSpacing = 12.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Waiting for pairing...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            if (uiState.error != null) {
                Text(
                    text = uiState.error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            OutlinedButton(onClick = { viewModel.stopPairing() }) {
                Text("Enter manually instead")
            }
            return@Column
        }

        // Show pairing button (for both TV and phone)
        if (!isTv && !uiState.isPairingActive) {
            OutlinedButton(
                onClick = { viewModel.startPairing() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Pair with code")
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        OutlinedTextField(
            value = uiState.serverUrl,
            onValueChange = viewModel::updateServerUrl,
            label = { Text("Server URL") },
            placeholder = { Text("https://zonik.example.com") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = uiState.username,
            onValueChange = viewModel::updateUsername,
            label = { Text("Username") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = uiState.apiKey,
            onValueChange = viewModel::updateApiKey,
            label = { Text("API Key") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                    Icon(
                        imageVector = if (apiKeyVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                        contentDescription = if (apiKeyVisible) "Hide API key" else "Show API key"
                    )
                }
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (uiState.error != null) {
            Text(
                text = uiState.error!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        Button(
            onClick = viewModel::login,
            enabled = !uiState.isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Connect")
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "v0.1.18",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
