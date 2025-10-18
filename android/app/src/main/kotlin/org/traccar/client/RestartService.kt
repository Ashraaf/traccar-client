package org.traccar.client

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.hmdm.MDMService

/**
 * Service to restart the app if it's killed by the system or user
 * This runs independently of the main app process
 */
class RestartService : Service() {

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MDMService.Log.i("TraccarClient", "RestartService: Ensuring tracking continues")
        return START_STICKY // System will recreate service if killed
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        
        MDMService.Log.w("TraccarClient", "App removed from recents - Restarting...")
        
        // Restart the app
        val restartIntent = Intent(applicationContext, MainActivity::class.java)
        restartIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        applicationContext.startActivity(restartIntent)
        
        // Restart this service
        val serviceIntent = Intent(applicationContext, RestartService::class.java)
        applicationContext.startService(serviceIntent)
        
        stopSelf()
    }
}
