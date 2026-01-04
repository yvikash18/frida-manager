package com.prapps.fridaserverinstaller.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.animateContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import com.prapps.fridaserverinstaller.rasp.DetectionResult
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prapps.fridaserverinstaller.FridaInstaller
import com.prapps.fridaserverinstaller.FridaInstallerViewModel
import com.prapps.fridaserverinstaller.InstallStatus
import com.prapps.fridaserverinstaller.ui.theme.SuccessGreen
import com.prapps.fridaserverinstaller.ui.theme.TextWhite
import com.prapps.fridaserverinstaller.ui.theme.*

@Composable
fun HomeScreen(
    viewModel: FridaInstallerViewModel,
    onNavigateToLogs: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val tempFile = java.io.File(context.cacheDir, "temp_frida_server")
            context.contentResolver.openInputStream(it)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            viewModel.installFromManualFile(tempFile.absolutePath)
        }
    }
    
    // Dialogs
    if (uiState.showRedownloadDialog) {
        RedownloadDialog(
            onConfirm = { viewModel.forceRedownload() },
            onDismiss = { viewModel.dismissRedownloadDialog() },
            onSelectFile = { 
                viewModel.dismissRedownloadDialog()
                filePickerLauncher.launch("*/*")
            },
            serverInfo = uiState.serverInfo
        )
    }
    
    if (uiState.showInstallTypeDialog) {
        InstallTypeDialog(
            onDownload = { viewModel.downloadAndInstall() },
            onSelectFile = { 
                viewModel.dismissInstallTypeDialog()
                filePickerLauncher.launch("*/*")
            },
            onDismiss = { viewModel.dismissInstallTypeDialog() }
        )
    }
    
    if (uiState.showVersionSelectionDialog) {
        VersionSelectionDialog(
            isLoading = uiState.isLoadingReleases,
            releases = uiState.availableReleases,
            savedVersions = uiState.savedVersions,
            onVersionSelected = { release ->
                viewModel.installAndSaveVersion(release)
            },
            onDismiss = { viewModel.dismissVersionSelectionDialog() }
        )
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Frida Manager",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        
        Text(
            text = "Manage Frida server on your rooted Android device",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        
        if (!uiState.isRooted) {
            RootWarningCard()
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Server Status Card
        ServerStatusCard(
            isInstalled = uiState.isServerInstalled,
            currentServerType = uiState.currentServerType,
            isRunning = uiState.isServerRunning,
            serverPid = uiState.serverPid,
            serverPort = uiState.serverPort
        )
        
        // WiFi ADB Card
        WifiAdbCard(
            isEnabled = uiState.isWifiAdbEnabled,
            address = uiState.wifiAdbAddress,
            onToggle = { enable -> viewModel.toggleWifiAdb(enable) }
        )
        

        
        // Main Action Area
        when (uiState.status) {
            InstallStatus.IDLE -> {
                Button(
                    onClick = { viewModel.startInstallation() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricViolet,
                        contentColor = TextWhite
                    )
                ) {
                    Text("Install Frida Server")
                }
                
                // Change Version Button
                OutlinedButton(
                    onClick = { viewModel.showVersionSelector() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onBackground
                    ),
                    border = BorderStroke(1.dp, CyberCyan)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Browse Versions")
                }
            }
            InstallStatus.INSTALLING -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (uiState.downloadProgress > 0) {
                        LinearProgressIndicator(
                            progress = { uiState.downloadProgress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = ElectricViolet,
                            trackColor = ShieldGrey
                        )
                        Text(
                            text = "Download: ${uiState.downloadProgress}%",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        CircularProgressIndicator(color = ElectricViolet)
                    }
                    Text(
                        text = uiState.currentMessage.ifEmpty { "Installing..." },
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )
                }
            }
            InstallStatus.SUCCESS -> {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Success",
                    tint = SuccessGreen,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = "Installation Complete!",
                    color = SuccessGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.startServer() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricViolet,
                            contentColor = TextWhite
                        )
                    ) {
                        Text("Start Server")
                    }
                    OutlinedButton(
                        onClick = { viewModel.showVersionSelector() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onBackground
                        ),
                        border = BorderStroke(1.dp, CyberCyan)
                    ) {
                        Text("Change Version")
                    }
                }
            }
            InstallStatus.SERVER_STARTING -> {
                CircularProgressIndicator(color = ElectricViolet)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Starting Server...",
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            InstallStatus.SERVER_RUNNING -> {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Running",
                    tint = SuccessGreen,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = "Server Running!",
                    color = SuccessGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.stopServer() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ErrorRed,
                            contentColor = TextWhite
                        )
                    ) {
                        Text("Stop Server")
                    }
                    OutlinedButton(
                        onClick = onNavigateToLogs,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onBackground
                        ),
                        border = BorderStroke(1.dp, CyberCyan)
                    ) {
                        Text("View Logs")
                    }
                }
                
                // Change Version (Stop first)
                OutlinedButton(
                    onClick = { viewModel.showVersionSelector() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onBackground
                    ),
                    border = BorderStroke(1.dp, ElectricViolet)
                ) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Change Version")
                }
            }
            InstallStatus.SERVER_STOPPED -> {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Stopped",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = "Server Stopped",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.startServer() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricViolet,
                            contentColor = TextWhite
                        )
                    ) {
                        Text("Start Server")
                    }
                    OutlinedButton(
                        onClick = { viewModel.showVersionSelector() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onBackground
                        ),
                        border = BorderStroke(1.dp, CyberCyan)
                    ) {
                        Text("Change Version")
                    }
                }
            }
            InstallStatus.ERROR -> {
                Icon(
                    imageVector = Icons.Filled.Error,
                    contentDescription = "Error",
                    tint = ErrorRed,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = "Error",
                    color = ErrorRed,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Button(
                    onClick = { viewModel.resetInstallation() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricViolet,
                        contentColor = TextWhite
                    )
                ) {
                    Text("Try Again")
                }
            }
        }
        
        // Current Message
        if (uiState.currentMessage.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Text(
                    text = uiState.currentMessage,
                    modifier = Modifier.padding(12.dp),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun RootWarningCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Root Access Required",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Frida Server Manager requires root access to function correctly. Please ensure your device is rooted and root access is granted.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
fun ServerStatusCard(
    isInstalled: Boolean,
    currentServerType: String,
    isRunning: Boolean,
    serverPid: String?,
    serverPort: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.dp,
            when {
                isRunning -> SuccessGreen
                isInstalled -> CyberCyan
                else -> WarningAmber
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isInstalled) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = when {
                        isRunning -> SuccessGreen
                        isInstalled -> CyberCyan
                        else -> WarningAmber
                    },
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = when {
                            isRunning -> "Server Running"
                            isInstalled -> "Server Installed"
                            else -> "Not Installed"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = currentServerType,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            if (isRunning) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (serverPid != null) {
                        Text(text = "PID: $serverPid", fontSize = 12.sp, color = CyberCyan)
                    }
                    Text(text = "Port: $serverPort", fontSize = 12.sp, color = CyberCyan)
                }
            }
        }
    }
}

