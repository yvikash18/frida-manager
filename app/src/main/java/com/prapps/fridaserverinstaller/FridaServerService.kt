package com.prapps.fridaserverinstaller

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class FridaServerService : Service() {
    
    companion object {
        private const val TAG = "FridaServerService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "frida_server_channel"
        
        const val ACTION_START = "com.prapps.fridaserverinstaller.START"
        const val ACTION_STOP = "com.prapps.fridaserverinstaller.STOP"
        const val EXTRA_PORT = "port"
    }
    
    private var serverProcess: Process? = null
    private var currentPort: Int = PreferencesManager.DEFAULT_PORT
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                currentPort = intent.getIntExtra(EXTRA_PORT, PreferencesManager.DEFAULT_PORT)
                startForeground(NOTIFICATION_ID, createNotification("Starting..."))
                startServer()
            }
            ACTION_STOP -> {
                stopServer()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }
    
    private fun startServer() {
        Thread {
            try {
                val fridaDir = File(filesDir, "frida")
                val serverFile = File(fridaDir, "frida-server")
                
                if (!serverFile.exists()) {
                    Log.e(TAG, "Frida server not found")
                    updateNotification("Error: Server not installed")
                    return@Thread
                }
                
                // Kill any existing server
                stopExistingServer()
                
                // Start new server
                serverProcess = Runtime.getRuntime().exec("su")
                val command = "cd /data/local/tmp && ${serverFile.absolutePath} -l 0.0.0.0:$currentPort\n"
                serverProcess?.outputStream?.write(command.toByteArray())
                serverProcess?.outputStream?.flush()
                
                Thread.sleep(2000)
                
                val pid = getServerPid()
                if (pid != null) {
                    updateNotification("Running on port $currentPort (PID: $pid)")
                    Log.d(TAG, "Frida server started with PID: $pid")
                } else {
                    updateNotification("Failed to start server")
                    Log.e(TAG, "Failed to start Frida server")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error starting server", e)
                updateNotification("Error: ${e.message}")
            }
        }.start()
    }
    
    private fun stopServer() {
        try {
            serverProcess?.destroy()
            serverProcess = null
            stopExistingServer()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping server", e)
        }
    }
    
    private fun stopExistingServer() {
        try {
            val process = Runtime.getRuntime().exec("su")
            process.outputStream.write("pkill frida-server\n".toByteArray())
            process.outputStream.write("exit\n".toByteArray())
            process.outputStream.flush()
            process.waitFor()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping existing server", e)
        }
    }
    
    private fun getServerPid(): String? {
        return try {
            val process = Runtime.getRuntime().exec("su")
            process.outputStream.write("pgrep frida-server\n".toByteArray())
            process.outputStream.write("exit\n".toByteArray())
            process.outputStream.flush()
            
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val pid = reader.readLine()
            process.waitFor()
            pid?.trim()?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Frida Server",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Frida Server status notifications"
        }
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
    
    private fun createNotification(status: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        
        val stopIntent = Intent(this, FridaServerService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Frida Server")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_delete, "Stop", stopPendingIntent)
            .setOngoing(true)
            .build()
    }
    
    private fun updateNotification(status: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(status))
    }
    
    override fun onDestroy() {
        stopServer()
        super.onDestroy()
    }
}
