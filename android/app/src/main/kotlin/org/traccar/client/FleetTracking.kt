package org.traccar.client

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import com.hmdm.HeadwindMDM
import com.hmdm.MDMService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object FleetTracking : HeadwindMDM.EventHandler {
    private const val TAG = "RapidBusTracker"
    private const val RETRY_JOB_ID = 5055
    private const val SERVER_URL = "http://avl.rapidbus-it.com:5055"
    private lateinit var context: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()
    private val headwind = HeadwindMDM.getInstance()
    private var lastStatus: String? = null
    private var attempt: Deferred<Boolean>? = null

    fun initialize(application: Context) {
        context = application.applicationContext
        start()
    }

    fun start(): Deferred<Boolean> {
        attempt?.takeIf { it.isActive }?.let { return it }
        return scope.async(start = CoroutineStart.LAZY) {
            mutex.withLock {
                try {
                    reconcile()
                } catch (exception: Exception) {
                    report("Tracking startup failed: ${exception.javaClass.simpleName}: ${exception.message}")
                    scheduleRetry()
                    false
                }
            }
        }.also {
            attempt = it
            it.start()
        }
    }

    private suspend fun reconcile(): Boolean {
        if (!headwind.isConnected) {
            headwind.connect(context, this)
            var remainingAttempts = 20
            while (!headwind.isConnected && remainingAttempts-- > 0) {
                delay(250)
            }
        }

        val preferences = context.getSharedPreferences("rapidbus_fleet", Context.MODE_PRIVATE)
        val deviceId = if (headwind.isConnected) {
            headwind.deviceId?.takeIf { it.isNotBlank() }
        } else {
            preferences.getString("headwind_device_id", null)
        }
        if (deviceId == null) {
            report("Waiting for Headwind getDeviceId(); no random or legacy identifier will be used")
            scheduleRetry()
            return false
        }
        if (headwind.isConnected) {
            preferences.edit().putString("headwind_device_id", deviceId).apply()
        }

        val required = buildList {
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                add(Manifest.permission.ACTIVITY_RECOGNITION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (required.any { context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }) {
            grantDelegatedPermissions(required)
        }
        val missing = required.filter {
            context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            report("Headwind device ID=$deviceId; grant permissions through MDM: ${missing.joinToString()}")
            scheduleRetry()
            return false
        }

        val powerManager = context.getSystemService(PowerManager::class.java)
        if (!powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
            report("Headwind device ID=$deviceId; configure unrestricted battery usage through MDM")
        }

        val config = Config(
            serverUrl = SERVER_URL,
            deviceId = deviceId,
            location = LocationConfig(
                accuracy = Accuracy.HIGHEST,
                distanceMeters = 50,
                intervalSeconds = 10,
                angleDegrees = 30,
                stopDetection = false,
            ),
            wakeLock = true,
            buffer = true,
            notification = NotificationConfig(text = "Company vehicle location tracking"),
        )
        var tracker = sharedTracker(config)
        if (tracker.config != config) {
            tracker = tracker.updateConfig(config)
        }
        tracker.start()
        try {
            val intent = Intent(context, TrackerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (exception: Exception) {
            tracker.stop()
            throw exception
        }
        report("Tracking enabled; Headwind device ID=$deviceId; server=$SERVER_URL")
        return true
    }

    private suspend fun grantDelegatedPermissions(permissions: List<String>) = withContext(Dispatchers.IO) {
        val policyManager = context.getSystemService(DevicePolicyManager::class.java)
        if (DevicePolicyManager.DELEGATION_PERMISSION_GRANT !in
            policyManager.getDelegatedScopes(null, context.packageName)) return@withContext
        for (permission in permissions) {
            if (context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) continue
            val granted = policyManager.setPermissionGrantState(
                null,
                context.packageName,
                permission,
                DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED,
            )
            if (!granted) {
                MDMService.Log.w(TAG, "MDM delegation could not grant $permission")
            }
        }
    }

    private fun scheduleRetry() {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        if (scheduler.getPendingJob(RETRY_JOB_ID) != null) return
        val result = scheduler.schedule(
            JobInfo.Builder(RETRY_JOB_ID, ComponentName(context, FleetRetryJob::class.java))
                .setMinimumLatency(30_000)
                .setBackoffCriteria(30_000, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .setPersisted(true)
                .build(),
        )
        if (result != JobScheduler.RESULT_SUCCESS) {
            report("Android could not schedule tracking startup retry")
        }
    }

    private fun report(message: String) {
        if (message == lastStatus) return
        lastStatus = message
        MDMService.Log.i(TAG, message)
    }

    override fun onHeadwindMDMConnected() {
        lastStatus = null
        refreshIdentity()
    }

    override fun onHeadwindMDMDisconnected() {
        scope.launch {
            delay(5_000)
            start().await()
        }
    }

    override fun onHeadwindMDMConfigChanged() {
        refreshIdentity()
    }

    private fun refreshIdentity() {
        scope.launch {
            attempt?.await()
            start().await()
        }
    }
}