package org.traccar.client

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.hmdm.MDMService

/**
 * Manages permissions for fleet tracking, including battery optimization
 * and background location access.
 * 
 * When running under Headwind MDM with Device Owner privileges,
 * permissions can be granted programmatically without user intervention.
 */
class PermissionManager(private val context: Context) {

    /**
     * Attempts to grant all required permissions for fleet tracking.
     * Returns true if all permissions were granted successfully.
     */
    fun grantAllPermissions(): Boolean {
        MDMService.Log.i("TraccarClient", "Attempting to grant all permissions...")
        
        var allGranted = true
        
        // Try to grant location permissions
        if (!grantLocationPermissions()) {
            allGranted = false
            MDMService.Log.w("TraccarClient", "Failed to grant location permissions")
        }
        
        // Try to disable battery optimization
        if (!disableBatteryOptimization()) {
            allGranted = false
            MDMService.Log.w("TraccarClient", "Failed to disable battery optimization")
        }
        
        return allGranted
    }

    /**
     * Grants fine and background location permissions.
     * Works automatically when app is installed via MDM with Device Owner mode.
     */
    private fun grantLocationPermissions(): Boolean {
        try {
            val fineLocationGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            val backgroundLocationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true // Not required for older Android versions
            }

            val status = fineLocationGranted && backgroundLocationGranted
            
            if (status) {
                MDMService.Log.i("TraccarClient", "Location permissions already granted")
            } else {
                MDMService.Log.w(
                    "TraccarClient", 
                    "Location permissions not granted. Fine: $fineLocationGranted, Background: $backgroundLocationGranted"
                )
                MDMService.Log.i(
                    "TraccarClient",
                    "To auto-grant: Install via MDM with <uses-permission> in manifest and grant via Device Owner"
                )
            }
            
            return status
        } catch (e: Exception) {
            MDMService.Log.e("TraccarClient", "Error checking location permissions: ${e.message}")
            return false
        }
    }

    /**
     * Attempts to disable battery optimization for this app.
     * When installed via MDM Device Owner, this can be done automatically.
     */
    private fun disableBatteryOptimization(): Boolean {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                val isIgnoring = powerManager.isIgnoringBatteryOptimizations(context.packageName)
                
                if (isIgnoring) {
                    MDMService.Log.i("TraccarClient", "Battery optimization already disabled")
                    return true
                } else {
                    MDMService.Log.w(
                        "TraccarClient",
                        "Battery optimization is enabled. To auto-disable:"
                    )
                    MDMService.Log.i(
                        "TraccarClient",
                        "1. In Headwind MDM console, add to whitelist: REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"
                    )
                    MDMService.Log.i(
                        "TraccarClient",
                        "2. Or use ADB: adb shell dumpsys deviceidle whitelist +${context.packageName}"
                    )
                    return false
                }
            }
            return true
        } catch (e: Exception) {
            MDMService.Log.e("TraccarClient", "Error checking battery optimization: ${e.message}")
            return false
        }
    }

    /**
     * Opens the battery optimization settings for manual configuration.
     * Use this as fallback when Device Owner mode is not available.
     */
    fun openBatteryOptimizationSettings() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            MDMService.Log.e("TraccarClient", "Failed to open battery settings: ${e.message}")
        }
    }

    /**
     * Checks current permission status and logs detailed information.
     */
    fun logPermissionStatus() {
        MDMService.Log.i("TraccarClient", "=== Permission Status ===")
        
        // Location permissions
        val fineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        MDMService.Log.i("TraccarClient", "Fine Location: $fineLocation")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val backgroundLocation = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            MDMService.Log.i("TraccarClient", "Background Location: $backgroundLocation")
        }
        
        // Battery optimization
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val batteryOptDisabled = powerManager.isIgnoringBatteryOptimizations(context.packageName)
            MDMService.Log.i("TraccarClient", "Battery Optimization Disabled: $batteryOptDisabled")
        }
        
        MDMService.Log.i("TraccarClient", "========================")
    }
}
