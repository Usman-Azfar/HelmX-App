package com.yourname.helmx

data class HelmetData(
    val batteryLevel: Int = 0,
    val speed: Float = 0f,
    val isDrowsy: Boolean = false,
    val isCrashDetected: Boolean = false,
    val distance: Float = 0f,
    val temperature: Float = 0f,
    val humidity: Float = 0f,
    val airQuality: String = "Unknown",
    val destination: String = "",
    val connectionStatus: String = "Disconnected"
)
