package com.freshkeep.app.notifications

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.freshkeep.app.data.settings.InstellingenRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plant de dagelijkse controles in met WorkManager. WorkManager bewaart geplande taken
 * zelf over een herstart heen; [BootReceiver] roept [planAlles] voor de zekerheid nog eens aan.
 */
@Singleton
class MeldingPlanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val instellingen: InstellingenRepository,
) {
    private val workManager get() = WorkManager.getInstance(context)

    /** @param vervang true na een gewijzigde tijd; false om een bestaande planning te behouden. */
    suspend fun planAlles(vervang: Boolean) {
        val s = instellingen.huidige()
        val beleid = if (vervang) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
        plan(ControleWorker.SOORT_SAMENVATTING, LocalTime.of(s.meldingUur, s.meldingMinuut), beleid)
        plan(ControleWorker.SOORT_VANDAAG, OCHTEND, beleid)
    }

    /** Wordt aangeroepen door de worker zelf, na het uitvoeren. */
    suspend fun planVolgende(soort: String) {
        val tijd = if (soort == ControleWorker.SOORT_VANDAAG) {
            OCHTEND
        } else {
            val s = instellingen.huidige()
            LocalTime.of(s.meldingUur, s.meldingMinuut)
        }
        // APPEND_OR_REPLACE: de nieuwe taak start pas nadat de huidige (lopende) taak klaar is.
        plan(soort, tijd, ExistingWorkPolicy.APPEND_OR_REPLACE)
    }

    /** Stuurt meteen een samenvatting, zonder de planning te wijzigen (knop in Instellingen). */
    fun stuurNu() {
        val verzoek = OneTimeWorkRequestBuilder<ControleWorker>()
            .setInputData(workDataOf(ControleWorker.SOORT to ControleWorker.SOORT_SAMENVATTING, ControleWorker.HERPLANNEN to false))
            .build()
        workManager.enqueue(verzoek)
    }

    private fun plan(soort: String, tijd: LocalTime, beleid: ExistingWorkPolicy) {
        val verzoek = OneTimeWorkRequestBuilder<ControleWorker>()
            .setInitialDelay(vertragingTot(tijd, LocalDateTime.now()).toMillis(), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(ControleWorker.SOORT to soort))
            .addTag(TAG)
            .build()
        workManager.enqueueUniqueWork("controle_$soort", beleid, verzoek)
    }

    companion object {
        private const val TAG = "dagelijkse_controle"
        private val OCHTEND: LocalTime = LocalTime.of(9, 0)

        /** Tijd tot het volgende moment [tijd]; vandaag als dat nog komt, anders morgen. */
        fun vertragingTot(tijd: LocalTime, nu: LocalDateTime): Duration {
            var volgende = nu.toLocalDate().atTime(tijd)
            if (!volgende.isAfter(nu.plusMinutes(1))) volgende = volgende.plusDays(1)
            return Duration.between(nu, volgende)
        }
    }
}
