package org.traccar.client

import android.app.job.JobParameters
import android.app.job.JobService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FleetRetryJob : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var attempt: Job? = null

    override fun onStartJob(parameters: JobParameters): Boolean {
        attempt = scope.launch {
            val started = FleetTracking.start().await()
            jobFinished(parameters, !started)
        }
        return true
    }

    override fun onStopJob(parameters: JobParameters): Boolean {
        attempt?.cancel()
        return true
    }
}