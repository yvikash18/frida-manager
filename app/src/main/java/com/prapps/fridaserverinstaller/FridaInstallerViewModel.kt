package com.prapps.fridaserverinstaller

import android.content.Context
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class InstallStatus {
    IDLE, INSTALLING, SUCCESS, ERROR, SERVER_STARTING, SERVER_RUNNING, SERVER_STOPPED
}

data class InstallUiState(
    val status: InstallStatus = InstallStatus.IDLE,
    val messages: List<String> = emptyList(),
    val currentMessage: String = "",
    val isServerInstalled: Boolean = false,
    val isServerRunning: Boolean = false,
    val downloadProgress: Int = 0,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val serverInfo: String? = null,
    val currentServerType: String = "Unknown",
    val showRedownloadDialog: Boolean = false,
    val showInstallTypeDialog: Boolean = false,
    val showVersionSelectionDialog: Boolean = false,
    val availableReleases: List<FridaInstaller.FridaRelease> = emptyList(),
    val isLoadingReleases: Boolean = false,
    val serverPid: String? = null,
    val serverPort: Int = 27042,
    val isAutoStartEnabled: Boolean = false,
    val isDarkTheme: Boolean = true,
    val savedVersions: List<String> = emptyList(),
    val isWifiAdbEnabled: Boolean = false,
    val wifiAdbAddress: String? = null,

    val isRooted: Boolean = false,
    val raspResults: com.prapps.fridaserverinstaller.rasp.DetectionSummary? = null
)

class FridaInstallerViewModel(private val context: Context) : ViewModel() {
    private val _uiState = MutableStateFlow(InstallUiState())
    val uiState: StateFlow<InstallUiState> = _uiState.asStateFlow()
    
    private val fridaInstaller = FridaInstaller(context)
    private val preferencesManager = PreferencesManager(context)
    
    init {
        checkRootStatus()
        checkExistingInstallation()
        checkWifiAdbStatus()
        checkWifiAdbStatus()
        loadPreferences()
        runRaspScan()
    }
    
    fun runRaspScan() {
        // Run in background properly, but for now just direct call if simple
        // Ideally inside viewModelScope.launch(Dispatchers.IO)
        // Assuming Detector is lightweight enough or already threaded?
        // Actually native scans can block. Let's assume we need a thread/coroutine.
        // Since I don't see coroutines setup fully here (it's StateFlow), I'll check imports.
        // It has `kotlinx.coroutines.flow`. 
        // I'll add a simple thread for now or look if `viewModelScope` is available.
        // `androidx.lifecycle.ViewModel` usually has it.
        // But imports are minimal. I'll just use a Thread for safety to not block UI.
        Thread {
            val raspDetector = com.prapps.fridaserverinstaller.rasp.RaspDetector()
            val results = raspDetector.runFullScan()
            _uiState.value = _uiState.value.copy(raspResults = results)
        }.start()
    }
    
    private fun loadPreferences() {
        _uiState.value = _uiState.value.copy(
            serverPort = preferencesManager.serverPort,
            isAutoStartEnabled = preferencesManager.isAutoStartEnabled,
            isDarkTheme = preferencesManager.isDarkTheme,
            savedVersions = preferencesManager.getSavedVersions().toList()
        )
    }
    
    private fun checkExistingInstallation() {
        if (fridaInstaller.isServerAlreadyInstalled()) {
            val serverInfo = fridaInstaller.installedServerInfo
            val currentServerType = fridaInstaller.currentServerType
            _uiState.value = _uiState.value.copy(
                isServerInstalled = true,
                serverInfo = serverInfo,
                currentServerType = currentServerType,
                status = InstallStatus.SUCCESS
            )
        } else {
            _uiState.value = _uiState.value.copy(
                currentServerType = "Not installed"
            )
        }
    }
    
    fun startInstallation() {
        if (fridaInstaller.isServerAlreadyInstalled()) {
            _uiState.value = _uiState.value.copy(showRedownloadDialog = true)
            return
        }
        
        _uiState.value = _uiState.value.copy(showInstallTypeDialog = true)
    }
    
