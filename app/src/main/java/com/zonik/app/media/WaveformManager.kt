package com.zonik.app.media

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.zonik.app.data.DebugLog
import com.zonik.app.data.repository.SettingsRepository
import com.zonik.app.util.md5
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

@Singleton
class WaveformManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val okHttpClient: OkHttpClient
) {
    companion object {
        const val BAR_COUNT = 200
    }

    private val waveformDir = File(context.filesDir, "waveforms").also { it.mkdirs() }

    private val _currentWaveform = MutableStateFlow<FloatArray?>(null)
    val currentWaveform: StateFlow<FloatArray?> = _currentWaveform.asStateFlow()

    private var currentTrackId: String? = null
    private var extractionJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun loadWaveform(trackId: String) {
        if (trackId == currentTrackId && _currentWaveform.value != null) return
        currentTrackId = trackId

        // Check persistent file cache (instant)
        readFromDisk(trackId)?.let {
            _currentWaveform.value = it
            return
        }

        // Start background extraction
        _currentWaveform.value = null
        extractionJob?.cancel()
        extractionJob = scope.launch {
            try {
                // Try server-side waveform API first (fast — server has file on disk)
                val waveform = tryServerWaveform(trackId)
                    ?: extractWaveformFromStream(trackId)

                // Persist to disk
                writeToDisk(trackId, waveform)

                if (currentTrackId == trackId) {
                    _currentWaveform.value = waveform
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DebugLog.w("Waveform", "Extraction failed: ${e.message}")
            }
        }
    }

    fun clear() {
        _currentWaveform.value = null
        currentTrackId = null
        extractionJob?.cancel()
    }

    // --- Persistent file cache ---

    private fun readFromDisk(trackId: String): FloatArray? {
        val file = File(waveformDir, "$trackId.wfm")
        if (!file.exists()) return null
        return try {
            val bytes = file.readBytes()
            if (bytes.size != BAR_COUNT * 4) return null
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            FloatArray(BAR_COUNT) { buffer.float }
        } catch (e: Exception) {
            null
        }
    }

    private fun writeToDisk(trackId: String, waveform: FloatArray) {
        try {
            val buffer = ByteBuffer.allocate(BAR_COUNT * 4).order(ByteOrder.LITTLE_ENDIAN)
            waveform.forEach { buffer.putFloat(it) }
            File(waveformDir, "$trackId.wfm").writeBytes(buffer.array())
        } catch (e: Exception) {
            DebugLog.w("Waveform", "Cache write failed: ${e.message}")
        }
    }

    // --- Server-side waveform (fast path) ---

    private suspend fun tryServerWaveform(trackId: String): FloatArray? {
        return try {
            val config = settingsRepository.serverConfig.first() ?: return null
            val salt = (1..16).map { "abcdefghijklmnopqrstuvwxyz0123456789".random() }.joinToString("")
            val token = md5("${config.apiKey}$salt")
            val url = "${config.url.trimEnd('/')}/api/tracks/$trackId/waveform?bars=$BAR_COUNT&u=${config.username}&t=$token&s=$salt"

            val request = Request.Builder().url(url).get().build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return null

            val body = response.body?.string() ?: return null
            // Parse JSON array: {"waveform": [0.1, 0.5, ...]} or bare [0.1, 0.5, ...]
            val arrayStr = if (body.contains("\"waveform\"")) {
                body.substringAfter("[").substringBeforeLast("]")
            } else if (body.trimStart().startsWith("[")) {
                body.trim().removePrefix("[").removeSuffix("]")
            } else return null

            val values = arrayStr.split(",").mapNotNull { it.trim().toFloatOrNull() }
            if (values.size < 10) return null
            DebugLog.d("Waveform", "Got server waveform for $trackId (${values.size} bars)")
            values.toFloatArray()
        } catch (e: Exception) {
            // Server doesn't support waveform API — fall back silently
            null
        }
    }

    // --- Client-side extraction (slow fallback) ---

    private suspend fun extractWaveformFromStream(trackId: String): FloatArray {
        val url = buildStreamUrl(trackId)
        return extractWaveform(url)
    }

    private suspend fun buildStreamUrl(trackId: String): String {
        val config = settingsRepository.serverConfig.first()
            ?: throw IllegalStateException("No server config")
        val salt = (1..16).map { "abcdefghijklmnopqrstuvwxyz0123456789".random() }.joinToString("")
        val token = md5("${config.apiKey}$salt")
        return "${config.url.trimEnd('/')}/rest/stream.view?id=$trackId&estimateContentLength=true&u=${config.username}&t=$token&s=$salt&v=1.16.1&c=ZonikApp"
    }

    private fun extractWaveform(url: String): FloatArray {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(url)

            var audioTrackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    break
                }
            }
            if (audioTrackIndex == -1) return FloatArray(BAR_COUNT)

            extractor.selectTrack(audioTrackIndex)
            val format = extractor.getTrackFormat(audioTrackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return FloatArray(BAR_COUNT)
            val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val duration = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                format.getLong(MediaFormat.KEY_DURATION)
            } else 0L

            val decoder = MediaCodec.createDecoderByType(mime)
            try {
                decoder.configure(format, null, null, 0)
                decoder.start()

                val totalSamples = if (duration > 0) {
                    (duration / 1_000_000.0 * sampleRate * channels).toLong()
                } else {
                    (300L * sampleRate * channels)
                }
                val samplesPerBar = maxOf(1L, totalSamples / BAR_COUNT)

                val amplitudes = FloatArray(BAR_COUNT)
                var currentBar = 0
                var sumSquares = 0.0
                var barSampleCount = 0L
                var inputDone = false
                var outputDone = false
                val bufferInfo = MediaCodec.BufferInfo()

                while (!outputDone && currentBar < BAR_COUNT) {
                    if (!inputDone) {
                        val inputIndex = decoder.dequeueInputBuffer(10_000)
                        if (inputIndex >= 0) {
                            val inputBuffer = decoder.getInputBuffer(inputIndex)!!
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                decoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                decoder.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }

                    val outputIndex = decoder.dequeueOutputBuffer(bufferInfo, 10_000)
                    if (outputIndex >= 0) {
                        val outputBuffer = decoder.getOutputBuffer(outputIndex)!!
                        val shortBuffer = outputBuffer.asShortBuffer()
                        while (shortBuffer.hasRemaining() && currentBar < BAR_COUNT) {
                            val sample = shortBuffer.get().toFloat() / Short.MAX_VALUE
                            sumSquares += sample * sample
                            barSampleCount++

                            if (barSampleCount >= samplesPerBar) {
                                amplitudes[currentBar] = sqrt(sumSquares / barSampleCount).toFloat()
                                currentBar++
                                sumSquares = 0.0
                                barSampleCount = 0
                            }
                        }
                        decoder.releaseOutputBuffer(outputIndex, false)

                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                    }
                }

                if (currentBar < BAR_COUNT && barSampleCount > 0) {
                    amplitudes[currentBar] = sqrt(sumSquares / barSampleCount).toFloat()
                }

                val max = amplitudes.maxOrNull() ?: 1f
                if (max > 0f) {
                    for (i in amplitudes.indices) {
                        amplitudes[i] = (amplitudes[i] / max).coerceIn(0f, 1f)
                    }
                }

                return amplitudes
            } finally {
                try { decoder.stop() } catch (_: Exception) {}
                try { decoder.release() } catch (_: Exception) {}
            }
        } finally {
            try { extractor.release() } catch (_: Exception) {}
        }
    }
}
