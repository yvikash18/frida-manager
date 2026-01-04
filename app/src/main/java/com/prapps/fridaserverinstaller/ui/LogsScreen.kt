package com.prapps.fridaserverinstaller.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prapps.fridaserverinstaller.ui.theme.*

enum class LogLevel {
    INFO, SUCCESS, WARNING, ERROR, STDOUT, STDERR
}

data class LogEntry(
    val message: String,
    val level: LogLevel,
    val timestamp: Long = System.currentTimeMillis()
)

@Composable
fun LogsScreen(
    messages: List<String>,
    onClear: () -> Unit
) {
    val logEntries = remember(messages) {
        messages.map { msg ->
            val level = when {
                msg.startsWith("✅") || msg.contains("SUCCESS") -> LogLevel.SUCCESS
                msg.startsWith("❌") || msg.contains("ERROR") -> LogLevel.ERROR
                msg.startsWith("⚠️") || msg.contains("WARNING") -> LogLevel.WARNING
                msg.contains("[STDOUT]") -> LogLevel.STDOUT
                msg.contains("[STDERR]") -> LogLevel.STDERR
                else -> LogLevel.INFO
            }
            LogEntry(msg, level)
        }
    }
    
    var filterLevel by remember { mutableStateOf<LogLevel?>(null) }
    var autoScroll by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()
    
    val filteredEntries = if (filterLevel != null) {
        logEntries.filter { it.level == filterLevel }
    } else {
        logEntries
    }
    
    LaunchedEffect(filteredEntries.size, autoScroll) {
        if (autoScroll && filteredEntries.isNotEmpty()) {
            listState.animateScrollToItem(filteredEntries.size - 1)
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Server Logs",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Row {
                IconButton(onClick = { autoScroll = !autoScroll }) {
                    Icon(
                        imageVector = if (autoScroll) Icons.Default.VerticalAlignBottom else Icons.Default.VerticalAlignTop,
                        contentDescription = "Auto-scroll",
                        tint = if (autoScroll) ElectricViolet else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onClear) {
                    Icon(
                        Icons.Default.Delete, 
                        contentDescription = "Clear logs",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        // Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = filterLevel == null,
                onClick = { filterLevel = null },
                label = { Text("All") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ElectricViolet,
                    selectedLabelColor = TextWhite
                )
            )
            FilterChip(
                selected = filterLevel == LogLevel.ERROR,
                onClick = { filterLevel = if (filterLevel == LogLevel.ERROR) null else LogLevel.ERROR },
                label = { Text("Errors") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ErrorRed,
                    selectedLabelColor = TextWhite
                )
            )
            FilterChip(
                selected = filterLevel == LogLevel.STDOUT,
                onClick = { filterLevel = if (filterLevel == LogLevel.STDOUT) null else LogLevel.STDOUT },
                label = { Text("Stdout") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CyberCyan,
                    selectedLabelColor = TextWhite
                )
            )
        }
        
        // Log Count
        Text(
            text = "${filteredEntries.size} log entries",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        // Log List - Use surface color for light/dark compatibility
        Card(
            modifier = Modifier.fillMaxSize(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (filteredEntries.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Terminal,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No logs yet",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Start the server to see logs",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                SelectionContainer {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                    ) {
                        items(filteredEntries) { entry ->
                            LogEntryRow(entry)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LogEntryRow(entry: LogEntry) {
    val textColor by animateColorAsState(
        targetValue = when (entry.level) {
            LogLevel.SUCCESS -> SuccessGreen
            LogLevel.ERROR -> ErrorRed
            LogLevel.WARNING -> WarningAmber
            LogLevel.STDOUT -> CyberCyan
            LogLevel.STDERR -> ErrorRed.copy(alpha = 0.8f)
            LogLevel.INFO -> MaterialTheme.colorScheme.onSurface
        },
        label = "textColor"
    )
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        // Level indicator
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(16.dp)
                .background(
                    color = textColor,
                    shape = RoundedCornerShape(2.dp)
                )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = entry.message,
            color = textColor,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            lineHeight = 16.sp
        )
    }
}
