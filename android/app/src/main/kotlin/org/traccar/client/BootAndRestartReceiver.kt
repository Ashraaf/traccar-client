package org.traccar.client

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.hmdm.MDMService

/**
 * Receiver to restart tracking service when:
 * - Device boots up
 * - App is killed and needs to restart
 */
class BootAndRestartReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                MDMService.Log.i("TraccarClient", "Device booted - Starting tracking service")
                startServices(context)
            }
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                MDMService.Log.i("TraccarClient", "App updated - Restarting tracking service")
                startServices(context)
            }
            "android.intent.action.PACKAGE_RESTARTED" -> {
                MDMService.Log.w("TraccarClient", "App process killed - Restarting")
                startServices(context)
            }
        }
    }

    private fun startServices(context: Context) {
        try {
            // Start the restart service
            val serviceIntent = Intent(context, RestartService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            
            MDMService.Log.i("TraccarClient", "Services started successfully")
        } catch (e: Exception) {
            MDMService.Log.e("TraccarClient", "Failed to start services: ${e.message}")
        }
    }
}
