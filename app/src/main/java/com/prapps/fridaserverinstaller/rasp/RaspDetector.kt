package com.prapps.fridaserverinstaller.rasp

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Socket

class RaspDetector {
    
    companion object {
        private const val TAG = "RaspDetector"
        
        init {
            try {
                System.loadLibrary("rasp_detector")
                Log.i(TAG, "Native library loaded")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native library", e)
            }
        }
    }
    
    // Native methods
    private external fun nativeScanMaps(): Array<String>
    private external fun nativeScanSmaps(): Array<String>
    private external fun nativeScanFds(): Array<String>
    private external fun nativeCheckPort(port: Int): Boolean
    private external fun nativeCheckPtrace(): Boolean
    private external fun nativeCheckThreads(): Array<String>
    private external fun nativeCheckDbus(port: Int): Boolean
    private external fun nativeCheckEnvironment(): Array<String>
    
    fun runFullScan(): DetectionSummary {
        val startTime = System.currentTimeMillis()
        val results = mutableListOf<DetectionResult>()
        
        // 1. Maps scan (native)
        results.add(scanMaps())
        
        // 2. Smaps scan (native) 
        results.add(scanSmaps())
        
        // 3. FD scan (native)
        results.add(scanFds())
        
        // 4. Frida port check
        results.add(checkFridaPort())
        
        // 5. Ptrace detection (native)
        results.add(checkPtrace())
        
        // 6. Thread names (native)
        results.add(checkThreads())
        
        // 7. D-Bus check (native)
        results.add(checkDbus())
        
        // 8. Environment check (native)
        results.add(checkEnvironment())
        
        // 9. Process name check
        results.add(checkFridaProcesses())
        
        val scanTime = System.currentTimeMillis() - startTime
        val detections = results.filter { it.detected }
        val maxSeverity = detections.maxOfOrNull { it.severity } ?: Severity.LOW
        
        return DetectionSummary(
            totalChecks = results.size,
            detections = detections.size,
            maxSeverity = maxSeverity,
            results = results,
            scanTimeMs = scanTime
        )
    }
    
    private fun scanMaps(): DetectionResult {
        return try {
            val findings = nativeScanMaps()
            DetectionResult(
                technique = "Memory Maps",
                detected = findings.isNotEmpty(),
                details = if (findings.isEmpty()) "Clean" else "${findings.size} suspicious",
                severity = if (findings.size > 3) Severity.CRITICAL else if (findings.isNotEmpty()) Severity.HIGH else Severity.LOW,
                rawData = findings.toList()
            )
        } catch (e: Exception) {
            DetectionResult("Memory Maps", false, "Error", Severity.LOW)
        }
    }
    
    private fun scanSmaps(): DetectionResult {
        return try {
            val findings = nativeScanSmaps()
            DetectionResult(
                technique = "Anonymous Memory",
                detected = findings.isNotEmpty(),
                details = if (findings.isEmpty()) "Clean" else "${findings.size} suspicious",
                severity = if (findings.isNotEmpty()) Severity.HIGH else Severity.LOW,
                rawData = findings.toList()
            )
        } catch (e: Exception) {
            DetectionResult("Anonymous Memory", false, "Error", Severity.LOW)
        }
    }
    
    private fun scanFds(): DetectionResult {
        return try {
            val findings = nativeScanFds()
            DetectionResult(
                technique = "File Descriptors",
                detected = findings.isNotEmpty(),
                details = if (findings.isEmpty()) "Clean" else "${findings.size} suspicious",
                severity = if (findings.isNotEmpty()) Severity.HIGH else Severity.LOW,
                rawData = findings.toList()
            )
        } catch (e: Exception) {
            DetectionResult("File Descriptors", false, "Error", Severity.LOW)
        }
    }
    
    private fun checkFridaPort(): DetectionResult {
        val portsToCheck = listOf(27042, 27043)
        val openPorts = mutableListOf<Int>()
        
        for (port in portsToCheck) {
            try {
                if (nativeCheckPort(port)) openPorts.add(port)
            } catch (e: Exception) {
                try {
                    Socket("127.0.0.1", port).use { openPorts.add(port) }
                } catch (e: Exception) { }
            }
        }
        
        return DetectionResult(
            technique = "Frida Ports",
            detected = openPorts.isNotEmpty(),
            details = if (openPorts.isEmpty()) "Closed" else "Open: ${openPorts.joinToString()}",
            severity = if (openPorts.isNotEmpty()) Severity.HIGH else Severity.LOW,
            rawData = openPorts.map { "Port $it" }
        )
    }
    
    private fun checkPtrace(): DetectionResult {
        return try {
            val traced = nativeCheckPtrace()
            DetectionResult(
                technique = "Ptrace Detection",
                detected = traced,
                details = if (traced) "Process is being traced" else "Not traced",
                severity = if (traced) Severity.CRITICAL else Severity.LOW
            )
        } catch (e: Exception) {
            DetectionResult("Ptrace Detection", false, "Error", Severity.LOW)
        }
    }
    
    private fun checkThreads(): DetectionResult {
        return try {
            val findings = nativeCheckThreads()
            DetectionResult(
                technique = "Suspicious Threads",
                detected = findings.isNotEmpty(),
                details = if (findings.isEmpty()) "Clean" else "${findings.size} found",
                severity = if (findings.isNotEmpty()) Severity.CRITICAL else Severity.LOW,
                rawData = findings.toList()
            )
        } catch (e: Exception) {
            DetectionResult("Suspicious Threads", false, "Error", Severity.LOW)
        }
    }
    
    private fun checkDbus(): DetectionResult {
        return try {
            val detected = nativeCheckDbus(27042)
            DetectionResult(
                technique = "D-Bus Auth",
                detected = detected,
                details = if (detected) "Frida D-Bus detected" else "Clean",
                severity = if (detected) Severity.CRITICAL else Severity.LOW
            )
        } catch (e: Exception) {
            DetectionResult("D-Bus Auth", false, "Error", Severity.LOW)
        }
    }
    
    private fun checkEnvironment(): DetectionResult {
        return try {
            val findings = nativeCheckEnvironment()
            DetectionResult(
                technique = "Environment",
                detected = findings.isNotEmpty(),
                details = if (findings.isEmpty()) "Clean" else findings.joinToString(),
                severity = if (findings.isNotEmpty()) Severity.MEDIUM else Severity.LOW,
                rawData = findings.toList()
            )
        } catch (e: Exception) {
            DetectionResult("Environment", false, "Error", Severity.LOW)
        }
    }
    
    private fun checkFridaProcesses(): DetectionResult {
        val suspicious = listOf("frida-server", "frida-helper", "gum-js-loop")
        val found = mutableListOf<String>()
        
        try {
            val process = Runtime.getRuntime().exec("ps -A")
            BufferedReader(InputStreamReader(process.inputStream)).useLines { lines ->
                lines.forEach { line ->
                    val lower = line.lowercase()
                    suspicious.forEach { proc ->
                        if (lower.contains(proc)) found.add(line.trim())
                    }
                }
            }
        } catch (e: Exception) { }
        
        return DetectionResult(
            technique = "Process Names",
            detected = found.isNotEmpty(),
            details = if (found.isEmpty()) "Clean" else "${found.size} found",
            severity = if (found.isNotEmpty()) Severity.CRITICAL else Severity.LOW,
            rawData = found
        )
    }
}
