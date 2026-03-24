package com.zonik.app.media

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.zonik.app.data.DebugLog
import com.zonik.app.data.repository.SettingsRepository
import com.zonik.app.util.md5
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

@Singleton
class WaveformManager @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    companion object {
        const val BAR_COUNT = 200
    }

    private val cache = linkedMapOf<String, FloatArray>()
    private val maxCacheSize = 50

    private val _currentWaveform = MutableStateFlow<FloatArray?>(null)
    val currentWaveform: StateFlow<FloatArray?> = _currentWaveform.asStateFlow()

    private var currentTrackId: String? = null
    private var extractionJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun loadWaveform(trackId: String) {
        if (trackId == currentTrackId && _currentWaveform.value != null) return
        currentTrackId = trackId

        // Check cache
        cache[trackId]?.let {
            _currentWaveform.value = it
            return
        }

        // Start background extraction
        _currentWaveform.value = null
        extractionJob?.cancel()
        extractionJob = scope.launch {
            try {
                val url = buildStreamUrl(trackId)
                val waveform = extractWaveform(url)
                // Store in cache (evict oldest if full)
                synchronized(cache) {
                    if (cache.size >= maxCacheSize) {
                        cache.remove(cache.keys.first())
                    }
                    cache[trackId] = waveform
                }
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

    private suspend fun buildStreamUrl(trackId: String): String {
        val config = settingsRepository.serverConfig.first()
            ?: throw IllegalStateException("No server config")
        val salt = (1..16).map { "abcdefghijklmnopqrstuvwxyz0123456789".random() }.joinToString("")
        val token = md5("${config.apiKey}$salt")
        // No maxBitRate — we want original quality for accurate waveform
        return "${config.url.trimEnd('/')}/rest/stream.view?id=$trackId&estimateContentLength=true&u=${config.username}&t=$token&s=$salt&v=1.16.1&c=ZonikApp"
    }

    private fun extractWaveform(url: String): FloatArray {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(url)

            // Find audio track
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
                    // Estimate: assume 5 minutes if unknown
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
                    // Feed input
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

                    // Read output
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

                // Handle remaining samples in last bar
                if (currentBar < BAR_COUNT && barSampleCount > 0) {
                    amplitudes[currentBar] = sqrt(sumSquares / barSampleCount).toFloat()
                    currentBar++
                }

                // Normalize to 0..1
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
