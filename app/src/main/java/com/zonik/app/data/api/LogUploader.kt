package com.zonik.app.data.api

import android.os.Build
import com.zonik.app.data.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LogUploader @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val zonikApi: ZonikApi
) {

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

    private fun getAppVersion(): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }
    }
}
