package com.prapps.fridaserverinstaller.rasp

enum class Severity {
    LOW, MEDIUM, HIGH, CRITICAL
}

data class DetectionResult(
    val technique: String,
    val detected: Boolean,
    val details: String,
    val severity: Severity,
    val rawData: List<String> = emptyList()
) {
    val icon: String
        get() = when (severity) {
            Severity.LOW -> "ℹ️"
            Severity.MEDIUM -> "⚠️"
            Severity.HIGH -> "🔶"
            Severity.CRITICAL -> "🔴"
        }
    
    val severityColor: Long
        get() = when (severity) {
            Severity.LOW -> 0xFF4CAF50  // Green
            Severity.MEDIUM -> 0xFFFF9800  // Orange
            Severity.HIGH -> 0xFFFF5722  // Deep Orange
            Severity.CRITICAL -> 0xFFF44336  // Red
        }
}

data class DetectionSummary(
    val totalChecks: Int,
    val detections: Int,
    val maxSeverity: Severity,
    val results: List<DetectionResult>,
    val scanTimeMs: Long
) {
    val threatLevel: String
        get() = when {
            detections == 0 -> "Clean"
            maxSeverity == Severity.CRITICAL -> "Critical"
            maxSeverity == Severity.HIGH -> "High Risk"
            maxSeverity == Severity.MEDIUM -> "Medium Risk"
            else -> "Low Risk"
        }
    
    val threatColor: Long
        get() = when {
            detections == 0 -> 0xFF4CAF50  // Green
            maxSeverity == Severity.CRITICAL -> 0xFFF44336
            maxSeverity == Severity.HIGH -> 0xFFFF5722
            maxSeverity == Severity.MEDIUM -> 0xFFFF9800
            else -> 0xFF8BC34A
        }
}
