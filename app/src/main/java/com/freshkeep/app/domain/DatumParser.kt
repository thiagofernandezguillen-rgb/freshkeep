package com.freshkeep.app.domain

import com.freshkeep.app.domain.model.DatumType
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/** Resultaat van het ontleden van een datumtekst. */
data class GeparseerdeDatum(
    val datum: LocalDate,
    val type: DatumType,
    /** True als enkel maand en jaar vermeld stonden (datum = laatste dag van de maand). */
    val alleenMaand: Boolean,
)

/**
 * Begrijpt de gangbare notaties op verpakkingen (NL, EN, FR, DE), zoals
 * "THT 15/11/26", "15.11.2026", "11-2026", "NOV 2026", "best before 2026-11-15",
 * "à consommer jusqu'au 3 déc. 2026". Wordt gebruikt om het antwoord van de AI te
 * normaliseren wanneer die geen nette ISO-datum teruggeeft.
 * Bij een dubbelzinnige numerieke datum geldt de Europese volgorde (dag-maand-jaar).
 */
object VervaldatumParser {

    private const val VOOR = """(?<![\p{L}\d])"""
    private const val NA = """(?![\p{L}\d])"""

    private val iso = Regex("""$VOOR(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})$NA""")
    private val dagMaandJaar = Regex("""$VOOR(\d{1,2})\s*[-/. ]\s*(\d{1,2})\s*[-/. ]\s*(\d{4}|\d{2})$NA""")
    private val dagMaandnaamJaar = Regex("""$VOOR(\d{1,2})\s*[-/. ]?\s*(\p{L}{3,10})\.?\s*[-/. ]?\s*(\d{4}|\d{2})$NA""")
    private val maandnaamJaar = Regex("""$VOOR(\p{L}{3,10})\.?\s*[-/. ]?\s*(\d{4}|\d{2})$NA""")
    private val maandJaar = Regex("""$VOOR(\d{1,2})\s*[-/.]\s*(\d{4}|\d{2})$NA""")

    private val maanden: Map<String, Int> = buildMap {
        fun zet(nummer: Int, vararg namen: String) = namen.forEach { put(it, nummer) }
        zet(1, "januari", "january", "janvier", "januar", "jan", "janv")
        zet(2, "februari", "february", "fevrier", "février", "februar", "feb", "febr", "fev", "fév", "fevr", "févr")
        zet(3, "maart", "march", "mars", "märz", "maerz", "mrt", "mar", "mär", "mrz")
        zet(4, "april", "avril", "apr", "avr")
        zet(5, "mei", "may", "mai")
        zet(6, "juni", "june", "juin", "jun")
        zet(7, "juli", "july", "juillet", "jul", "juil")
        zet(8, "augustus", "august", "aout", "août", "aug", "aou", "aoû")
        zet(9, "september", "septembre", "sep", "sept")
        zet(10, "oktober", "october", "octobre", "okt", "oct")
        zet(11, "november", "novembre", "nov")
        zet(12, "december", "décembre", "decembre", "dezember", "dec", "déc", "dez")
    }

    fun parse(tekst: String?): GeparseerdeDatum? {
        if (tekst.isNullOrBlank()) return null
        val norm = tekst.lowercase(Locale.ROOT).replace('’', '\'').replace(',', ' ')
        val type = detecteerType(norm)

        iso.findAll(norm).forEach { m ->
            val (j, mnd, d) = m.destructured
            maakDatum(j.toInt(), mnd.toInt(), d.toInt())?.let { return GeparseerdeDatum(it, type, false) }
        }
        dagMaandJaar.findAll(norm).forEach { m ->
            val (d, mnd, j) = m.destructured
            maakDatum(jaar(j), mnd.toInt(), d.toInt())?.let { return GeparseerdeDatum(it, type, false) }
        }
        dagMaandnaamJaar.findAll(norm).forEach { m ->
            val (d, naam, j) = m.destructured
            val maand = maandNummer(naam) ?: return@forEach
            maakDatum(jaar(j), maand, d.toInt())?.let { return GeparseerdeDatum(it, type, false) }
        }
        maandnaamJaar.findAll(norm).forEach { m ->
            val (naam, j) = m.destructured
            val maand = maandNummer(naam) ?: return@forEach
            laatsteDag(jaar(j), maand)?.let { return GeparseerdeDatum(it, type, true) }
        }
        maandJaar.findAll(norm).forEach { m ->
            val (mnd, j) = m.destructured
            laatsteDag(jaar(j), mnd.toInt())?.let { return GeparseerdeDatum(it, type, true) }
        }
        return null
    }

    /** Bepaalt THT of TGT op basis van de tekst rond de datum. */
    fun detecteerType(tekst: String): DatumType {
        val norm = tekst.lowercase(Locale.ROOT)
        val tht = listOf(
            "tht", "ten minste houdbaar", "tenminste houdbaar", "best before", "best by", "bbe",
            "de préférence", "de preference", "ddm", "dluo", "mindestens haltbar", "mhd",
        )
        val tgt = listOf(
            "tgt", "te gebruiken tot", "use by", "consommer jusqu", "dlc", "verbrauchen bis", "zu verbrauchen",
        )
        return when {
            tht.any { norm.contains(it) } -> DatumType.THT
            tgt.any { norm.contains(it) } -> DatumType.TGT
            else -> DatumType.ONBEKEND
        }
    }

    /** Exacte maandnaam of afkorting, of een afgekapte volledige naam zoals "novem". */
    private fun maandNummer(naam: String): Int? = maanden[naam]
        ?: maanden.entries.firstOrNull { (key, _) -> naam.length >= 3 && key.length >= 4 && key.startsWith(naam) }?.value

    private fun jaar(tekst: String): Int = tekst.toInt().let { if (tekst.length == 2) 2000 + it else it }

    private fun maakDatum(jaar: Int, maand: Int, dag: Int): LocalDate? =
        if (jaar !in 2000..2100) null else runCatching { LocalDate.of(jaar, maand, dag) }.getOrNull()

    private fun laatsteDag(jaar: Int, maand: Int): LocalDate? =
        if (maand in 1..12 && jaar in 2000..2100) YearMonth.of(jaar, maand).atEndOfMonth() else null
}
