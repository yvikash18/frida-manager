package com.prapps.fridaserverinstaller

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import org.tukaani.xz.XZInputStream
import java.io.*
import java.util.concurrent.TimeUnit

class FridaInstaller(private val context: Context) {
    
    companion object {
        private const val TAG = "FridaInstaller"
        private const val GITHUB_API_URL = "https://api.github.com/repos/frida/frida/releases/latest"
        private const val GITHUB_ALL_RELEASES_URL = "https://api.github.com/repos/frida/frida/releases"
        private const val PHANTOM_ALL_RELEASES_URL = "https://api.github.com/repos/FernanApps/phantom-frida/releases"
    }
    
    data class FridaRelease(
        val tagName: String,
        val name: String,
        val publishedAt: String,
        val prerelease: Boolean,
        val assets: JsonArray,
        val isPhantom: Boolean = false
    ) {
        fun getDisplayName(): String {
            val label = if (isPhantom) "Phantom $tagName" else tagName
            return label + if (prerelease) " (Pre-release)" else ""
        }
    }
    
    interface InstallCallback {
        fun onProgress(message: String)
        fun onError(error: String)
        fun onSuccess(message: String)
        fun onDownloadProgress(progress: Int, bytesDownloaded: Long, totalBytes: Long)
    }
    
    interface ReleasesCallback {
        fun onReleasesLoaded(releases: List<FridaRelease>)
        fun onError(error: String)
    }
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private var fridaServerProcess: Process? = null
    
    var currentServerType: String = "Unknown"
        private set
    
    val installedServerInfo: String?
        get() = getInstalledServerInfoInternal()
    
    val runningServerPid: String?
        get() = getRunningServerPidInternal()
    
    init {
        loadCurrentServerType()
    }
    
    private fun loadCurrentServerType() {
        val serverInfo = getInstalledServerInfoInternal()
        currentServerType = when {
            serverInfo == null -> "Unknown"
            serverInfo.startsWith("Manual Installation") -> serverInfo
            else -> "Downloaded: $serverInfo"
        }
    }
    