    fun setServerPort(port: Int) {
        preferencesManager.serverPort = port
        _uiState.value = _uiState.value.copy(serverPort = port)
    }
    
    fun setAutoStartEnabled(enabled: Boolean) {
        preferencesManager.isAutoStartEnabled = enabled
        _uiState.value = _uiState.value.copy(isAutoStartEnabled = enabled)
    }
    
    fun setDarkTheme(enabled: Boolean) {
        preferencesManager.isDarkTheme = enabled
        _uiState.value = _uiState.value.copy(isDarkTheme = enabled)
    }
    
    fun switchToSavedVersion(versionTag: String) {
        // Find the release from available releases or create a minimal one
        _uiState.value = _uiState.value.copy(
            status = InstallStatus.INSTALLING,
            messages = listOf("🔄 Switching to version $versionTag..."),
            downloadProgress = 0,
            currentMessage = "Switching to $versionTag..."
        )
        
        // First load releases to get the full release info
        fridaInstaller.getAllReleases(object : FridaInstaller.ReleasesCallback {
            override fun onReleasesLoaded(releases: List<FridaInstaller.FridaRelease>) {
                val release = releases.find { it.tagName == versionTag }
                if (release != null) {
                    performInstallationFromRelease(release, true)
                } else {
                    val currentMessages = _uiState.value.messages.toMutableList()
                    currentMessages.add("❌ Version $versionTag not found in releases")
                    _uiState.value = _uiState.value.copy(
                        status = InstallStatus.ERROR,
                        messages = currentMessages,
                        currentMessage = "Version not found"
                    )
                }
            }
            
            override fun onError(error: String) {
                val currentMessages = _uiState.value.messages.toMutableList()
                currentMessages.add("❌ Failed to fetch releases: $error")
                _uiState.value = _uiState.value.copy(
                    status = InstallStatus.ERROR,
                    messages = currentMessages,
                    currentMessage = "Failed to switch version"
                )
            }
        })
    }
    
    fun deleteSavedVersion(version: String) {
        preferencesManager.removeSavedVersion(version)
        _uiState.value = _uiState.value.copy(
            savedVersions = preferencesManager.getSavedVersions().toList()
        )
    }
    
    fun startServer() {
        val port = _uiState.value.serverPort
        _uiState.value = _uiState.value.copy(status = InstallStatus.SERVER_STARTING)
        
        fridaInstaller.startFridaServer(object : FridaInstaller.InstallCallback {
            override fun onProgress(message: String) {
                val currentMessages = _uiState.value.messages.toMutableList()
                currentMessages.add(message)
                _uiState.value = _uiState.value.copy(messages = currentMessages)
            }
            
            override fun onError(error: String) {
                val currentMessages = _uiState.value.messages.toMutableList()
                currentMessages.add("ERROR: $error")
                _uiState.value = _uiState.value.copy(
                    status = InstallStatus.ERROR,
                    messages = currentMessages,
                    serverPid = null
                )
            }
            
            override fun onSuccess(message: String) {
                val currentMessages = _uiState.value.messages.toMutableList()
                currentMessages.add(message)
                val pid = fridaInstaller.runningServerPid
                _uiState.value = _uiState.value.copy(
                    status = InstallStatus.SERVER_RUNNING,
                    messages = currentMessages,
                    isServerRunning = true,
                    serverPid = pid
                )
            }
            
            override fun onDownloadProgress(progress: Int, bytesDownloaded: Long, totalBytes: Long) {
                // Not used for server start
            }
        }, port)
    }
    
    fun stopServer() {
        fridaInstaller.stopFridaServer()
        val currentMessages = _uiState.value.messages.toMutableList()
        currentMessages.add("🛑 Frida server stopped")
        _uiState.value = _uiState.value.copy(
            status = InstallStatus.SERVER_STOPPED,
            messages = currentMessages,
            isServerRunning = false,
            currentMessage = "🛑 Frida server stopped",
            serverPid = null
        )
    }
    
