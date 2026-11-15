package com.freshkeep.app.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.freshkeep.app.data.repository.VoorraadRepository
import com.freshkeep.app.data.settings.InstellingenRepository
import com.freshkeep.app.domain.MeldingTekst
import com.freshkeep.app.domain.Vervalrekenaar
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate

/**
 * Dagelijkse controle van de voorraad.
 * - [SOORT_SAMENVATTING]: één overzicht op het ingestelde uur (producten binnen X dagen).
 * - [SOORT_VANDAAG]: 's ochtends een extra melding voor producten die vandaag vervallen.
 * Na afloop plant de worker zichzelf opnieuw in voor de volgende dag.
 */
@HiltWorker
class ControleWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val voorraad: VoorraadRepository,
    private val instellingen: InstellingenRepository,
    private val meldingen: MeldingHelper,
    private val planner: MeldingPlanner,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val soort = inputData.getString(SOORT) ?: SOORT_SAMENVATTING
        val vandaag = LocalDate.now()
        try {
            val items = voorraad.alle()
            when (soort) {
                SOORT_VANDAAG -> {
                    val namen = items.filter { it.vervaldatum == vandaag }.map { it.naam }
                    MeldingTekst.vandaag(namen)?.let(meldingen::toonVandaag)
                }
                else -> {
                    val dagenVooraf = instellingen.huidige().dagenVooraf
                    val binnenkort = items
                        .map { it.naam to Vervalrekenaar.dagenTot(it.vervaldatum, vandaag) }
                        .filter { (_, dagen) -> dagen in 1..dagenVooraf.toLong() }
                    MeldingTekst.samenvatting(binnenkort, dagenVooraf)?.let(meldingen::toonSamenvatting)
                }
            }
        } finally {
            if (inputData.getBoolean(HERPLANNEN, true)) planner.planVolgende(soort)
        }
        return Result.success()
    }

    companion object {
        const val SOORT = "soort"
        const val HERPLANNEN = "herplannen"
        const val SOORT_SAMENVATTING = "samenvatting"
        const val SOORT_VANDAAG = "vandaag"
    }
}
