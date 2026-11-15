package com.freshkeep.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.freshkeep.app.notifications.MeldingHelper
import com.freshkeep.app.notifications.MeldingPlanner
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class FreshKeepApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var meldingen: MeldingHelper
    @Inject lateinit var planner: MeldingPlanner

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        meldingen.maakKanalen()
        // Bestaande planning behouden; alleen inplannen als er nog niets gepland staat.
        appScope.launch { planner.planAlles(vervang = false) }
    }
}
