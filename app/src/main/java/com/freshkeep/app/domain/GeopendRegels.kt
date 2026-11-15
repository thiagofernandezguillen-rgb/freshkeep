package com.freshkeep.app.domain

import com.freshkeep.app.domain.model.Locatie
import java.time.LocalDate

/**
 * Lokale vuistregels voor hoe lang een product nog goed is na openen.
 * Wordt gebruikt als de AI niet bereikbaar is.
 */
object GeopendHoudbaarheid {

    private val regels: List<Pair<List<String>, Int>> = listOf(
        listOf("vis", "zalm", "garnaal", "scampi", "sushi", "fish") to 1,
        listOf("gehakt", "kip", "vlees", "filet", "worst", "spek", "meat", "chicken") to 2,
        listOf("ham", "salami", "charcuterie", "beleg", "kalkoen") to 4,
        listOf("melk", "milk", "lait", "room", "cream") to 4,
        listOf("yoghurt", "yogurt", "skyr", "kwark", "pudding", "dessert") to 4,
        listOf("hummus", "salade", "spread", "tapenade", "soep", "soup") to 4,
        listOf("sap", "juice", "jus", "smoothie") to 5,
        listOf("mozzarella", "verse kaas", "ricotta", "mascarpone", "feta") to 4,
        listOf("pesto", "tomatensaus", "pastasaus", "passata") to 5,
        listOf("kaas", "cheese", "fromage", "gouda", "brie", "parmezaan") to 14,
        listOf("boter", "butter", "margarine") to 30,
        listOf("ketchup", "mosterd", "mayonaise", "saus", "sauce", "dressing") to 30,
        listOf("confituur", "jam", "choco", "honing") to 60,
    )

    /** Aantal dagen houdbaar na openen. */
    fun dagenNaOpenen(naam: String, categorie: String?, locatie: Locatie): Int {
        if (locatie == Locatie.DIEPVRIES) return 90
        val tekst = "${naam.lowercase()} ${categorie.orEmpty().lowercase()}"
        return regels.firstOrNull { (woorden, _) -> woorden.any { tekst.contains(it) } }?.second
            ?: if (locatie == Locatie.KAST) 30 else 4
    }

    /** De voorgestelde nieuwe vervaldatum: nooit later dan de oorspronkelijke datum. */
    fun voorgesteldeDatum(geopendOp: LocalDate, dagen: Int, huidigeVervaldatum: LocalDate?): LocalDate {
        val voorstel = geopendOp.plusDays(dagen.toLong())
        return if (huidigeVervaldatum != null && huidigeVervaldatum.isBefore(voorstel)) huidigeVervaldatum else voorstel
    }
}
