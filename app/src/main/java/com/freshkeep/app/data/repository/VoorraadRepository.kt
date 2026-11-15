package com.freshkeep.app.data.repository

import com.freshkeep.app.data.local.Geschiedenis
import com.freshkeep.app.data.local.GeschiedenisDao
import com.freshkeep.app.data.local.TransactieUitvoerder
import com.freshkeep.app.data.local.VoorraadDao
import com.freshkeep.app.data.local.VoorraadItem
import com.freshkeep.app.data.local.VoorraadWeergave
import com.freshkeep.app.domain.model.Resultaat
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Alles wat nodig is om een actie (Op/Weggegooid) ongedaan te maken. */
data class UndoActie(
    val origineel: VoorraadItem,
    val geschiedenisId: Long,
    val resultaat: Resultaat,
    /** True als het item nog in de lijst staat (aantal is enkel verlaagd). */
    val itemBlijft: Boolean,
)

@Singleton
class VoorraadRepository @Inject constructor(
    private val voorraadDao: VoorraadDao,
    private val geschiedenisDao: GeschiedenisDao,
    private val transacties: TransactieUitvoerder,
) {
    val items: Flow<List<VoorraadWeergave>> = voorraadDao.observeAlle()

    suspend fun get(id: Long): VoorraadItem? = voorraadDao.get(id)

    suspend fun alle(): List<VoorraadItem> = voorraadDao.alle()

    suspend fun voegToe(item: VoorraadItem): Long = voorraadDao.insert(item)

    suspend fun bewaar(item: VoorraadItem) = voorraadDao.update(item)

    /**
     * Product is (deels) opgebruikt: telt als gered.
     * Bij een aantal groter dan 1 gaat er één af en blijft het item staan.
     */
    suspend fun markeerOp(item: VoorraadItem, vandaag: LocalDate = LocalDate.now()): UndoActie =
        transacties.uitvoeren {
            val blijft = item.aantal > 1
            if (blijft) voorraadDao.update(item.copy(aantal = item.aantal - 1)) else voorraadDao.delete(item)
            val id = geschiedenisDao.insert(
                Geschiedenis(productnaam = item.naam, resultaat = Resultaat.OP, datum = vandaag, aantal = 1),
            )
            UndoActie(item, id, Resultaat.OP, blijft)
        }

    /** Product is weggegooid: verdwijnt helemaal en telt (met het volledige aantal) als verspild. */
    suspend fun markeerWeggegooid(item: VoorraadItem, vandaag: LocalDate = LocalDate.now()): UndoActie =
        transacties.uitvoeren {
            voorraadDao.delete(item)
            val id = geschiedenisDao.insert(
                Geschiedenis(
                    productnaam = item.naam,
                    resultaat = Resultaat.WEGGEGOOID,
                    datum = vandaag,
                    aantal = item.aantal.coerceAtLeast(1),
                ),
            )
            UndoActie(item, id, Resultaat.WEGGEGOOID, itemBlijft = false)
        }

    /** Zet het item terug zoals het was en verwijdert de geschiedenisregel. */
    suspend fun ongedaanMaken(actie: UndoActie) = transacties.uitvoeren {
        voorraadDao.upsert(actie.origineel)
        geschiedenisDao.delete(actie.geschiedenisId)
    }
}
