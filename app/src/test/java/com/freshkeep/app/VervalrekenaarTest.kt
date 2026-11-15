package com.freshkeep.app

import com.freshkeep.app.domain.Houdbaarheid
import com.freshkeep.app.domain.Vervalrekenaar
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class VervalrekenaarTest {

    private val vandaag = LocalDate.of(2026, 11, 14)

    @Test
    fun `dagen tot vervaldatum`() {
        assertEquals(0, Vervalrekenaar.dagenTot(vandaag, vandaag))
        assertEquals(1, Vervalrekenaar.dagenTot(vandaag.plusDays(1), vandaag))
        assertEquals(-3, Vervalrekenaar.dagenTot(vandaag.minusDays(3), vandaag))
        assertEquals(17, Vervalrekenaar.dagenTot(LocalDate.of(2026, 12, 1), vandaag))
    }

    @Test
    fun `kleurcodes volgen de grenzen 7 en 2 dagen`() {
        assertEquals(Houdbaarheid.GOED, Vervalrekenaar.status(8))
        assertEquals(Houdbaarheid.BINNENKORT, Vervalrekenaar.status(7))
        assertEquals(Houdbaarheid.BINNENKORT, Vervalrekenaar.status(3))
        assertEquals(Houdbaarheid.DRINGEND, Vervalrekenaar.status(2))
        assertEquals(Houdbaarheid.DRINGEND, Vervalrekenaar.status(1))
        assertEquals(Houdbaarheid.VERLOPEN, Vervalrekenaar.status(0))
        assertEquals(Houdbaarheid.VERLOPEN, Vervalrekenaar.status(-5))
    }

    @Test
    fun `labels in het Nederlands`() {
        assertEquals("nog 5 dagen", Vervalrekenaar.label(5))
        assertEquals("nog 1 dag", Vervalrekenaar.label(1))
        assertEquals("vervalt vandaag", Vervalrekenaar.label(0))
        assertEquals("1 dag verlopen", Vervalrekenaar.label(-1))
        assertEquals("4 dagen verlopen", Vervalrekenaar.label(-4))
    }

    @Test
    fun `korte labels voor meldingen`() {
        assertEquals("morgen", Vervalrekenaar.kortLabel(1))
        assertEquals("2 dagen", Vervalrekenaar.kortLabel(2))
        assertEquals("vandaag", Vervalrekenaar.kortLabel(0))
    }

    @Test
    fun `lange datum met hoofdletter`() {
        assertEquals("Zaterdag 14 november 2026", Vervalrekenaar.formatLang(vandaag))
    }
}
