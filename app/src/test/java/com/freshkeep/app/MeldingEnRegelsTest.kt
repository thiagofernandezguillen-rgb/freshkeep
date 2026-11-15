package com.freshkeep.app

import com.freshkeep.app.domain.Gebeurtenis
import com.freshkeep.app.domain.GeopendHoudbaarheid
import com.freshkeep.app.domain.MeldingTekst
import com.freshkeep.app.domain.Statistiek
import com.freshkeep.app.domain.model.Locatie
import com.freshkeep.app.domain.model.Resultaat
import com.freshkeep.app.notifications.MeldingPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

class MeldingEnRegelsTest {

    @Test
    fun `samenvatting zoals in de specificatie`() {
        val tekst = MeldingTekst.samenvatting(listOf("kaas" to 5L, "melk" to 1L, "yoghurt" to 2L), dagenVooraf = 7)!!
        assertEquals("3 producten vervallen deze week: melk (morgen), yoghurt (2 dagen), kaas (5 dagen)", tekst.inhoud)
        assertEquals("3 producten vervallen binnenkort", tekst.titel)
    }

    @Test
    fun `samenvatting enkelvoud en andere periode`() {
        val tekst = MeldingTekst.samenvatting(listOf("melk" to 3L), dagenVooraf = 4)!!
        assertEquals("1 product vervalt binnen 4 dagen: melk (3 dagen)", tekst.inhoud)
        assertNull(MeldingTekst.samenvatting(emptyList(), 7))
    }

    @Test
    fun `samenvatting kort lange lijsten in`() {
        val items = (1..9).map { "p$it" to it.toLong() }
        val tekst = MeldingTekst.samenvatting(items, 14)!!
        assertEquals(true, tekst.inhoud.endsWith(" en 3 andere"))
    }

    @Test
    fun `melding van vandaag`() {
        assertEquals("melk vervalt vandaag. Eet smakelijk!", MeldingTekst.vandaag(listOf("melk"))!!.inhoud)
        assertEquals("2 producten vervallen vandaag", MeldingTekst.vandaag(listOf("a", "b"))!!.titel)
        assertNull(MeldingTekst.vandaag(emptyList()))
    }

    @Test
    fun `houdbaarheid na openen`() {
        assertEquals(4, GeopendHoudbaarheid.dagenNaOpenen("Halfvolle melk", null, Locatie.KOELKAST))
        assertEquals(1, GeopendHoudbaarheid.dagenNaOpenen("Gerookte zalm", null, Locatie.KOELKAST))
        assertEquals(90, GeopendHoudbaarheid.dagenNaOpenen("Erwten", null, Locatie.DIEPVRIES))
        assertEquals(30, GeopendHoudbaarheid.dagenNaOpenen("Rijst", null, Locatie.KAST))
        assertEquals(14, GeopendHoudbaarheid.dagenNaOpenen("Jonge Gouda", "cheeses", Locatie.KOELKAST))
    }

    @Test
    fun `voorstel na openen is nooit later dan de originele datum`() {
        val open = LocalDate.of(2026, 11, 14)
        assertEquals(LocalDate.of(2026, 11, 18), GeopendHoudbaarheid.voorgesteldeDatum(open, 4, LocalDate.of(2026, 12, 1)))
        assertEquals(LocalDate.of(2026, 11, 16), GeopendHoudbaarheid.voorgesteldeDatum(open, 4, LocalDate.of(2026, 11, 16)))
    }

    @Test
    fun `statistiek per maand inclusief lege maanden`() {
        val vandaag = LocalDate.of(2026, 11, 14)
        val lijst = listOf(
            Gebeurtenis(Resultaat.OP, LocalDate.of(2026, 11, 2), 1),
            Gebeurtenis(Resultaat.OP, LocalDate.of(2026, 11, 3), 1),
            Gebeurtenis(Resultaat.WEGGEGOOID, LocalDate.of(2026, 11, 5), 2),
            Gebeurtenis(Resultaat.OP, LocalDate.of(2026, 9, 1), 1),
            Gebeurtenis(Resultaat.OP, LocalDate.of(2025, 1, 1), 9), // te oud
        )
        val maanden = Statistiek.perMaand(lijst, vandaag, 6)
        assertEquals(6, maanden.size)
        assertEquals(YearMonth.of(2026, 6), maanden.first().maand)
        assertEquals(YearMonth.of(2026, 11), maanden.last().maand)
        assertEquals(2, maanden.last().gered)
        assertEquals(2, maanden.last().weggegooid)
        assertEquals(1, maanden[3].gered) // september
        assertEquals(0, maanden[4].gered) // oktober
        assertEquals(50, Statistiek.percentageGered(2, 2))
        assertNull(Statistiek.percentageGered(0, 0))
    }

    @Test
    fun `planner kiest vandaag of morgen`() {
        val ochtend = LocalDateTime.of(2026, 11, 14, 9, 0)
        assertEquals(Duration.ofHours(9), MeldingPlanner.vertragingTot(LocalTime.of(18, 0), ochtend))
        val avond = LocalDateTime.of(2026, 11, 14, 18, 0)
        assertEquals(Duration.ofHours(24), MeldingPlanner.vertragingTot(LocalTime.of(18, 0), avond))
        val laat = LocalDateTime.of(2026, 11, 14, 23, 30)
        assertEquals(Duration.ofMinutes(18 * 60 + 30), MeldingPlanner.vertragingTot(LocalTime.of(18, 0), laat))
    }
}
