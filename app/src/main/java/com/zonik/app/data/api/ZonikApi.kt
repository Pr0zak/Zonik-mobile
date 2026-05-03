package com.zonik.app.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.*

/**
 * Zonik native REST API client for downloads, discovery, and management.
 * Base URL is the same as the Subsonic API — the dynamic base URL interceptor handles it.
 */
interface ZonikApi {

    // --- Download Search ---

    @POST("api/download/search")
    suspend fun searchDownloads(@Body request: DownloadSearchRequest): DownloadSearchResponse

    // --- Download Trigger ---

    @POST("api/download/trigger")
    suspend fun triggerDownload(@Body request: DownloadTriggerRequest): DownloadTriggerResponse

    @POST("api/download/bulk")
    suspend fun bulkDownload(@Body request: BulkDownloadRequest): DownloadTriggerResponse

    @POST("api/download/cancel-transfer")
    suspend fun cancelTransfer(@Body request: CancelTransferRequest)

    // --- Download Status ---

    @GET("api/download/status")
    suspend fun getDownloadStatus(): DownloadStatusResponse

    // --- Logs ---

    @POST("api/logs")
    suspend fun uploadLogs(@Body request: LogUploadRequest): LogUploadResponse

    // --- Jobs ---

    @GET("api/jobs/active")
    suspend fun getActiveJobs(): List<JobInfo>

    @Headers("Accept: application/json")
    @GET("api/jobs")
    suspend fun getJobHistory(
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 50,
        @Query("type") type: String = "download,bulk_download"
    ): JobHistoryResponse

    @GET("api/jobs/{id}")
    suspend fun getJob(@Path("id") id: String): JobDetailResponse

    @GET("api/jobs/counts")
    suspend fun getJobCounts(
        @Query("type") type: String = "download"
    ): JobCountsResponse

    // --- Track Management ---

    @POST("api/tracks/bulk-delete")
    suspend fun bulkDeleteTracks(@Body request: BulkDeleteTracksRequest)

    // --- Device Pairing ---

    @POST("api/pair")
    suspend fun createPairingCode(): PairingCodeResponse

    @GET("api/pair/{code}")
    suspend fun checkPairingCode(@Path("code") code: String): PairingConfigResponse
}

@Serializable
data class BulkDeleteTracksRequest(
    val track_ids: List<String>
)

// --- Log Models ---

@Serializable
data class LogUploadRequest(
    val device: String,
    val app_version: String,
    val timestamp: String,
    val logs: String
)

@Serializable
data class LogUploadResponse(
    val id: String = "",
    val message: String = ""
)

// --- Request Models ---

@Serializable
data class DownloadSearchRequest(
    val artist: String? = null,
    val track: String? = null,
    val query: String? = null
)

@Serializable
data class DownloadTriggerRequest(
    val artist: String,
    val track: String,
    val username: String? = null,
    val filename: String? = null
)

@Serializable
data class BulkDownloadRequest(
    val tracks: List<BulkDownloadTrack>
)

@Serializable
data class BulkDownloadTrack(
    val artist: String,
    val track: String,
    val username: String? = null,
    val filename: String? = null
)

@Serializable
data class CancelTransferRequest(
    val username: String,
    val filename: String
)

// --- Response Models ---

@Serializable
data class DownloadSearchResponse(
    val results: List<DownloadResult> = emptyList()
)

@Serializable
data class DownloadResult(
    val username: String = "",
    val filename: String = "",
    val size: Long = 0,
    @SerialName("bit_rate") val bitRate: Int? = null,
    @SerialName("sample_rate") val sampleRate: Int? = null,
    @SerialName("bit_depth") val bitDepth: Int? = null,
    val speed: Long? = null,
    @SerialName("queue_length") val queueLength: Int? = null,
    @SerialName("slots_free") val slotsFree: Boolean? = null,
    @SerialName("free_upload_slots") val freeUploadSlots: Int? = null,
    @SerialName("upload_speed") val uploadSpeed: Long? = null,
    @SerialName("score") val score: Double? = null
) {
    val displayName: String
        get() {
            val parts = filename.replace("\\", "/").split("/")
            return parts.lastOrNull()?.substringBeforeLast(".") ?: filename
        }

    val format: String
        get() = filename.substringAfterLast(".").uppercase()

    val sizeMb: String
        get() = "%.1f MB".format(size / 1_048_576.0)
}

@Serializable
data class DownloadTriggerResponse(
    @SerialName("job_id") val jobId: String? = null,
    val message: String? = null
)

@Serializable
data class DownloadStatusResponse(
    val status: String = "",
    val transfers: List<TransferInfo> = emptyList()
)

@Serializable
data class TransferInfo(
    val username: String = "",
    val filename: String = "",
    val state: String = "",
    @SerialName("total_bytes") val totalBytes: Long = 0,
    @SerialName("received_bytes") val receivedBytes: Long = 0,
    val progress: Float = 0f,
    val speed: Long = 0,
    @SerialName("eta_seconds") val etaSeconds: Int? = null,
    @SerialName("save_path") val savePath: String? = null,
    val error: String? = null
) {
    val displayName: String
        get() {
            val parts = filename.replace("\\", "/").split("/")
            return parts.lastOrNull()?.substringBeforeLast(".") ?: filename
        }

    val speedMbps: String
        get() = if (speed > 0) "%.1f MB/s".format(speed / 1_048_576.0) else ""
}

@Serializable
data class JobInfo(
    val id: String = "",
    val type: String = "",
    val card: String? = null,
    val status: String = "",
    val progress: Int? = null,
    val total: Int? = null,
    val description: String? = null,
    val result: String? = null,
    val tracks: String? = null,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("finished_at") val finishedAt: String? = null
)

@Serializable
data class JobHistoryResponse(
    val items: List<JobInfo> = emptyList(),
    val total: Int = 0
)

@Serializable
data class JobCountsResponse(
    val pending: Int = 0,
    val running: Int = 0,
    val completed: Int = 0,
    val failed: Int = 0,
    val total: Int = 0
)

@Serializable
data class JobDetailResponse(
    val id: String = "",
    val type: String = "",
    val status: String = "",
    val progress: Int? = null,
    val total: Int? = null,
    val result: String? = null,
    val log: String? = null,
    val tracks: String? = null,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("finished_at") val finishedAt: String? = null
)

// --- Pairing Models ---

@Serializable
data class PairingCodeResponse(
    val code: String,
    val expires: String? = null
)

@Serializable
data class PairingConfigResponse(
    val status: String, // "pending", "ready", "expired"
    val url: String? = null,
    val username: String? = null,
    @SerialName("api_key") val apiKey: String? = null
)
