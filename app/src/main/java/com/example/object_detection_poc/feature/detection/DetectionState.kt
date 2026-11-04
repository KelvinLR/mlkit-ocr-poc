package com.example.object_detection_poc.feature.detection

data class DetectionState (
    val fps: Int = 0,
    val recognizedText: String = "",
    val textBlocks: Int = 0,
    val isDetecting: Boolean = false,
    val hasCameraPermission: Boolean = false
)