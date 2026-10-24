# [Traccar Client app](https://www.traccar.org/client)

[![Get it on Google Play](https://www.tananaev.com/badges/google-play.svg)](https://play.google.com/store/apps/details?id=org.traccar.client) [![Download on the App Store](https://www.tananaev.com/badges/app-store.svg)](https://itunes.apple.com/app/traccar-client/id843156974)

## RapidBus Android Fleet Build

This branch retains the latest upstream project but replaces Android's Flutter
entry point with native, headless tracking using Traccar Client SDK 1.1.1 and
Headwind SDK 1.1.10. The iOS implementation is unchanged.

- Package: `org.traccar.client`, retained for in-place fleet upgrades.
- Version: `10.1.6`, version code `162` (previous local build: `161`).
- Endpoint: `http://avl.rapidbus-it.com:5055`.
- Identifier: Headwind's `getDeviceId()`, preserved exactly, including leading
  zeros. No random identifier or old Flutter identifier is used. A previously
  obtained Headwind ID is cached for temporary MDM disconnections; a connected
  agent's ID changes are applied on configuration updates.
- GPS: navigation accuracy (`HIGHEST`) for frequent fixes, with reports accepted
	when any of these thresholds is met: 50 metres travelled, 10 seconds elapsed,
	or a 30-degree heading change since the last accepted fix. This is a practical
	starting profile for vans, not a guaranteed reporting cadence. Stop detection
	is disabled; wake lock and offline buffering are enabled. Android/GPS
	availability can delay actual reports. Continuous GPS increases battery usage.
- Startup: boot after user unlock, package upgrade, SDK service recovery, and
  Headwind activation. Missing provisioning is retried through JobScheduler;
  retry timing is controlled by Android, not an exact timer.
- No screens, settings, permission dialogs, battery-settings popups, or password.
  The no-display launcher entry immediately finishes and exists so Headwind can
  activate fresh installs. It does not load Flutter or appear in recents.
- Android's foreground-service notification remains visible:
	`Company vehicle location tracking`. Android requires it while tracking.
- Flutter Firebase messaging entry points are removed on Android so an old
	registered background callback cannot restore tracking from legacy preferences.
	Firebase position/start/stop commands are not supported by this fleet build.

### Headwind Provisioning

1. Provision Headwind as **device owner** on the fully managed company phones.
	Ordinary installation, legacy device admin, or a personal work profile is
	not sufficient for unattended sensor-permission grants on Android 15/16.
	Device-owner provisioning must not opt out of sensor-permission control.
2. Set Headwind's **Permissions for other apps** to **Auto-grant all
	permissions**, and ensure no per-app permission strategy overrides it for
	`org.traccar.client`. Headwind's installed-app permission routine explicitly
	grants declared runtime permissions, including background location. Verify
	the actual grants on a pilot phone with your installed agent version:

	```text
	android.permission.ACCESS_COARSE_LOCATION
	android.permission.ACCESS_FINE_LOCATION
	android.permission.ACCESS_BACKGROUND_LOCATION
	android.permission.ACTIVITY_RECOGNITION
	android.permission.POST_NOTIFICATIONS
	```

	Grant coarse/fine location before background location. The alternative is
	for the device-owner agent to delegate
	`DevicePolicyManager.DELEGATION_PERMISSION_GRANT` (`permission-grant`) to
	this package using `setDelegatedScopes`. The client then grants its own
	declared runtime permissions with that delegated authority. The Headwind
	AAR does not provide a method to establish this delegation: it must be
	configured by the device-owner agent. Neither path requires driver prompts.
3. Enable system location, allow background mobile data, and exempt this
	package from battery optimizations/OEM task killers. For recovery from a
	background retry job, the battery-optimization exemption is particularly
	important: the client's use of Headwind does not itself give the client a
	device-owner foreground-service exemption. Configure these settings during
	provisioning using the MDM/OEM capabilities available for your fleet.
4. Enable Headwind application-log collection at INFO level for
	`RapidBusTracker`. Logs include the exact device ID, endpoint, missing
	permissions, and startup failures. `Tracking enabled` confirms startup was
	requested; verify received positions on the server to confirm connectivity.
5. Have Headwind launch the app once after a fresh install. No screen is shown.
	**Run after installation** provides this activation; **Run at boot** is also
	compatible with the no-display entry point and the native boot receiver.
	Together with device-owner permission grants, these settings support remote
	fresh installation without a driver opening the app.
	An app in Android's initial stopped state cannot rely on boot broadcasts
	before activation. Upgrades of activated installations use the package
	replacement receiver.
6. Hide the app **only from the Headwind launcher**, leaving the package enabled
	and installed. Do not use Android `setApplicationHidden`, disable/suspend the
	package, or force-stop it. Android package hiding makes it unavailable for
	use. Where supported by your MDM, prevent force-stop/clear-data with
	`setUserControlDisabledPackages` and block uninstall.

The client cannot grant itself device-owner rights, silently change privileged
system settings, remove Android's tracking indicators, or guarantee recovery
after a force-stop. Missing grants leave it waiting and logging rather than
opening a screen. Test the actual Headwind agent and OEM policies on both Android
15 and 16 before fleet rollout.

### Upgrade and Build

Before deploying, map each old random identifier to its Headwind device ID and
change the **Unique Identifier on the existing Traccar device record** to that
exact Headwind ID. This preserves server history and assignments. Ensure IDs
are unique across the fleet. A Headwind ID rename also requires a server update.
The old engine's unsent queue is not imported into the rewritten SDK; allow old
queues to drain where possible before switching builds/endpoints.

Release signing uses the existing local `android/key.properties` and its
`storeFile`, which resolves relative to `android/app` and points to your existing
`upload-keystore.jks`. Neither signing credentials nor the keystore should be
committed. A matching package name and signing certificate, plus a higher version
code than the installed APK, are required for an in-place update.

Use Flutter 3.47.6 (or a compatible newer stable SDK), Android SDK 37.0, and a
supported JDK 17 or newer. The Android build explicitly selects API 37 minor 0
to match the installed `android-37.0` platform. Keep the upstream Gradle 9.3.1
wrapper configuration.
Local version properties have been synchronized with the pubspec version.

```shell
flutter pub get
flutter build apk --release --build-name=10.1.6 --build-number=162
```

The APK is produced at `build/app/outputs/flutter-apk/app-release.apk`.
Check its signing certificate against an APK already installed on the fleet;
the local key's presence alone cannot establish that they match.

Pilot acceptance checks before rolling out:

1. Upgrade a 9.7.3 phone without uninstalling; check the ID and new endpoint in
	Headwind logs and verify received positions on the existing server record.
2. Reboot with another app visible; verify tracking resumes after unlock without
	a Traccar window, permission prompt, or battery-settings page.
3. Clear recents and exercise system process recovery; verify no UI appears and
	reports resume. Do not confuse clearing recents with Android force-stop.
4. Delay Headwind startup or permission provisioning; verify logged waiting,
	eventual retry, and correct identity. Check permission grants with Android's
	package diagnostics, not only the MDM policy selection.
5. Drive with the screen off, lose connectivity, then reconnect; verify GPS
	cadence and buffered uploads. Confirm launcher hiding leaves tracking active.

Transport is HTTP, so GPS data is not encrypted on the public network. Use a
company VPN or an HTTPS endpoint with a certificate valid for the address if
transport confidentiality is required. This build does not disable TLS checks.

The upstream app description below applies to the original Flutter UI, not the
headless Android fleet entry point in this branch.

## Overview

Traccar Client is a GPS tracking app for Android and iOS. It runs in the background and sends location updates to your own server running [Traccar](https://github.com/traccar/traccar), the open-source GPS tracking platform.

- **Real-time Tracking**: See your device’s location on your private server in real time.
- **Open-Source**: 100% free and open-source, with no ads or tracking.
- **Customizable**: Configure update intervals, accuracy, and data usage to fit your needs.
- **Privacy First**: Your location data is sent only to your chosen server—never to third parties.
- **Easy Integration**: Designed to work seamlessly with the Traccar server and many third-party GPS tracking platforms.

Just enter your server address, grant location permissions, and the app will automatically send periodic location reports in the background.

Don't have a Traccar server yet? [Try the live demo](https://www.traccar.org/demo-server/) or see the [installation guides](https://www.traccar.org/install-vps/) to set up your own for free.

| Client App |
|---|
| <img src=".github/screenshot.png" alt="Traccar Client app" width="200"> |

## Build

Standard Flutter project:

```shell
flutter pub get
flutter run
```

## Team

- Anton Tananaev ([anton@traccar.org](mailto:anton@traccar.org))

## License

Apache License, Version 2.0. See [LICENSE.txt](https://github.com/traccar/traccar-client/blob/master/LICENSE.txt) for details.
