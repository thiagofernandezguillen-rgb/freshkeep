package com.freshkeep.app

import com.freshkeep.app.data.local.Geschiedenis
import com.freshkeep.app.data.local.GeschiedenisDao
import com.freshkeep.app.data.local.ProductTelling
import com.freshkeep.app.data.local.TransactieUitvoerder
import com.freshkeep.app.data.local.VoorraadDao
import com.freshkeep.app.data.local.VoorraadItem
import com.freshkeep.app.data.local.VoorraadWeergave
import com.freshkeep.app.data.remote.DatumAntwoord
import com.freshkeep.app.data.repository.AiRepository
import com.freshkeep.app.data.repository.BackupMapper
import com.freshkeep.app.data.repository.VoorraadRepository
import com.freshkeep.app.domain.model.DatumType
import com.freshkeep.app.domain.model.Locatie
import com.freshkeep.app.domain.model.Resultaat
import com.freshkeep.app.data.local.Product
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** Eenvoudige in-memory DAO's om de repositorylogica zonder Room te testen. */
private class NepVoorraadDao : VoorraadDao {
    val items = MutableStateFlow<Map<Long, VoorraadItem>>(emptyMap())
    private var volgendId = 1L

    override fun observeAlle(): Flow<List<VoorraadWeergave>> =
        items.map { m -> m.values.sortedBy { it.vervaldatum }.map { VoorraadWeergave(it, null, null) } }
    override suspend fun get(id: Long) = items.value[id]
    override suspend fun alle() = items.value.values.toList()
    override suspend fun tussen(van: LocalDate, tot: LocalDate) = alle().filter { it.vervaldatum in van..tot }
    override suspend fun insert(item: VoorraadItem): Long {
        val id = if (item.id == 0L) volgendId++ else item.id
        items.value += id to item.copy(id = id)
        return id
    }
    override suspend fun update(item: VoorraadItem) { items.value += item.id to item }
    override suspend fun upsert(item: VoorraadItem) { items.value += item.id to item }
    override suspend fun delete(item: VoorraadItem) { items.value -= item.id }
    override suspend fun insertAll(items: List<VoorraadItem>) = items.forEach { insert(it) }
    override suspend fun deleteAll() { items.value = emptyMap() }
}

private class NepGeschiedenisDao : GeschiedenisDao {
    val rijen = mutableMapOf<Long, Geschiedenis>()
    private var volgendId = 1L
    override suspend fun insert(item: Geschiedenis): Long = volgendId++.also { rijen[it] = item.copy(id = it) }
    override suspend fun delete(id: Long) { rijen.remove(id) }
    override fun observeVanaf(vanaf: LocalDate): Flow<List<Geschiedenis>> = MutableStateFlow(rijen.values.toList())
    override fun observeTopWeggegooid(): Flow<List<ProductTelling>> = MutableStateFlow(emptyList())
    override suspend fun alle() = rijen.values.toList()
    override suspend fun insertAll(items: List<Geschiedenis>) = items.forEach { rijen[it.id] = it }
    override suspend fun deleteAll() = rijen.clear()
}

private object DirecteTransacties : TransactieUitvoerder {
    override suspend fun <T> uitvoeren(blok: suspend () -> T): T = blok()
}

class RepositoryTest {

    private val vandaag = LocalDate.of(2026, 11, 14)

    private fun item(aantal: Int = 1) = VoorraadItem(
        barcode = "5410000000001", naam = "Melk", merk = "Inza", vervaldatum = vandaag.plusDays(3),
        type = DatumType.THT, locatie = Locatie.KOELKAST, aantal = aantal, geopend = false, geopendOp = null,
        notitie = null, toegevoegdOp = Instant.EPOCH,
    )

    @Test
    fun `op met aantal 1 verwijdert het item en telt als gered`() = runTest {
        val voorraad = NepVoorraadDao(); val geschiedenis = NepGeschiedenisDao()
        val repo = VoorraadRepository(voorraad, geschiedenis, DirecteTransacties)
        val id = repo.voegToe(item())

        val actie = repo.markeerOp(repo.get(id)!!, vandaag)

        assertNull(repo.get(id))
        assertFalse(actie.itemBlijft)
        assertEquals(Resultaat.OP, geschiedenis.rijen.values.single().resultaat)
    }

