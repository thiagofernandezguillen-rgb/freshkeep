package com.freshkeep.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Na een herstart of tijdswijziging: dagelijkse controles (opnieuw) inplannen. */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var planner: MeldingPlanner

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        // Bij een tijdswijziging klopt de bestaande vertraging niet meer: vervangen.
        val vervang = intent.action == Intent.ACTION_TIME_CHANGED || intent.action == Intent.ACTION_TIMEZONE_CHANGED
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                planner.planAlles(vervang = vervang)
            } finally {
                pending.finish()
            }
        }
    }
}
