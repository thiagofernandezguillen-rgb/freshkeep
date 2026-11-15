package com.freshkeep.app.data.repository

import com.freshkeep.app.data.local.GeschiedenisDao
import com.freshkeep.app.data.local.ProductTelling
import com.freshkeep.app.domain.Gebeurtenis
import com.freshkeep.app.domain.MaandStat
import com.freshkeep.app.domain.Statistiek
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatistiekRepository @Inject constructor(
    private val dao: GeschiedenisDao,
) {
    fun perMaand(aantalMaanden: Int = 6, vandaag: LocalDate = LocalDate.now()): Flow<List<MaandStat>> {
        val vanaf = vandaag.withDayOfMonth(1).minusMonths((aantalMaanden - 1).toLong())
        return dao.observeVanaf(vanaf).map { lijst ->
            Statistiek.perMaand(lijst.map { Gebeurtenis(it.resultaat, it.datum, it.aantal) }, vandaag, aantalMaanden)
        }
    }

    fun topWeggegooid(): Flow<List<ProductTelling>> = dao.observeTopWeggegooid()
}
