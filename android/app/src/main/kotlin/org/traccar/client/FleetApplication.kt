package org.traccar.client

import android.app.Application

class FleetApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FleetTracking.initialize(this)
    }
}