    fun isRooted(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            process.outputStream.apply {
                write("id\n".toByteArray())
                write("exit\n".toByteArray())
                flush()
            }
            val output = BufferedReader(InputStreamReader(process.inputStream)).readLine()
            val exitCode = process.waitFor()
            Log.d(TAG, "Root check output: $output, exit code: $exitCode")
            exitCode == 0 && output?.contains("uid=0") == true
        } catch (e: Exception) {
            Log.e(TAG, "Root check failed", e)
            false
        }
    }
    
    fun getDeviceArchitecture(): String {
        val abi = Build.CPU_ABI
        Log.d(TAG, "Device ABI: $abi")
        return when (abi) {
            "arm64-v8a" -> "arm64"
            "armeabi-v7a" -> "arm"
            "x86" -> "x86"
            "x86_64" -> "x86_64"
            else -> abi
        }
    }
    
    private fun getFridaDownloadDir(): File {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return File(downloadsDir, "FridaServerInstaller").apply { mkdirs() }
    }
    
    private fun getFridaInternalDir(): File {
        return File(context.filesDir, "frida").apply { mkdirs() }
    }
    
    fun isServerAlreadyInstalled(): Boolean {
        val serverFile = File(getFridaInternalDir(), "frida-server")
        return serverFile.exists() && serverFile.canExecute()
    }
    
    private fun getInstalledServerInfoInternal(): String? {
        val fridaDir = getFridaInternalDir()
        val serverFile = File(fridaDir, "frida-server")
        val infoFile = File(fridaDir, "server-info.txt")
        
        return if (serverFile.exists() && infoFile.exists()) {
            try {
                infoFile.bufferedReader().readLine()
            } catch (e: Exception) {
                "Unknown version"
            }
        } else null
    }
    
    private fun removeExistingInstallation() {
        try {
            stopFridaServer()
            val fridaDir = getFridaInternalDir()
            File(fridaDir, "frida-server").delete()
            File(fridaDir, "server-info.txt").delete()
            currentServerType = "Unknown"
            Log.d(TAG, "Existing Frida installation removed")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove existing installation", e)
        }
    }
    
    fun installFridaServer(callback: InstallCallback, forceRedownload: Boolean = false) {
        installFridaServerFromLatest(callback, forceRedownload)
    }
    
    fun installFridaServerFromRelease(release: FridaRelease, callback: InstallCallback, forceRedownload: Boolean) {
        Thread {
            try {
                callback.onProgress("🛑 Stopping any running Frida server...")
                stopFridaServer()
                
                callback.onProgress("🔐 Checking root permissions...")
                if (!isRooted()) {
                    callback.onProgress("❌ Root check failed - No root access")
                    callback.onError("Root access is required but not available")
                    return@Thread
                }
                callback.onProgress("✅ Root access confirmed - Device is rooted")
                
                callback.onProgress("📱 Detecting device architecture...")
                val arch = getDeviceArchitecture()
                callback.onProgress("✅ Device architecture detected: $arch")
                
                if (!forceRedownload && isServerAlreadyInstalled()) {
                    val serverInfo = installedServerInfo
                    callback.onProgress("📋 Found existing server: ${serverInfo ?: "Unknown version"}")
                    callback.onProgress("🗑️ Removing existing installation...")
                    removeExistingInstallation()
                    callback.onProgress("✅ Previous installation removed")
                }
                
                callback.onProgress("✅ Selected Frida version: ${release.tagName}")
                
                callback.onProgress("🔍 Finding matching server binary for $arch...")
                val downloadUrl = findServerAssetInRelease(release, arch)
                if (downloadUrl == null) {
                    callback.onProgress("❌ No matching binary found for $arch")
                    callback.onError("No matching server binary found for architecture: $arch")
                    return@Thread
                }
                callback.onProgress("✅ Found matching binary for download")
                
                callback.onProgress("📥 Starting download...")
                val downloadedFile = downloadAssetWithProgress(downloadUrl, callback)
                if (downloadedFile == null) {
                    callback.onProgress("❌ Download failed")
                    callback.onError("Failed to download Frida server")
                    return@Thread
                }
                callback.onProgress("✅ Download completed: ${downloadedFile.name}")
                
                callback.onProgress("📦 Extracting server binary...")
                val extractedFile = if (release.isPhantom)
                    extractGzFile(downloadedFile)
                else
                    extractXzFile(downloadedFile)
                if (extractedFile == null) {
                    callback.onProgress("❌ Extraction failed")
                    callback.onError("Failed to extract server binary")
                    return@Thread
                }
                callback.onProgress("✅ Extraction completed")

                callback.onProgress("🔧 Setting executable permissions...")
                if (!setExecutablePermissions(extractedFile)) {
                    callback.onProgress("❌ Permission setting failed")
                    callback.onError("Failed to set executable permissions")
                    return@Thread
                }
                callback.onProgress("✅ Executable permissions set successfully")

                val label = if (release.isPhantom) "Phantom Frida ${release.tagName}" else release.tagName
                saveServerInfo(label, arch)
                loadCurrentServerType()
                callback.onSuccess("$label installed successfully!")
                
            } catch (e: Exception) {
                Log.e(TAG, "Installation failed", e)
                callback.onError("Installation failed: ${e.message}")
            }
        }.start()
    }
    
    private fun installFridaServerFromLatest(callback: InstallCallback, forceRedownload: Boolean) {
        Thread {
            try {
                callback.onProgress("🛑 Stopping any running Frida server...")
                stopFridaServer()
                
                callback.onProgress("🔐 Checking root permissions...")
                if (!isRooted()) {
                    callback.onProgress("❌ Root check failed - No root access")
                    callback.onError("Root access is required but not available")
                    return@Thread
                }
                callback.onProgress("✅ Root access confirmed - Device is rooted")
                
                callback.onProgress("📱 Detecting device architecture...")
                val arch = getDeviceArchitecture()
                callback.onProgress("✅ Device architecture detected: $arch")
                
                if (!forceRedownload && isServerAlreadyInstalled()) {
                    val serverInfo = installedServerInfo
                    callback.onProgress("📋 Found existing server: ${serverInfo ?: "Unknown version"}")
                    callback.onSuccess("✅ Frida server already installed! ${serverInfo ?: ""}")
                    return@Thread
                }
                
                callback.onProgress("🌐 Fetching latest Frida release from GitHub...")
                val release = getLatestRelease()
                if (release == null) {
                    callback.onProgress("❌ Failed to fetch release information")
                    callback.onError("Failed to fetch latest release information")
                    return@Thread
                }
                
                val version = release.get("tag_name").asString
                callback.onProgress("✅ Latest Frida version found: $version")
                
                callback.onProgress("🔍 Finding matching server binary for $arch...")
                val downloadUrl = findServerAsset(release, arch)
                if (downloadUrl == null) {
                    callback.onProgress("❌ No matching binary found for $arch")
                    callback.onError("No matching server binary found for architecture: $arch")
                    return@Thread
                }
                callback.onProgress("✅ Found matching binary for download")
                
                callback.onProgress("📥 Starting download...")
                val downloadedFile = downloadAssetWithProgress(downloadUrl, callback)
                if (downloadedFile == null) {
                    callback.onProgress("❌ Download failed")
                    callback.onError("Failed to download Frida server")
                    return@Thread
                }
                callback.onProgress("✅ Download completed: ${downloadedFile.name}")
                
                callback.onProgress("📦 Extracting server binary...")
                val extractedFile = extractXzFile(downloadedFile)
                if (extractedFile == null) {
                    callback.onProgress("❌ Extraction failed")
                    callback.onError("Failed to extract server binary")
                    return@Thread
                }
                callback.onProgress("✅ Extraction completed")
                
                callback.onProgress("🔧 Setting executable permissions...")
                if (!setExecutablePermissions(extractedFile)) {
                    callback.onProgress("❌ Permission setting failed")
                    callback.onError("Failed to set executable permissions")
                    return@Thread
                }
                callback.onProgress("✅ Executable permissions set successfully")
                
                saveServerInfo(version, arch)
                loadCurrentServerType()
                callback.onSuccess("Frida server $version installed successfully!")
                
            } catch (e: Exception) {
                Log.e(TAG, "Installation failed", e)
                callback.onError("Installation failed: ${e.message}")
            }
        }.start()
    }
    
    fun installFromManualFile(filePath: String, callback: InstallCallback) {
        Thread {
            try {
                callback.onProgress("🛑 Stopping any running Frida server...")
                stopFridaServer()
                
                callback.onProgress("🔐 Checking root permissions...")
                if (!isRooted()) {
                    callback.onProgress("❌ Root check failed - No root access")
                    callback.onError("Root access is required but not available")
                    return@Thread
                }
                callback.onProgress("✅ Root access confirmed - Device is rooted")
                
                val sourceFile = File(filePath)
                if (!sourceFile.exists()) {
                    callback.onProgress("❌ Selected file does not exist: $filePath")
                    callback.onError("Selected file does not exist")
                    return@Thread
                }
                
                callback.onProgress("📁 Processing: ${sourceFile.name} (${formatFileSize(sourceFile.length())})")
                
                val fridaDir = getFridaInternalDir()
                val nameLower = sourceFile.name.lowercase()
                val isXzFile = nameLower.endsWith(".xz")
                val isGzFile = nameLower.endsWith(".gz")

                val targetFile = when {
                    isXzFile -> {
                        callback.onProgress("📦 Processing compressed file (.xz)...")
                        val tempFile = File(fridaDir, "temp-server.xz")
                        sourceFile.copyTo(tempFile, overwrite = true)
                        callback.onProgress("📦 Extracting server binary...")
                        extractXzFile(tempFile).also { tempFile.delete() }
                    }
                    isGzFile -> {
                        callback.onProgress("📦 Processing compressed file (.gz)...")
                        val tempFile = File(fridaDir, "temp-server.gz")
                        sourceFile.copyTo(tempFile, overwrite = true)
                        callback.onProgress("📦 Extracting server binary...")
                        extractGzFile(tempFile).also { tempFile.delete() }
                    }
                    else -> {
                        callback.onProgress("📁 Processing raw binary file...")
                        val target = File(fridaDir, "frida-server")
                        target.delete()
                        sourceFile.copyTo(target, overwrite = true)
                        target
                    }
                }
                
                callback.onProgress("✅ File processing completed")
                
                if (targetFile == null) {
                    callback.onProgress("❌ File processing failed")
                    callback.onError("Failed to process server file")
                    return@Thread
                }
                
                callback.onProgress("🔧 Setting executable permissions...")
                if (!setExecutablePermissions(targetFile)) {
                    callback.onProgress("❌ Permission setting failed")
                    callback.onError("Failed to set executable permissions")
                    return@Thread
                }
                callback.onProgress("✅ Executable permissions set successfully")
                
                saveServerInfo("Manual Installation (${sourceFile.name})", "Unknown")
                loadCurrentServerType()
                callback.onSuccess("✅ Frida server installed successfully from manual file!")
                
            } catch (e: Exception) {
                Log.e(TAG, "Manual installation failed", e)
                callback.onError("Manual installation failed: ${e.message}")
            }
        }.start()
    }
    
    private fun getLatestRelease(): JsonObject? {
        val request = Request.Builder().url(GITHUB_API_URL).build()
        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Failed to fetch release: ${response.code}")
            gson.fromJson(response.body?.string(), JsonObject::class.java)
        }
    }
    
    fun getAllReleases(callback: ReleasesCallback) {
        Thread {
            try {
                val releases = fetchAllReleases()
                callback.onReleasesLoaded(releases)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch releases", e)
                callback.onError("Failed to fetch releases: ${e.message}")
            }
        }.start()
    }
    
    private fun fetchAllReleases(): List<FridaRelease> {
        val request = Request.Builder().url("$GITHUB_ALL_RELEASES_URL?per_page=50").build()
        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Failed to fetch releases: ${response.code}")
            
            val releasesArray = gson.fromJson(response.body?.string(), JsonArray::class.java)
            releasesArray.mapNotNull { element ->
                val releaseObj = element.asJsonObject
                val assets = releaseObj.getAsJsonArray("assets")
                if (hasAndroidAssets(assets)) {
                    FridaRelease(
                        tagName = releaseObj.get("tag_name").asString,
                        name = releaseObj.get("name")?.takeIf { !it.isJsonNull }?.asString 
                            ?: releaseObj.get("tag_name").asString,
                        publishedAt = releaseObj.get("published_at").asString,
                        prerelease = releaseObj.get("prerelease").asBoolean,
                        assets = assets
                    )
                } else null
            }
        }
    }
    
    private fun hasAndroidAssets(assets: JsonArray): Boolean {
        return assets.any {
            val name = it.asJsonObject.get("name").asString
            name.contains("android") && name.contains("frida-server")
        }
    }

    fun getAllPhantomReleases(callback: ReleasesCallback) {
        Thread {
            try {
                val releases = fetchAllPhantomReleases()
                callback.onReleasesLoaded(releases)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch phantom releases", e)
                callback.onError("Failed to fetch Phantom Frida releases: ${e.message}")
            }
        }.start()
    }

    private fun fetchAllPhantomReleases(): List<FridaRelease> {
        val request = Request.Builder().url("$PHANTOM_ALL_RELEASES_URL?per_page=20").build()
        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Failed to fetch releases: ${response.code}")
            val releasesArray = gson.fromJson(response.body?.string(), JsonArray::class.java)
            releasesArray.mapNotNull { element ->
                val obj = element.asJsonObject
                val assets = obj.getAsJsonArray("assets")
                if (assets.any { it.asJsonObject.get("name").asString.contains("android") }) {
                    FridaRelease(
                        tagName = obj.get("tag_name").asString,
                        name = obj.get("name")?.takeIf { !it.isJsonNull }?.asString
                            ?: obj.get("tag_name").asString,
                        publishedAt = obj.get("published_at").asString,
                        prerelease = obj.get("prerelease").asBoolean,
                        assets = assets,
                        isPhantom = true
                    )
                } else null
            }
        }
    }
    
    private fun findServerAsset(release: JsonObject, arch: String): String? {
        val assets = release.getAsJsonArray("assets")
        val version = release.get("tag_name").asString
        val expectedName = "frida-server-$version-android-$arch.xz"
        
        return assets.firstOrNull { it.asJsonObject.get("name").asString == expectedName }
            ?.asJsonObject?.get("browser_download_url")?.asString
    }
    
    private fun findServerAssetInRelease(release: FridaRelease, arch: String): String? {
        return if (release.isPhantom) {
            val expectedName = "ajeossida-server-${release.tagName}-android-$arch.gz"
            release.assets.firstOrNull { it.asJsonObject.get("name").asString == expectedName }
                ?.asJsonObject?.get("browser_download_url")?.asString
        } else {
            val expectedName = "frida-server-${release.tagName}-android-$arch.xz"
            release.assets.firstOrNull { it.asJsonObject.get("name").asString == expectedName }
                ?.asJsonObject?.get("browser_download_url")?.asString
        }
    }
    
    private fun downloadAssetWithProgress(url: String, callback: InstallCallback): File? {
        val request = Request.Builder().url(url).build()
        val downloadDir = getFridaDownloadDir()
        val fileName = url.substringAfterLast("/")
        val outputFile = File(downloadDir, fileName)
        
        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Failed to download: ${response.code}")
            
            val totalBytes = response.body?.contentLength() ?: 0L
            var downloadedBytes = 0L
            
            response.body?.byteStream()?.use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(4096)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        if (totalBytes > 0) {
                            val progress = ((downloadedBytes * 100) / totalBytes).toInt()
                            callback.onDownloadProgress(progress, downloadedBytes, totalBytes)
                        }
                    }
                }
            }
            outputFile
        }
    }
    
    private fun extractGzFile(gzFile: File): File? {
        val outputFile = File(getFridaInternalDir(), "frida-server")
        outputFile.delete()
        return try {
            java.io.FileInputStream(gzFile).use { fis ->
                java.util.zip.GZIPInputStream(fis).use { gzis ->
                    FileOutputStream(outputFile).use { fos ->
                        gzis.copyTo(fos)
                    }
                }
            }
            outputFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract GZ file", e)
            null
        }
    }

    private fun extractXzFile(xzFile: File): File? {
        val outputFile = File(getFridaInternalDir(), "frida-server")
        outputFile.delete()
        
        return try {
            FileInputStream(xzFile).use { fis ->
                XZInputStream(fis).use { xzis ->
                    FileOutputStream(outputFile).use { fos ->
                        xzis.copyTo(fos)
                    }
                }
            }
            outputFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract XZ file", e)
            null
        }
    }
    
    private fun setExecutablePermissions(file: File): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            process.outputStream.apply {
                write("chmod 755 ${file.absolutePath}\n".toByteArray())
                write("exit\n".toByteArray())
                flush()
            }
            val exitCode = process.waitFor()
            Log.d(TAG, "chmod exit code: $exitCode")
            exitCode == 0 && file.canExecute()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set permissions", e)
            false
        }
    }
    
    fun startFridaServer(callback: InstallCallback, port: Int = 27042) {
        Thread {
            try {
                val serverFile = File(getFridaInternalDir(), "frida-server")
                if (!serverFile.exists()) {
                    callback.onError("Frida server not found. Please install it first.")
                    return@Thread
                }

                callback.onProgress("🛑 Stopping any existing Frida server...")
                stopFridaServer()

                // Copy to /data/local/tmp — SELinux blocks exec from app private storage
                callback.onProgress("📋 Staging binary to /data/local/tmp...")
                val tmpPath = "/data/local/tmp/frida-server"
                val stageProcess = Runtime.getRuntime().exec("su")
                stageProcess.outputStream.apply {
                    write("cp ${serverFile.absolutePath} $tmpPath\n".toByteArray())
                    write("chmod 755 $tmpPath\n".toByteArray())
                    write("exit\n".toByteArray())
                    flush()
                }
                stageProcess.waitFor()

                callback.onProgress("🚀 Starting Frida server: $currentServerType")
                callback.onProgress("📡 Server will listen on 0.0.0.0:$port")
                callback.onProgress("📝 Real-time output will be shown below:")

                fridaServerProcess = Runtime.getRuntime().exec("su")
                val command = "$tmpPath -l 0.0.0.0:$port\n"
                fridaServerProcess?.outputStream?.apply {
                    write(command.toByteArray())
                    flush()
                }
                
                // Read stdout with graceful interrupt handling
                Thread {
                    try {
                        fridaServerProcess?.inputStream?.bufferedReader()?.use { reader ->
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                callback.onProgress("📤 [STDOUT] $line")
                            }
                        }
                    } catch (e: IOException) {
                        // Stream closed - normal when stopping server
                        Log.d(TAG, "Stdout stream closed")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error reading stdout", e)
                    }
                }.apply { isDaemon = true; start() }
                
                // Read stderr with graceful interrupt handling
                Thread {
                    try {
                        fridaServerProcess?.errorStream?.bufferedReader()?.use { reader ->
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                callback.onProgress("🔴 [STDERR] $line")
                            }
                        }
                    } catch (e: IOException) {
                        // Stream closed - normal when stopping server
                        Log.d(TAG, "Stderr stream closed")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error reading stderr", e)
                    }
                }.apply { isDaemon = true; start() }
                
                Thread.sleep(3000)
                
                if (isServerRunning()) {
                    val pid = runningServerPid
                    val pidInfo = if (pid != null) " (PID: $pid)" else ""
                    callback.onSuccess("✅ Frida server started on port $port$pidInfo!")
                } else {
                    callback.onError("❌ Failed to start Frida server. Check output above for errors.")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start server", e)
                callback.onError("Failed to start server: ${e.message}")
            }
        }.start()
    }
    
    fun stopFridaServer() {
        try {
            fridaServerProcess?.let { process ->
                if (process.isAlive) {
                    process.destroy()
                    Thread.sleep(500)
                    if (process.isAlive) {
                        process.destroyForcibly()
                    }
                }
            }
            fridaServerProcess = null
            
            // Kill any other frida-server processes
            val killProcess = Runtime.getRuntime().exec("su")
            killProcess.outputStream.apply {
                write("pkill -9 frida-server\n".toByteArray())
                write("killall frida-server 2>/dev/null\n".toByteArray())
                write("exit\n".toByteArray())
                flush()
            }
            killProcess.waitFor()
            Thread.sleep(500)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop server", e)
        }
    }
    
    fun isServerRunning(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            process.outputStream.apply {
                write("pgrep frida-server\n".toByteArray())
                write("exit\n".toByteArray())
                flush()
            }
            val pid = BufferedReader(InputStreamReader(process.inputStream)).readLine()
            val exitCode = process.waitFor()
            exitCode == 0 && !pid.isNullOrBlank()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check server status", e)
            false
        }
    }
    
    private fun getRunningServerPidInternal(): String? {
        return try {
            val process = Runtime.getRuntime().exec("su")
            process.outputStream.apply {
                write("pgrep frida-server\n".toByteArray())
                write("exit\n".toByteArray())
                flush()
            }
            val pid = BufferedReader(InputStreamReader(process.inputStream)).readLine()
            process.waitFor()
            pid?.trim()?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get server PID", e)
            null
        }
    }
    
    fun getInstalledServerVersion(): String? {
        val info = installedServerInfo ?: return null
        val spaceIndex = info.indexOf(" ")
        return if (spaceIndex > 0) info.substring(0, spaceIndex) else info
    }
    
    private fun saveServerInfo(version: String, arch: String) {
        try {
            File(getFridaInternalDir(), "server-info.txt").writeText("$version ($arch)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save server info", e)
        }
    }
    
    private fun formatFileSize(bytes: Long): String = when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> String.format("%.1fKB", bytes / 1024.0)
        else -> String.format("%.1fMB", bytes / (1024.0 * 1024.0))
    }
    fun executeSuCommand(command: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            process.outputStream.apply {
                write((command + "\n").toByteArray())
                write("exit\n".toByteArray())
                flush()
            }
            val exitCode = process.waitFor()
            Log.d(TAG, "Command execution exit code: $exitCode for command: $command")
            exitCode == 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute SU command: $command", e)
            false
        }
    }
}
