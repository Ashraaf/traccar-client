package org.traccar.client

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import com.hmdm.MDMService
import com.hmdm.HeadwindMDM

class MainActivity : FlutterActivity(), HeadwindMDM.EventHandler {
    private val CHANNEL = "org.traccar.client/mdm_log"
    private lateinit var headwindMDM: HeadwindMDM
    private var cachedDeviceId: String? = null

    // Helper function to get Device ID from SharedPreferences or cache
    private fun getTraccarDeviceId(): String {
        // Return cached value if available
        if (cachedDeviceId != null) {
            return cachedDeviceId!!
        }
        
        // Try to read from SharedPreferences
        val prefs = getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
        val deviceId = prefs.getString("flutter.hardware_unique_id", null) 
            ?: prefs.getString("flutter.id", null)
        
        if (deviceId != null) {
            cachedDeviceId = deviceId
            return deviceId
        }
        
        return "Unknown"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        headwindMDM = HeadwindMDM.getInstance()
        
        // Log Device ID immediately on creation
        val deviceId = getTraccarDeviceId()
        MDMService.Log.i("TraccarClient", "MainActivity onCreate - Device ID: $deviceId")
        
        // Check and attempt to grant permissions automatically
        val permissionManager = PermissionManager(this)
        permissionManager.logPermissionStatus()
        
        // Attempt automatic permission grant (works when app is Device Owner managed)
        permissionManager.grantAllPermissions()
        
        // Start restart service to keep app alive even when cleared from recents
        startRestartService()
    }
    
    private fun startRestartService() {
        try {
            val serviceIntent = Intent(this, RestartService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            MDMService.Log.i("TraccarClient", "RestartService started - App will survive recents clear")
        } catch (e: Exception) {
            MDMService.Log.e("TraccarClient", "Failed to start RestartService: ${e.message}")
        }
    }

    override fun onResume() {
        super.onResume()
        
        val deviceId = getTraccarDeviceId()
        MDMService.Log.i("TraccarClient", "MainActivity onResume - Device ID: $deviceId")
        
        if (!headwindMDM.isConnected()) {
            if (!headwindMDM.connect(this, this)) {
                // Application is running outside Headwind MDM
                MDMService.Log.w("TraccarClient", "Running outside Headwind MDM - Device ID: $deviceId")
            } else {
                MDMService.Log.i("TraccarClient", "Connecting to Headwind MDM - Device ID: $deviceId")
            }
        } else {
            // Already connected
            MDMService.Log.i("TraccarClient", "Already connected to Headwind MDM - Device ID: $deviceId")
            loadSettings()
        }
    }

    override fun onDestroy() {
        headwindMDM.disconnect(this)
        super.onDestroy()
    }

    override fun onHeadwindMDMConnected() {
        // Connected to Headwind MDM
        val deviceId = getTraccarDeviceId()
        MDMService.Log.i("TraccarClient", "Connected to Headwind MDM - Device ID: $deviceId")
        loadSettings()
    }

    override fun onHeadwindMDMDisconnected() {
        val deviceId = getTraccarDeviceId()
        MDMService.Log.i("TraccarClient", "Disconnected from Headwind MDM - Device ID: $deviceId")
    }

    override fun onHeadwindMDMConfigChanged() {
        // Settings changed on the server
        val deviceId = getTraccarDeviceId()
        MDMService.Log.i("TraccarClient", "Headwind MDM config changed - Device ID: $deviceId")
        loadSettings()
    }

    private fun loadSettings() {
        // Load settings from Headwind MDM if needed
        val deviceId = getTraccarDeviceId()
        MDMService.Log.i("TraccarClient", "Loading Headwind MDM settings - Device ID: $deviceId")
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "setDeviceId" -> {
                    val deviceId = call.argument<String>("deviceId")
                    if (deviceId != null) {
                        cachedDeviceId = deviceId
                        MDMService.Log.i("TraccarClient", "Device ID cached from Flutter: $deviceId")
                    }
                    result.success(null)
                }
                "logInfo" -> {
                    val tag = call.argument<String>("tag") ?: "TraccarClient"
                    val message = call.argument<String>("message") ?: ""
                    MDMService.Log.i(tag, message)
                    result.success(null)
                }
                "logDebug" -> {
                    val tag = call.argument<String>("tag") ?: "TraccarClient"
                    val message = call.argument<String>("message") ?: ""
                    MDMService.Log.d(tag, message)
                    result.success(null)
                }
                "logWarning" -> {
                    val tag = call.argument<String>("tag") ?: "TraccarClient"
                    val message = call.argument<String>("message") ?: ""
                    MDMService.Log.w(tag, message)
                    result.success(null)
                }
                "logError" -> {
                    val tag = call.argument<String>("tag") ?: "TraccarClient"
                    val message = call.argument<String>("message") ?: ""
                    MDMService.Log.e(tag, message)
                    result.success(null)
                }
                "logVerbose" -> {
                    val tag = call.argument<String>("tag") ?: "TraccarClient"
                    val message = call.argument<String>("message") ?: ""
                    MDMService.Log.v(tag, message)
                    result.success(null)
                }
                else -> {
                    result.notImplemented()
                }
            }
        }
    }
}
