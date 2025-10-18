import 'dart:io';

import 'package:flutter/material.dart';

import 'l10n/app_localizations.dart';
import 'preferences.dart';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  bool advanced = false;

  String _getAccuracyLabel(String? key) {
    return switch (key) {
      'highest' => AppLocalizations.of(context)!.highestAccuracyLabel,
      'high' => AppLocalizations.of(context)!.highAccuracyLabel,
      'low' => AppLocalizations.of(context)!.lowAccuracyLabel,
      _ => AppLocalizations.of(context)!.mediumAccuracyLabel,
    };
  }

  Widget _buildListTile(String title, String key, bool isInt) {
    String? value;
    if (isInt) {
      final intValue = Preferences.instance.getInt(key);
      if (intValue != null && intValue > 0) {
        value = intValue.toString();
      } else {
        value = AppLocalizations.of(context)!.disabledValue;
      }
    } else {
      value = Preferences.instance.getString(key);
    }
    return ListTile(
      title: Text(title),
      subtitle: Text(value ?? ''),
      // Disabled editing - hardcoded configuration for fleet monitoring
      enabled: false,
    );
  }

  Widget _buildAccuracyListTile() {
    return ListTile(
      title: Text(AppLocalizations.of(context)!.accuracyLabel),
      subtitle: Text(_getAccuracyLabel(Preferences.instance.getString(Preferences.accuracy))),
      // Disabled editing - hardcoded configuration for fleet monitoring
      enabled: false,
    );
  }

  @override
  Widget build(BuildContext context) {
    final isHighestAccuracy = Preferences.instance.getString(Preferences.accuracy) == 'highest';
    final distance = Preferences.instance.getInt(Preferences.distance);
    return Scaffold(
      appBar: AppBar(
        title: Text(AppLocalizations.of(context)!.settingsTitle),
        // QR code scanner disabled for hardcoded configuration
      ),
      body: ListView(
        children: [
          _buildListTile(AppLocalizations.of(context)!.idLabel, Preferences.id, false),
          _buildListTile(AppLocalizations.of(context)!.urlLabel, Preferences.url, false),
          _buildAccuracyListTile(),
          _buildListTile(AppLocalizations.of(context)!.distanceLabel, Preferences.distance, true),
          if (isHighestAccuracy || Platform.isAndroid && distance == 0)
            _buildListTile(AppLocalizations.of(context)!.intervalLabel, Preferences.interval, true),
          if (isHighestAccuracy)
            _buildListTile(AppLocalizations.of(context)!.angleLabel, Preferences.angle, true),
          _buildListTile(AppLocalizations.of(context)!.heartbeatLabel, Preferences.heartbeat, true),
          SwitchListTile(
            title: Text(AppLocalizations.of(context)!.advancedLabel),
            value: advanced,
            // Allow viewing advanced settings but not changing configuration
            onChanged: (value) {
              setState(() => advanced = value);
            },
          ),
          if (advanced)
            _buildListTile(AppLocalizations.of(context)!.fastestIntervalLabel, Preferences.fastestInterval, true),
          if (advanced)
            SwitchListTile(
              title: Text(AppLocalizations.of(context)!.bufferLabel),
              value: Preferences.instance.getBool(Preferences.buffer) ?? true,
              // Disabled editing - hardcoded configuration for fleet monitoring
              onChanged: null,
            ),
          if (advanced && Platform.isAndroid)
            SwitchListTile(
              title: Text(AppLocalizations.of(context)!.wakelockLabel),
              value: Preferences.instance.getBool(Preferences.wakelock) ?? false,
              // Disabled editing - hardcoded configuration for fleet monitoring
              onChanged: null,
            ),
          if (advanced)
            SwitchListTile(
              title: Text(AppLocalizations.of(context)!.stopDetectionLabel),
              value: Preferences.instance.getBool(Preferences.stopDetection) ?? true,
              // Disabled editing - hardcoded configuration for fleet monitoring
              onChanged: null,
            ),
          if (advanced)
            ListTile(
              title: Text(AppLocalizations.of(context)!.passwordLabel),
              subtitle: const Text('Password is hardcoded for fleet monitoring'),
              // Disabled password change - hardcoded configuration
              enabled: false,
            ),
        ],
      ),
    );
  }
}
