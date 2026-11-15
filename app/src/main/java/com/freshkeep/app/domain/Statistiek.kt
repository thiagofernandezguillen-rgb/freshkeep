package com.freshkeep.app.domain

import com.freshkeep.app.domain.model.Resultaat
import java.time.LocalDate
import java.time.YearMonth

/** Aantal geredde en weggegooide producten in één maand. */
data class MaandStat(val maand: YearMonth, val gered: Int, val weggegooid: Int)

/** Eén gebeurtenis uit de geschiedenis, losgekoppeld van Room. */
data class Gebeurtenis(val resultaat: Resultaat, val datum: LocalDate, val aantal: Int)

object Statistiek {

    /** Geeft de laatste [aantalMaanden] maanden terug (oudste eerst), ook als ze leeg zijn. */
    fun perMaand(gebeurtenissen: List<Gebeurtenis>, vandaag: LocalDate, aantalMaanden: Int = 6): List<MaandStat> {
        val huidig = YearMonth.from(vandaag)
        val perMaand = gebeurtenissen.groupBy { YearMonth.from(it.datum) }
        return (aantalMaanden - 1 downTo 0).map { terug ->
            val maand = huidig.minusMonths(terug.toLong())
            val lijst = perMaand[maand].orEmpty()
            MaandStat(
                maand = maand,
                gered = lijst.filter { it.resultaat == Resultaat.OP }.sumOf { it.aantal },
                weggegooid = lijst.filter { it.resultaat == Resultaat.WEGGEGOOID }.sumOf { it.aantal },
            )
        }
    }

    /** Percentage gered (0-100), of null als er nog niets is. */
    fun percentageGered(gered: Int, weggegooid: Int): Int? {
        val totaal = gered + weggegooid
        return if (totaal == 0) null else Math.round(gered * 100f / totaal)
    }
}