    fun forceRedownload() {
        _uiState.value = _uiState.value.copy(
            showRedownloadDialog = false,
            showVersionSelectionDialog = true,
            isLoadingReleases = true
        )
        loadAvailableReleases()
    }
    
    fun installFromManualFile(filePath: String) {
        _uiState.value = _uiState.value.copy(
            status = InstallStatus.INSTALLING,
            messages = emptyList()
        )
        
        fridaInstaller.installFromManualFile(filePath, createInstallCallback())
    }
    
    fun dismissRedownloadDialog() {
        _uiState.value = _uiState.value.copy(showRedownloadDialog = false)
    }
    
    fun dismissInstallTypeDialog() {
        _uiState.value = _uiState.value.copy(showInstallTypeDialog = false)
    }
    
    fun downloadAndInstall() {
        _uiState.value = _uiState.value.copy(
            showInstallTypeDialog = false,
            showVersionSelectionDialog = true,
            isLoadingReleases = true
        )
        loadAvailableReleases()
    }
    
    fun loadAvailableReleases() {
        fridaInstaller.getAllReleases(object : FridaInstaller.ReleasesCallback {
            override fun onReleasesLoaded(releases: List<FridaInstaller.FridaRelease>) {
                _uiState.value = _uiState.value.copy(
                    availableReleases = releases,
                    isLoadingReleases = false
                )
            }
            
            override fun onError(error: String) {
                _uiState.value = _uiState.value.copy(
                    isLoadingReleases = false,
                    showVersionSelectionDialog = false
                )
                val currentMessages = _uiState.value.messages.toMutableList()
                currentMessages.add("ERROR: $error")
                _uiState.value = _uiState.value.copy(
                    status = InstallStatus.ERROR,
                    messages = currentMessages
                )
            }
        })
    }
    
    fun dismissVersionSelectionDialog() {
        _uiState.value = _uiState.value.copy(showVersionSelectionDialog = false)
    }
    
    fun showVersionSelector() {
        _uiState.value = _uiState.value.copy(
            showVersionSelectionDialog = true,
            isLoadingReleases = true
        )
        loadAvailableReleases()
    }
    
    fun installAndSaveVersion(release: FridaInstaller.FridaRelease) {
        // Save this version to preferences
        preferencesManager.addSavedVersion(release.tagName)
        _uiState.value = _uiState.value.copy(
            savedVersions = preferencesManager.getSavedVersions().toList()
        )
        
        // Install it
        _uiState.value = _uiState.value.copy(
            status = InstallStatus.INSTALLING,
            messages = emptyList(),
            downloadProgress = 0,
            downloadedBytes = 0,
            totalBytes = 0,
            showVersionSelectionDialog = false
        )
        performInstallationFromRelease(release, true)
    }
    
    fun installFromSelectedVersion(release: FridaInstaller.FridaRelease) {
        _uiState.value = _uiState.value.copy(
            status = InstallStatus.INSTALLING,
            messages = emptyList(),
            downloadProgress = 0,
            downloadedBytes = 0,
            totalBytes = 0,
            showVersionSelectionDialog = false
            // Keep existing currentServerType and other state
        )
        performInstallationFromRelease(release, false)
    }
    
    fun forceRedownloadFromVersion(release: FridaInstaller.FridaRelease) {
        _uiState.value = _uiState.value.copy(
            status = InstallStatus.INSTALLING,
            messages = emptyList(),
            downloadProgress = 0,
            downloadedBytes = 0,
            totalBytes = 0,
            showRedownloadDialog = false,
            showVersionSelectionDialog = false
            // Keep existing currentServerType and other state
        )
        performInstallationFromRelease(release, true)
    }
    
    private fun performInstallation(forceRedownload: Boolean) {
        fridaInstaller.installFridaServer(createInstallCallback(), forceRedownload)
    }
    