@Composable
fun RedownloadDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onSelectFile: () -> Unit,
    serverInfo: String?
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = { Text("Server Already Installed") },
        text = {
            Column {
                Text("Current: ${serverInfo ?: "Unknown"}")
                Spacer(modifier = Modifier.height(8.dp))
                Text("What would you like to do?")
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet)
            ) {
                Text("Download New Version")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onSelectFile) {
                    Text("Select File", color = CyberCyan)
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    )
}

@Composable
fun InstallTypeDialog(
    onDownload: () -> Unit,
    onSelectFile: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = { Text("Install Method") },
        text = { Text("How would you like to install Frida server?") },
        confirmButton = {
            Button(
                onClick = onDownload,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet)
            ) {
                Text("Download from GitHub")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onSelectFile) {
                    Text("Select Local File", color = CyberCyan)
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    )
}

@Composable
fun VersionSelectionDialog(
    isLoading: Boolean,
    releases: List<FridaInstaller.FridaRelease>,
    savedVersions: List<String>,
    onVersionSelected: (FridaInstaller.FridaRelease) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        title = { Text("Select Version") },
        text = {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = ElectricViolet)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.height(400.dp)
                ) {
                    items(releases) { release ->
                        val isSaved = savedVersions.contains(release.tagName)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { onVersionSelected(release) },
                            colors = CardDefaults.cardColors(
                                containerColor = when {
                                    isSaved -> SuccessGreen.copy(alpha = 0.15f)
                                    release.prerelease -> WarningAmber.copy(alpha = 0.15f)
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
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
                                        text = release.getDisplayName(),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Published: ${release.publishedAt.take(10)}",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isSaved) {
                                    Surface(
                                        color = SuccessGreen,
                                        shape = MaterialTheme.shapes.small
                                    ) {
                                        Text(
                                            text = "SAVED",
                                            color = TextWhite,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

@Composable
fun WifiAdbCard(
    isEnabled: Boolean,
    address: String?,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.dp,
            if (isEnabled) SuccessGreen else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isEnabled) Icons.Default.Wifi else Icons.Default.WifiOff,
                        contentDescription = null,
                        tint = if (isEnabled) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Wireless ADB",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (isEnabled && address != null) {
                            Text(
                                text = address,
                                fontSize = 12.sp,
                                color = CyberCyan
                            )
                        } else {
                            Text(
                                text = "Connect wirelessly",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                Switch(
                    checked = isEnabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = TextWhite,
                        checkedTrackColor = SuccessGreen,
                        uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }
            
            if (isEnabled && address != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "adb connect $address",
                        modifier = Modifier.padding(8.dp),
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}


