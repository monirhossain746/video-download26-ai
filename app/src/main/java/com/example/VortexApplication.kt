package com.example

import android.app.Application

class VortexApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.networkRecoveryMonitor.start()
    }
}