    private fun performInstallationFromRelease(release: FridaInstaller.FridaRelease, forceRedownload: Boolean) {
        fridaInstaller.installFridaServerFromRelease(release, createInstallCallback(), forceRedownload)
    }
    
    private fun createInstallCallback() = object : FridaInstaller.InstallCallback {
        override fun onProgress(message: String) {
            val currentMessages = _uiState.value.messages.toMutableList()
            currentMessages.add(message)
            _uiState.value = _uiState.value.copy(
                messages = currentMessages,
                currentMessage = message
            )
        }
        
        override fun onError(error: String) {
            val currentMessages = _uiState.value.messages.toMutableList()
            currentMessages.add("ERROR: $error")
            _uiState.value = _uiState.value.copy(
                status = InstallStatus.ERROR,
                messages = currentMessages,
                currentMessage = error
            )
        }
        
        override fun onSuccess(message: String) {
            val currentMessages = _uiState.value.messages.toMutableList()
            currentMessages.add(message)
            val serverInfo = fridaInstaller.installedServerInfo
            val currentServerType = fridaInstaller.currentServerType
            _uiState.value = _uiState.value.copy(
                status = InstallStatus.SUCCESS,
                messages = currentMessages,
                currentMessage = message,
                isServerInstalled = true,
                serverInfo = serverInfo,
                currentServerType = currentServerType
            )
        }
        
        override fun onDownloadProgress(progress: Int, bytesDownloaded: Long, totalBytes: Long) {
            _uiState.value = _uiState.value.copy(
                downloadProgress = progress,
                downloadedBytes = bytesDownloaded,
                totalBytes = totalBytes
            )
        }
    }
    
    fun resetInstallation() {
        // Reset to initial state but preserve server information
        val newState = InstallUiState()
        _uiState.value = newState
        
        // Update server info but keep status as IDLE to show install button
        if (fridaInstaller.isServerAlreadyInstalled()) {
            val serverInfo = fridaInstaller.installedServerInfo
            val currentServerType = fridaInstaller.currentServerType
            _uiState.value = _uiState.value.copy(
                isServerInstalled = true,
                serverInfo = serverInfo,
                currentServerType = currentServerType
                // Keep status = InstallStatus.IDLE to show install button
            )
        } else {
            _uiState.value = _uiState.value.copy(
                currentServerType = "Not installed"
            )
        }
    }
    
    fun refreshInstallationStatus() {
        checkExistingInstallation()
    }
    
    fun clearLogs() {
        _uiState.value = _uiState.value.copy(messages = emptyList())
    }
    fun checkWifiAdbStatus() {
        try {
            val process = Runtime.getRuntime().exec("getprop service.adb.tcp.port")
            val output = process.inputStream.bufferedReader().readText().trim()
            val port = output.toIntOrNull() ?: -1
            
            val isEnabled = port > 0
            val ipAddress = if (isEnabled) getIpAddress() else null
            val address = if (ipAddress != null) "$ipAddress:$port" else null
            
            _uiState.value = _uiState.value.copy(
                isWifiAdbEnabled = isEnabled,
                wifiAdbAddress = address
            )
        } catch (e: Exception) {
            e.printStackTrace()
            _uiState.value = _uiState.value.copy(
                isWifiAdbEnabled = false,
                wifiAdbAddress = null
            )
        }
    }
    
    fun toggleWifiAdb(enable: Boolean) {
        val command = if (enable) {
            "setprop service.adb.tcp.port 5555; stop adbd; start adbd"
        } else {
            "setprop service.adb.tcp.port -1; stop adbd; start adbd"
        }
        
        fridaInstaller.executeSuCommand(command)
        
        // Wait a bit for the service to restart
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            checkWifiAdbStatus()
        }, 1000)
    }
    
    private fun checkRootStatus() {
        val rooted = fridaInstaller.isRooted()
        _uiState.value = _uiState.value.copy(isRooted = rooted)
    }

    private fun getIpAddress(): String? {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is java.net.Inet4Address) {
                        return address.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}