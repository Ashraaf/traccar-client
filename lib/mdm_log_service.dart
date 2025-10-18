import 'dart:io';
import 'package:flutter/services.dart';

/// Service to log messages to Headwind MDM
class MDMLogService {
  static const MethodChannel _channel = MethodChannel('org.traccar.client/mdm_log');

  /// Log an info message to Headwind MDM
  static Future<void> info(String message, {String tag = 'TraccarClient'}) async {
    if (Platform.isAndroid) {
      try {
        await _channel.invokeMethod('logInfo', {
          'tag': tag,
          'message': message,
        });
      } catch (e) {
        // Fallback to regular print if MDM service is not available
        print('[$tag] INFO: $message');
      }
    }
  }

  /// Log a debug message to Headwind MDM
  static Future<void> debug(String message, {String tag = 'TraccarClient'}) async {
    if (Platform.isAndroid) {
      try {
        await _channel.invokeMethod('logDebug', {
          'tag': tag,
          'message': message,
        });
      } catch (e) {
        print('[$tag] DEBUG: $message');
      }
    }
  }

  /// Log a warning message to Headwind MDM
  static Future<void> warning(String message, {String tag = 'TraccarClient'}) async {
    if (Platform.isAndroid) {
      try {
        await _channel.invokeMethod('logWarning', {
          'tag': tag,
          'message': message,
        });
      } catch (e) {
        print('[$tag] WARNING: $message');
      }
    }
  }

  /// Log an error message to Headwind MDM
  static Future<void> error(String message, {String tag = 'TraccarClient'}) async {
    if (Platform.isAndroid) {
      try {
        await _channel.invokeMethod('logError', {
          'tag': tag,
          'message': message,
        });
      } catch (e) {
        print('[$tag] ERROR: $message');
      }
    }
  }

  /// Log a verbose message to Headwind MDM
  static Future<void> verbose(String message, {String tag = 'TraccarClient'}) async {
    if (Platform.isAndroid) {
      try {
        await _channel.invokeMethod('logVerbose', {
          'tag': tag,
          'message': message,
        });
      } catch (e) {
        print('[$tag] VERBOSE: $message');
      }
    }
  }
}
