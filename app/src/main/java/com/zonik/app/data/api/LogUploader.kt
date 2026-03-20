package com.zonik.app.data.api

import android.os.Build
import com.zonik.app.data.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class GistRequest(
    val description: String,
    val public: Boolean = false,
    val files: Map<String, GistFile>
)

@Serializable
data class GistFile(val content: String)

@Serializable
data class GistResponse(
    val html_url: String = "",
    val id: String = ""
)

@Singleton
class LogUploader @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val zonikApi: ZonikApi
) {

    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private var lastGistId: String? = null

    /**
     * Upload current debug logs to the Zonik server.
     * Returns the log ID or null on failure.
     */
    suspend fun uploadLogsToServer(): String? = withContext(Dispatchers.IO) {
        try {
            val device = "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"
            val appVersion = getAppVersion()
            val timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
            val logs = DebugLog.getPersistedLogs()

            val response = zonikApi.uploadLogs(
                LogUploadRequest(
                    device = device,
                    app_version = appVersion,
                    timestamp = timestamp,
                    logs = logs
                )
            )

            DebugLog.d("LogUploader", "Logs uploaded to server: ${response.id}")
            response.id
        } catch (e: Exception) {
            DebugLog.e("LogUploader", "Server upload failed", e)
            null
        }
    }

    /**
     * Upload current debug logs to a private GitHub Gist.
     * Returns the Gist URL or null on failure.
     */
    suspend fun uploadLogs(githubToken: String): String? = withContext(Dispatchers.IO) {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val deviceInfo = "Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"
            val appVersion = "App: Zonik v${getAppVersion()}"
            val header = "=== Zonik Debug Logs ===\n$timestamp\n$deviceInfo\n$appVersion\n${"=".repeat(40)}\n\n"

            val logs = header + DebugLog.getPersistedLogs()

            val gistBody = GistRequest(
                description = "Zonik debug logs - $timestamp",
                public = false,
                files = mapOf("zonik-logs.txt" to GistFile(content = logs))
            )

            val requestBody = json.encodeToString(gistBody)
                .toRequestBody("application/json".toMediaType())

            // Update existing gist or create new one
            val url = if (lastGistId != null) {
                "https://api.github.com/gists/$lastGistId"
            } else {
                "https://api.github.com/gists"
            }

            val method = if (lastGistId != null) "PATCH" else "POST"

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $githubToken")
                .header("Accept", "application/vnd.github.v3+json")
                .method(method, requestBody)
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                DebugLog.e("LogUploader", "Gist upload failed: ${response.code} ${response.message}")
                // If patching failed (gist deleted?), try creating new
                if (lastGistId != null && response.code == 404) {
                    lastGistId = null
                    return@withContext uploadLogs(githubToken)
                }
                return@withContext null
            }

            val body = response.body?.string() ?: return@withContext null
            val gist = json.decodeFromString<GistResponse>(body)
            lastGistId = gist.id

            DebugLog.d("LogUploader", "Logs uploaded: ${gist.html_url}")
            gist.html_url
        } catch (e: Exception) {
            DebugLog.e("LogUploader", "Upload failed", e)
            null
        }
    }

    private fun getAppVersion(): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }
    }
}
