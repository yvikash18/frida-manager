package com.prapps.fridaserverinstaller.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prapps.fridaserverinstaller.rasp.DetectionResult
import com.prapps.fridaserverinstaller.rasp.DetectionSummary
import com.prapps.fridaserverinstaller.rasp.RaspDetector
import com.prapps.fridaserverinstaller.rasp.Severity
import com.prapps.fridaserverinstaller.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DetectionScreen() {
    var isScanning by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf<DetectionSummary?>(null) }
    val scope = rememberCoroutineScope()
    val detector = remember { RaspDetector() }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "RASP Detection",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        
        Text(
            text = "Detect anti-Frida techniques",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // Scan Button
        Button(
            onClick = {
                isScanning = true
                scope.launch {
                    summary = withContext(Dispatchers.IO) {
                        detector.runFullScan()
                    }
                    isScanning = false
                }
            },
            enabled = !isScanning,
            colors = ButtonDefaults.buttonColors(
                containerColor = ElectricViolet,
                contentColor = TextWhite
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isScanning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = TextWhite,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Scanning...")
            } else {
                Icon(Icons.Default.Security, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Run Detection Scan")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Results
        summary?.let { result ->
            // Summary Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        result.detections == 0 -> SuccessGreen
                        result.maxSeverity == Severity.CRITICAL -> ErrorRed
                        else -> WarningAmber
                    }
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when {
                            result.detections == 0 -> Icons.Default.CheckCircle
                            result.maxSeverity == Severity.CRITICAL -> Icons.Default.Error
                            else -> Icons.Default.Warning
                        },
                        contentDescription = null,
                        tint = TextWhite,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = result.threatLevel,
                            color = TextWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "${result.detections} issues found",
                            color = TextWhite.copy(alpha = 0.9f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Detection Results
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(result.results) { detection ->
                    SimpleDetectionCard(detection)
                }
            }
        }
    }
}

@Composable
fun SimpleDetectionCard(result: DetectionResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status Icon
            Icon(
                imageVector = if (result.detected) Icons.Default.Warning else Icons.Default.CheckCircle,
                contentDescription = null,
                tint = if (result.detected) ErrorRed else SuccessGreen,
                modifier = Modifier.size(20.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Technique name
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.technique,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (result.detected && result.rawData.isNotEmpty()) {
                    Text(
                        text = "${result.rawData.size} items found",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Status badge
            Surface(
                color = if (result.detected) ErrorRed else SuccessGreen,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = if (result.detected) result.severity.name else "OK",
                    color = TextWhite,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
