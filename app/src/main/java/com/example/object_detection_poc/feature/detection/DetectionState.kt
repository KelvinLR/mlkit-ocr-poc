package com.example.object_detection_poc.feature.detection

data class DetectionState (
    val fps: Int = 0,
    val detectedObjects: Int = 0,
    val recognizedText: String = "",
    val textBlocks: Int = 0,
    val currentModel: String = "Mock",
    val availableModels: List<String> = listOf("Mock", "MobileNet", "YOLOv5"),
    val isDetecting: Boolean = false,
    val hasCameraPermission: Boolean = false
)
