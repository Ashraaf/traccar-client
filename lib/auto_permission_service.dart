import 'dart:io';
import 'package:flutter/services.dart';
import 'package:flutter_background_geolocation/flutter_background_geolocation.dart' as bg;
import 'mdm_log_service.dart';

/// Service to automatically request and verify critical permissions
/// for fleet tracking. Works in conjunction with MDM Device Owner mode
/// to enable permissions without user interaction.
class AutoPermissionService {
  static const MethodChannel _channel = MethodChannel('org.traccar.client/permissions');

  /// Initialize and attempt to grant all required permissions
  /// Returns true if all permissions are granted
  static Future<bool> initializePermissions() async {
    if (!Platform.isAndroid) {
      // iOS handles permissions differently
      return await _requestIOSPermissions();
    }

    await MDMLogService.info('Starting automatic permission initialization...');

    try {
      // Request location permissions through flutter_background_geolocation
      // This will automatically handle Android 10+ background location
      final status = await bg.BackgroundGeolocation.requestPermission();
      
      await MDMLogService.info('Location permission status: $status');

      // Check if all required permissions are granted
      final allGranted = await verifyAllPermissions();
      
      if (allGranted) {
        await MDMLogService.info('✓ All permissions granted successfully');
      } else {
        await MDMLogService.warning('⚠ Some permissions are missing - see logs');
      }

      return allGranted;
    } catch (e) {
      await MDMLogService.error('Error initializing permissions: $e');
      return false;
    }
  }

  /// Verify all required permissions are granted
  static Future<bool> verifyAllPermissions() async {
    if (!Platform.isAndroid) return true;

    try {
      // Check location permission
      final locationStatus = await bg.BackgroundGeolocation.requestPermission();
      final hasLocation = locationStatus == 3; // ALWAYS permission

      // Check battery optimization
      final batteryOptDisabled = await bg.DeviceSettings.isIgnoringBatteryOptimizations;

      await MDMLogService.info('=== Permission Verification ===');
      await MDMLogService.info('Location (Always): $hasLocation');
      await MDMLogService.info('Battery Optimization Disabled: $batteryOptDisabled');
      await MDMLogService.info('==============================');

      return hasLocation && batteryOptDisabled;
    } catch (e) {
      await MDMLogService.error('Error verifying permissions: $e');
      return false;
    }
  }

  /// Request battery optimization exemption
  /// In Device Owner mode, this can be granted automatically
  static Future<bool> requestBatteryOptimizationExemption() async {
    if (!Platform.isAndroid) return true;

    try {
      final isIgnoring = await bg.DeviceSettings.isIgnoringBatteryOptimizations;
      
      if (isIgnoring) {
        await MDMLogService.info('Battery optimization already disabled');
        return true;
      }

      await MDMLogService.info('Requesting battery optimization exemption...');
      
      // This will prompt the user if not in Device Owner mode
      // In Device Owner mode with proper MDM config, it's automatic
      final result = await bg.DeviceSettings.showIgnoreBatteryOptimizations();
      
      await MDMLogService.info('Battery optimization request: seen=${result.seen}');
      
      // Verify after request
      final isIgnoringNow = await bg.DeviceSettings.isIgnoringBatteryOptimizations;
      
      if (isIgnoringNow) {
        await MDMLogService.info('✓ Battery optimization disabled successfully');
      } else {
        await MDMLogService.warning('⚠ Battery optimization still enabled');
        await MDMLogService.info('For automatic grant: Ensure Device Owner mode and MDM whitelist');
      }

      return isIgnoringNow;
    } catch (e) {
      await MDMLogService.error('Error requesting battery optimization exemption: $e');
      return false;
    }
  }

