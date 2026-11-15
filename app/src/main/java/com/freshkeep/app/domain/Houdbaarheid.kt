package com.freshkeep.app.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Kleurcode van een product volgens het aantal resterende dagen. */
enum class Houdbaarheid {
    /** Groen: meer dan 7 dagen. */
    GOED,

    /** Geel: binnen 7 dagen. */
    BINNENKORT,

    /** Oranje: binnen 2 dagen. */
    DRINGEND,

    /** Rood: vandaag of verlopen. */
    VERLOPEN,
}

/** Pure functies rond vervaldatums; makkelijk te unit-testen. */
object Vervalrekenaar {

    val NEDERLANDS: Locale = Locale.forLanguageTag("nl-BE")
    private val langeDatum = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", NEDERLANDS)
    private val korteDatum = DateTimeFormatter.ofPattern("d MMM yyyy", NEDERLANDS)

    fun dagenTot(vervaldatum: LocalDate, vandaag: LocalDate = LocalDate.now()): Long =
        ChronoUnit.DAYS.between(vandaag, vervaldatum)

    fun status(dagen: Long): Houdbaarheid = when {
        dagen <= 0 -> Houdbaarheid.VERLOPEN
        dagen <= 2 -> Houdbaarheid.DRINGEND
        dagen <= 7 -> Houdbaarheid.BINNENKORT
        else -> Houdbaarheid.GOED
    }

    /** Tekst in de lijst: "nog 5 dagen", "vervalt vandaag", "3 dagen verlopen". */
    fun label(dagen: Long): String = when {
        dagen > 1 -> "nog $dagen dagen"
        dagen == 1L -> "nog 1 dag"
        dagen == 0L -> "vervalt vandaag"
        dagen == -1L -> "1 dag verlopen"
        else -> "${-dagen} dagen verlopen"
    }

    /** Korte tekst voor in meldingen: "morgen", "2 dagen". */
    fun kortLabel(dagen: Long): String = when (dagen) {
        0L -> "vandaag"
        1L -> "morgen"
        else -> "$dagen dagen"
    }

    fun formatLang(datum: LocalDate): String =
        langeDatum.format(datum).replaceFirstChar { it.titlecase(NEDERLANDS) }

    fun formatKort(datum: LocalDate): String = korteDatum.format(datum)
}
