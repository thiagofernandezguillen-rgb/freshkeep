package com.freshkeep.app

import com.freshkeep.app.domain.VervaldatumParser
import com.freshkeep.app.domain.model.DatumType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class VervaldatumParserTest {

    private fun datum(tekst: String?) = VervaldatumParser.parse(tekst)?.datum

    @Test
    fun `numerieke notaties`() {
        val verwacht = LocalDate.of(2026, 11, 15)
        assertEquals(verwacht, datum("15/11/26"))
        assertEquals(verwacht, datum("15.11.2026"))
        assertEquals(verwacht, datum("15-11-2026"))
        assertEquals(verwacht, datum("15 11 2026"))
        assertEquals(verwacht, datum("2026-11-15"))
        assertEquals(LocalDate.of(2026, 3, 5), datum("5/3/26"))
    }

    @Test
    fun `alleen maand en jaar geeft de laatste dag van de maand`() {
        val r = VervaldatumParser.parse("11-2026")!!
        assertEquals(LocalDate.of(2026, 11, 30), r.datum)
        assertTrue(r.alleenMaand)
        assertEquals(LocalDate.of(2026, 2, 28), datum("02/2026"))
        assertEquals(LocalDate.of(2028, 2, 29), datum("02.28"))
    }

    @Test
    fun `maandnamen in meerdere talen`() {
        assertEquals(LocalDate.of(2026, 11, 30), datum("NOV 2026"))
        assertEquals(LocalDate.of(2026, 12, 3), datum("à consommer jusqu'au 3 déc. 2026"))
        assertEquals(LocalDate.of(2027, 3, 31), datum("best before end MAR 2027"))
        assertEquals(LocalDate.of(2027, 5, 12), datum("THT 12 mei 2027"))
        assertEquals(LocalDate.of(2027, 8, 31), datum("août 2027"))
        assertEquals(LocalDate.of(2026, 10, 9), datum("09 OKT 26"))
    }

    @Test
    fun `type THT of TGT wordt herkend`() {
        assertEquals(DatumType.THT, VervaldatumParser.parse("Ten minste houdbaar tot: 15/11/26")!!.type)
        assertEquals(DatumType.THT, VervaldatumParser.parse("Best before 15.11.2026")!!.type)
        assertEquals(DatumType.THT, VervaldatumParser.parse("À consommer de préférence avant le 15/11/2026")!!.type)
        assertEquals(DatumType.TGT, VervaldatumParser.parse("Te gebruiken tot 15/11/26")!!.type)
        assertEquals(DatumType.TGT, VervaldatumParser.parse("USE BY 15 NOV 2026")!!.type)
        assertEquals(DatumType.TGT, VervaldatumParser.parse("à consommer jusqu'au 15.11.26")!!.type)
        assertEquals(DatumType.ONBEKEND, VervaldatumParser.parse("15.11.2026")!!.type)
    }

    @Test
    fun `volledige datum is geen alleen-maand`() {
        assertFalse(VervaldatumParser.parse("15/11/2026")!!.alleenMaand)
    }

    @Test
    fun `ongeldige of lege tekst geeft null`() {
        assertNull(datum(null))
        assertNull(datum(""))
        assertNull(datum("geen datum hier"))
        assertNull(datum("32/13/2026"))
        assertNull(datum("lot 4471"))
    }
}