  /// Request location permissions with proper rationale
  static Future<bool> requestLocationPermissions() async {
    try {
      if (Platform.isIOS) {
        // iOS: Request always authorization
        final status = await bg.BackgroundGeolocation.requestPermission();
        return status == 3; // ALWAYS permission
      }

      // Android: Request permissions
      await MDMLogService.info('Requesting location permissions...');
      
      final status = await bg.BackgroundGeolocation.requestPermission();
      
      await MDMLogService.info('Location permission result: $status');

      if (status == 3) { // ALWAYS permission
        await MDMLogService.info('✓ Location permissions granted (Always)');
        return true;
      } else if (status == 2) { // WHEN_IN_USE permission
        await MDMLogService.warning('⚠ Location permission: While in use only');
        await MDMLogService.info('For fleet tracking, "Allow all the time" is required');
        
        // Request battery settings as a workaround to show device settings
        if (Platform.isAndroid) {
          try {
            final request = await bg.DeviceSettings.showIgnoreBatteryOptimizations();
            // Show the settings dialog
            await bg.DeviceSettings.show(request);
          } catch (e) {
            // Fallback if settings can't be opened
            await MDMLogService.warning('Could not open settings: $e');
          }
        }
        
        return false;
      } else {
        await MDMLogService.error('✗ Location permissions denied');
        return false;
      }
    } catch (e) {
      await MDMLogService.error('Error requesting location permissions: $e');
      return false;
    }
  }

  /// iOS-specific permission requests
  static Future<bool> _requestIOSPermissions() async {
    try {
      // Request always authorization for iOS
      final status = await bg.BackgroundGeolocation.requestPermission();
      
      if (status == 3) { // ALWAYS permission
        await MDMLogService.info('✓ iOS: Location "Always" permission granted');
        return true;
      } else {
        await MDMLogService.warning('⚠ iOS: Location permission not "Always"');
        return false;
      }
    } catch (e) {
      await MDMLogService.error('iOS permission error: $e');
      return false;
    }
  }

  /// Log detailed permission status for troubleshooting
  static Future<void> logDetailedStatus() async {
    await MDMLogService.info('=== Detailed Permission Status ===');
    
    try {
      // Location permission
      final locationStatus = await bg.BackgroundGeolocation.requestPermission();
      await MDMLogService.info('Location Permission: $locationStatus');
      
      // Battery optimization
      if (Platform.isAndroid) {
        final batteryOpt = await bg.DeviceSettings.isIgnoringBatteryOptimizations;
        await MDMLogService.info('Battery Optimization Disabled: $batteryOpt');
        
        // Check if app can draw overlays (sometimes required)
        try {
          final canDrawOverlays = await _channel.invokeMethod<bool>('canDrawOverlays') ?? false;
          await MDMLogService.info('Can Draw Overlays: $canDrawOverlays');
        } catch (e) {
          // Method might not be implemented yet
        }
      }
      
      // Background geolocation state
      final state = await bg.BackgroundGeolocation.state;
      await MDMLogService.info('Tracking Enabled: ${state.enabled}');
      await MDMLogService.info('Moving: ${state.isMoving}');
      
    } catch (e) {
      await MDMLogService.error('Error getting detailed status: $e');
    }
    
    await MDMLogService.info('=================================');
  }

  /// Guide for MDM administrators
  static Future<void> logMDMSetupGuide() async {
    await MDMLogService.info('=== MDM Setup Guide ===');
    await MDMLogService.info('To automatically grant permissions:');
    await MDMLogService.info('');
    await MDMLogService.info('1. Ensure devices are enrolled as Device Owner');
    await MDMLogService.info('   Command: adb shell dpm set-device-owner com.hmdm.launcher/.AdminReceiver');
    await MDMLogService.info('');
    await MDMLogService.info('2. In Headwind MDM console, configure app permissions:');
    await MDMLogService.info('   - ACCESS_FINE_LOCATION: Auto-grant');
    await MDMLogService.info('   - ACCESS_BACKGROUND_LOCATION: Auto-grant');
    await MDMLogService.info('   - REQUEST_IGNORE_BATTERY_OPTIMIZATIONS: Auto-grant');
    await MDMLogService.info('');
    await MDMLogService.info('3. Add to battery whitelist:');
    await MDMLogService.info('   Package: org.traccar.client');
    await MDMLogService.info('');
    await MDMLogService.info('4. Or use ADB for existing devices:');
    await MDMLogService.info('   adb shell pm grant org.traccar.client android.permission.ACCESS_FINE_LOCATION');
    await MDMLogService.info('   adb shell pm grant org.traccar.client android.permission.ACCESS_BACKGROUND_LOCATION');
    await MDMLogService.info('   adb shell dumpsys deviceidle whitelist +org.traccar.client');
    await MDMLogService.info('');
    await MDMLogService.info('See MDM_CONFIGURATION_GUIDE.md for detailed instructions');
    await MDMLogService.info('=====================');
  }
}
