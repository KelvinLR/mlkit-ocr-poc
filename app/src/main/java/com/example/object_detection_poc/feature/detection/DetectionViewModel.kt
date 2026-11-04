package com.example.object_detection_poc.feature.detection

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class DetectionViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(DetectionState())
    val uiState: StateFlow<DetectionState> = _uiState.asStateFlow()

    // FPS calculado com base na quantidade de frames processados pelo OCR por segundo
    private var frameCount = 0
    private var windowStartMs = SystemClock.elapsedRealtime()

    fun onPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(hasCameraPermission = granted) }
    }

    fun onTextRecognized(text: String, blockCount: Int) {
        frameCount++
        val now = SystemClock.elapsedRealtime()
        val elapsed = now - windowStartMs

        _uiState.update { current ->
            if (elapsed >= 1_000) {
                val fps = (frameCount * 1_000 / elapsed).toInt()
                frameCount = 0
                windowStartMs = now
                current.copy(fps = fps, recognizedText = text, textBlocks = blockCount)
            } else {
                current.copy(recognizedText = text, textBlocks = blockCount)
            }
        }
    }

    fun setDetecting(detecting: Boolean) {
        _uiState.update { it.copy(isDetecting = detecting) }
    }
}