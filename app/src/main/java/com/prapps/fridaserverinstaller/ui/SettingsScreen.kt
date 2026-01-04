package com.prapps.fridaserverinstaller.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prapps.fridaserverinstaller.ui.theme.*

@Composable
fun SettingsScreen(
    serverPort: Int,
    isAutoStartEnabled: Boolean,
    isDarkTheme: Boolean,
    savedVersions: List<String>,
    currentVersion: String?,
    onPortChange: (Int) -> Unit,
    onAutoStartChange: (Boolean) -> Unit,
    onDarkThemeChange: (Boolean) -> Unit,
    onSwitchVersion: (String) -> Unit,
    onDeleteVersion: (String) -> Unit
) {
    var portText by remember(serverPort) { mutableStateOf(serverPort.toString()) }
    var versionToDelete by remember { mutableStateOf<String?>(null) }
    
    // Delete Confirmation Dialog
    versionToDelete?.let { version ->
        AlertDialog(
            onDismissRequest = { versionToDelete = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Delete Version?", color = MaterialTheme.colorScheme.onSurface) },
            text = { 
                Text(
                    "Are you sure you want to delete $version from saved versions?",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ) 
            },
            confirmButton = {
                Button(
                    onClick = { 
                        onDeleteVersion(version)
                        versionToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { versionToDelete = null }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Settings",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // Appearance Section
        SettingsSection(title = "Appearance") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Dark Theme",
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isDarkTheme) "Enabled" else "Disabled",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isDarkTheme,
                    onCheckedChange = onDarkThemeChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = TextWhite,
                        checkedTrackColor = ElectricViolet,
                        uncheckedThumbColor = MutedSlate,
                        uncheckedTrackColor = ShieldGrey
                    )
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Server Configuration Section
        SettingsSection(title = "Server Configuration") {
            // Port Setting
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Server Port",
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Default: 27042 (Frida standard)",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedTextField(
                    value = portText,
                    onValueChange = { newValue ->
                        portText = newValue
                        newValue.toIntOrNull()?.let { port ->
                            if (port in 1024..65535) {
                                onPortChange(port)
                            }
                        }
                    },
                    modifier = Modifier.width(120.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricViolet,
                        unfocusedBorderColor = MutedSlate,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Auto-start Setting
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Start on Boot",
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Automatically start Frida server when device boots",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isAutoStartEnabled,
                    onCheckedChange = onAutoStartChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = TextWhite,
                        checkedTrackColor = ElectricViolet,
                        uncheckedThumbColor = MutedSlate,
                        uncheckedTrackColor = ShieldGrey
                    )
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Version Management Section
        SettingsSection(title = "Saved Versions") {
            if (currentVersion != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CyberCyan.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = CyberCyan
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Currently Installed",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = currentVersion,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            if (savedVersions.isEmpty()) {
                Text(
                    text = "No saved versions. Download a version to add it here.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                Text(
                    text = "Tap Switch to install a different version",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                savedVersions.forEach { version ->
                    val isActive = currentVersion?.contains(version) == true
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isActive) 
                                SuccessGreen.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = version, 
                                    fontSize = 14.sp, 
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (isActive) {
                                    Text(
                                        text = "Currently active",
                                        fontSize = 11.sp,
                                        color = SuccessGreen
                                    )
                                }
                            }
                            
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (!isActive) {
                                    Button(
                                        onClick = { onSwitchVersion(version) },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = ElectricViolet,
                                            contentColor = TextWhite
                                        )
                                    ) {
                                        Text("Switch", fontSize = 12.sp)
                                    }
                                }
                                
                                IconButton(
                                    onClick = { versionToDelete = version }
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = ErrorRed,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // About Section
        SettingsSection(title = "About") {
            Text(
                text = "Frida Manager",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Version 1.0",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Manage Frida server installation and execution on rooted Android devices.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = ElectricViolet,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                content = content
            )
        }
    }
}
