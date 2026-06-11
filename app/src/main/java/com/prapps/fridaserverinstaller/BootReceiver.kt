package com.prapps.fridaserverinstaller

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "BootReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Boot completed received")

            val prefs = PreferencesManager(context)

            if (prefs.isWifiAdbOnBootEnabled) {
                Log.d(TAG, "WiFi ADB on boot enabled, enabling TCP ADB on port 5555")
                try {
                    val process = Runtime.getRuntime().exec("su")
                    process.outputStream.apply {
                        write("setprop service.adb.tcp.port 5555\n".toByteArray())
                        write("stop adbd\n".toByteArray())
                        write("start adbd\n".toByteArray())
                        write("exit\n".toByteArray())
                        flush()
                    }
                    process.waitFor()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to enable WiFi ADB on boot", e)
                }
            }

            if (prefs.isAutoStartEnabled) {
                Log.d(TAG, "Auto-start enabled, starting Frida server service")
                val serviceIntent = Intent(context, FridaServerService::class.java).apply {
                    action = FridaServerService.ACTION_START
                    putExtra(FridaServerService.EXTRA_PORT, prefs.serverPort)
                }
                context.startForegroundService(serviceIntent)
            } else {
                Log.d(TAG, "Auto-start disabled, not starting server")
            }
        }
    }
}
