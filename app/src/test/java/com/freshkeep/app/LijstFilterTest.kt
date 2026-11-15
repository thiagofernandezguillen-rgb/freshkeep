package com.freshkeep.app

import com.freshkeep.app.data.local.VoorraadItem
import com.freshkeep.app.data.local.VoorraadWeergave
import com.freshkeep.app.domain.model.DatumType
import com.freshkeep.app.domain.model.Locatie
import com.freshkeep.app.ui.home.LijstFilter
import com.freshkeep.app.ui.home.LijstItem
import com.freshkeep.app.ui.home.Tab
import com.freshkeep.app.ui.scan.isGeldigeBarcode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class LijstFilterTest {

    private val vandaag = LocalDate.of(2026, 11, 14)

    private fun rij(id: Long, naam: String, dagen: Long, locatie: Locatie = Locatie.KOELKAST, merk: String? = null) = LijstItem(
        VoorraadWeergave(
            VoorraadItem(
                id = id, barcode = null, naam = naam, merk = merk, vervaldatum = vandaag.plusDays(dagen),
                type = DatumType.ONBEKEND, locatie = locatie, aantal = 1, geopend = false, geopendOp = null,
                notitie = null, toegevoegdOp = Instant.EPOCH,
            ),
            fotoUrl = null,
            categorie = null,
        ),
        dagen,
    )

    private val lijst = listOf(
        rij(1, "Kaas", 10),
        rij(2, "Melk", 1),
        rij(3, "Yoghurt", -2),
        rij(4, "Erwten", 0, Locatie.DIEPVRIES, merk = "Iglo"),
        rij(5, "Pasta", 7, Locatie.KAST),
    )

    @Test
    fun `tabs filteren correct`() {
        assertEquals(listOf("Yoghurt", "Erwten", "Melk", "Pasta", "Kaas"), LijstFilter.filter(lijst, Tab.ALLES, null, "", 7).map { it.item.naam })
        assertEquals(listOf("Erwten", "Melk", "Pasta"), LijstFilter.filter(lijst, Tab.BINNENKORT, null, "", 7).map { it.item.naam })
        assertEquals(listOf("Yoghurt"), LijstFilter.filter(lijst, Tab.VERLOPEN, null, "", 7).map { it.item.naam })
        assertEquals(listOf("Erwten", "Melk"), LijstFilter.filter(lijst, Tab.BINNENKORT, null, "", 3).map { it.item.naam })
    }

    @Test
    fun `locatie en zoekterm`() {
        assertEquals(listOf("Pasta"), LijstFilter.filter(lijst, Tab.ALLES, Locatie.KAST, "", 7).map { it.item.naam })
        assertEquals(listOf("Erwten"), LijstFilter.filter(lijst, Tab.ALLES, null, "iglo", 7).map { it.item.naam })
        assertEquals(listOf("Melk"), LijstFilter.filter(lijst, Tab.ALLES, null, " MEL ", 7).map { it.item.naam })
    }

    @Test
    fun `tellers per tab`() {
        val tellers = LijstFilter.telPerTab(lijst, 7)
        assertEquals(5, tellers[Tab.ALLES])
        assertEquals(3, tellers[Tab.BINNENKORT])
        assertEquals(1, tellers[Tab.VERLOPEN])
    }

    @Test
    fun `barcode-invoer`() {
        assertTrue(isGeldigeBarcode("5410000000000"))
        assertTrue(isGeldigeBarcode("96385074"))
        assertFalse(isGeldigeBarcode("12ab"))
        assertFalse(isGeldigeBarcode("123"))
    }
}
