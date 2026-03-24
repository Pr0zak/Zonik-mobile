package com.zonik.app.media

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioVisualizerManager @Inject constructor() {

    private val _fftMagnitudes = MutableStateFlow(FloatArray(BAR_COUNT))
    val fftMagnitudes: StateFlow<FloatArray> = _fftMagnitudes.asStateFlow()

    private val _isEnabled = MutableStateFlow(false)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    companion object {
        const val BAR_COUNT = 32
    }

    fun updateMagnitudes(magnitudes: FloatArray) {
        _fftMagnitudes.value = magnitudes
    }

    fun setEnabled(enabled: Boolean) {
        _isEnabled.value = enabled
        if (!enabled) {
            _fftMagnitudes.value = FloatArray(BAR_COUNT)
        }
    }
}