    @Test
    fun `op met aantal 3 trekt er een af`() = runTest {
        val voorraad = NepVoorraadDao(); val geschiedenis = NepGeschiedenisDao()
        val repo = VoorraadRepository(voorraad, geschiedenis, DirecteTransacties)
        val id = repo.voegToe(item(aantal = 3))

        val actie = repo.markeerOp(repo.get(id)!!, vandaag)

        assertEquals(2, repo.get(id)!!.aantal)
        assertTrue(actie.itemBlijft)
        assertEquals(1, geschiedenis.rijen.values.single().aantal)
    }

    @Test
    fun `weggegooid telt het volledige aantal als verspild`() = runTest {
        val voorraad = NepVoorraadDao(); val geschiedenis = NepGeschiedenisDao()
        val repo = VoorraadRepository(voorraad, geschiedenis, DirecteTransacties)
        val id = repo.voegToe(item(aantal = 2))

        repo.markeerWeggegooid(repo.get(id)!!, vandaag)

        assertNull(repo.get(id))
        val rij = geschiedenis.rijen.values.single()
        assertEquals(Resultaat.WEGGEGOOID, rij.resultaat)
        assertEquals(2, rij.aantal)
    }

    @Test
    fun `ongedaan maken zet alles terug`() = runTest {
        val voorraad = NepVoorraadDao(); val geschiedenis = NepGeschiedenisDao()
        val repo = VoorraadRepository(voorraad, geschiedenis, DirecteTransacties)
        val id = repo.voegToe(item(aantal = 2))
        val origineel = repo.get(id)!!

        repo.ongedaanMaken(repo.markeerOp(origineel, vandaag))
        assertEquals(origineel, repo.get(id))
        assertTrue(geschiedenis.rijen.isEmpty())

        repo.ongedaanMaken(repo.markeerWeggegooid(origineel, vandaag))
        assertEquals(origineel, repo.get(id))
        assertTrue(geschiedenis.rijen.isEmpty())
    }

    @Test
    fun `AI-antwoord wordt genormaliseerd`() {
        val iso = AiRepository.verwerkDatum(DatumAntwoord("2026-11-15", "THT", 0.93))
        assertEquals(LocalDate.of(2026, 11, 15), iso.datum)
        assertEquals(DatumType.THT, iso.type)
        assertEquals(0.93, iso.zekerheid, 0.0001)

        val maand = AiRepository.verwerkDatum(DatumAntwoord("11/2026", "onbekend", 0.7))
        assertEquals(LocalDate.of(2026, 11, 30), maand.datum)

        val geen = AiRepository.verwerkDatum(DatumAntwoord(null, "onbekend", 0.9))
        assertNull(geen.datum)
        assertEquals(0.0, geen.zekerheid, 0.0)

        val teHoog = AiRepository.verwerkDatum(DatumAntwoord("2026-11-15", "TGT", 7.0))
        assertEquals(1.0, teHoog.zekerheid, 0.0)
        assertEquals(DatumType.TGT, teHoog.type)
    }

    @Test
    fun `back-up heen en terug geeft dezelfde gegevens`() {
        val producten = listOf(Product("123", "Melk", "Inza", "1 L", "https://x/y.jpg", "Melk"))
        val voorraad = listOf(item(aantal = 2).copy(id = 7, geopend = true, geopendOp = vandaag, notitie = "voor soep"))
        val geschiedenis = listOf(Geschiedenis(3, "Kaas", Resultaat.WEGGEGOOID, vandaag, 1))

        val json = BackupMapper.naarJson(producten, voorraad, geschiedenis, Instant.EPOCH)
        val (p, v, g) = BackupMapper.vanJson(json)

        assertEquals(producten, p)
        assertEquals(voorraad, v)
        assertEquals(geschiedenis, g)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `ongeldige back-up geeft een duidelijke fout`() {
        BackupMapper.vanJson("{\"niet\": \"geldig\"}")
    }
}
