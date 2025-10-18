import 'dart:io';
import 'package:flutter_background_geolocation/flutter_background_geolocation.dart' as bg;
import 'package:shared_preferences/shared_preferences.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'mdm_log_service.dart';

/// Detects app updates and re-requests permissions if needed
class VersionCheckService {
  static const String _lastVersionKey = 'last_app_version';
  
  /// Check if this is an app update and re-request permissions if needed
  static Future<void> checkAndUpdatePermissions() async {
    if (!Platform.isAndroid) return;
    
    try {
      final prefs = await SharedPreferences.getInstance();
      final packageInfo = await PackageInfo.fromPlatform();
      final currentVersion = packageInfo.version;
      final lastVersion = prefs.getString(_lastVersionKey);
      
      await MDMLogService.info('Current version: $currentVersion');
      await MDMLogService.info('Last known version: ${lastVersion ?? "first install"}');
      
      if (lastVersion == null) {
        // First install
        await MDMLogService.info('First installation detected');
        await prefs.setString(_lastVersionKey, currentVersion);
        return;
      }
      
      if (lastVersion != currentVersion) {
        // App was updated
        await MDMLogService.info('App update detected: $lastVersion → $currentVersion');
        await _handleAppUpdate(lastVersion, currentVersion);
        await prefs.setString(_lastVersionKey, currentVersion);
      } else {
        await MDMLogService.info('App version unchanged');
      }
    } catch (e) {
      await MDMLogService.error('Error checking app version: $e');
    }
  }
  
  /// Handle permission re-request after app update
  static Future<void> _handleAppUpdate(String fromVersion, String toVersion) async {
    await MDMLogService.info('Handling permissions after update...');
    
    // Check if background location is granted
    final locationStatus = await bg.BackgroundGeolocation.requestPermission();
    
    if (locationStatus == 3) { // ALWAYS permission
      await MDMLogService.info('✓ Background location already granted');
      return;
    }
    
    await MDMLogService.warning('⚠ Background location not granted after update');
    await MDMLogService.info('Attempting to re-request background location permission...');
    
    // Force permission request
    try {
      // First ensure foreground permission
      final newStatus = await bg.BackgroundGeolocation.requestPermission();
      
      if (newStatus == 3) {
        await MDMLogService.info('✓ Background location granted successfully');
      } else if (newStatus == 2) {
        await MDMLogService.warning('⚠ Location granted but only "While using app"');
        await MDMLogService.info('');
        await MDMLogService.info('=== ACTION REQUIRED ===');
        await MDMLogService.info('Due to Android restrictions, background location permission');
        await MDMLogService.info('cannot be automatically granted on app UPDATE.');
        await MDMLogService.info('');
        await MDMLogService.info('SOLUTION: Open app settings and change location to "Allow all the time"');
        await MDMLogService.info('OR: Uninstall and reinstall the app');
        await MDMLogService.info('=====================');
        
        // Try to open app settings
        await _openAppSettings();
      } else {
        await MDMLogService.error('✗ Location permission denied');
      }
    } catch (e) {
      await MDMLogService.error('Error requesting permissions: $e');
    }
  }
  
  /// Open app settings to allow user to change permissions
  static Future<void> _openAppSettings() async {
    try {
      final request = await bg.DeviceSettings.showIgnoreBatteryOptimizations();
      await bg.DeviceSettings.show(request);
      await MDMLogService.info('Opened app settings');
    } catch (e) {
      await MDMLogService.warning('Could not open app settings: $e');
    }
  }
  
  /// Force uninstall/reinstall message for MDM administrators
  static Future<void> logMDMUpdateInstructions() async {
    await MDMLogService.info('');
    await MDMLogService.info('=== MDM UPDATE INSTRUCTIONS ===');
    await MDMLogService.info('If devices are not getting background location after update:');
    await MDMLogService.info('');
    await MDMLogService.info('Option 1: Uninstall → Reinstall via MDM');
    await MDMLogService.info('  - In MDM console, remove app from device group');
    await MDMLogService.info('  - Wait for uninstall to complete');
    await MDMLogService.info('  - Add app back to device group');
    await MDMLogService.info('  - Fresh install will request all permissions');
    await MDMLogService.info('');
    await MDMLogService.info('Option 2: ADB Command (for specific devices)');
    await MDMLogService.info('  adb shell pm grant org.traccar.client android.permission.ACCESS_BACKGROUND_LOCATION');
    await MDMLogService.info('');
    await MDMLogService.info('Option 3: User opens Settings → Apps → Traccar → Permissions → Location → "Allow all the time"');
    await MDMLogService.info('');
    await MDMLogService.info('Root Cause: Android 10+ does not re-prompt for new permissions on app update');
    await MDMLogService.info('==============================');
  }
}
