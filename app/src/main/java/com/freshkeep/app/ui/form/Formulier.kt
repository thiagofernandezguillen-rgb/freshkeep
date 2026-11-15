package com.freshkeep.app.ui.form

import com.freshkeep.app.data.local.VoorraadItem
import com.freshkeep.app.data.repository.AiRepository
import com.freshkeep.app.domain.GeopendHoudbaarheid
import com.freshkeep.app.domain.model.DatumType
import com.freshkeep.app.domain.model.Locatie
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/** Voorstel voor een kortere houdbaarheid nadat een product geopend is. */
data class GeopendAdvies(
    val dagen: Int,
    val datum: LocalDate,
    val doorAi: Boolean,
)

/** Toestand van het formulier dat zowel bij toevoegen als bij aanpassen gebruikt wordt. */
data class ItemFormulier(
    val naam: String = "",
    val merk: String = "",
    val vervaldatum: LocalDate? = null,
    val type: DatumType = DatumType.ONBEKEND,
    /** Zekerheid van de AI (0-1), of null als de gebruiker de datum zelf koos. */
    val zekerheid: Double? = null,
    val locatie: Locatie = Locatie.KOELKAST,
    val aantal: Int = 1,
    val notitie: String = "",
    val geopend: Boolean = false,
    val geopendOp: LocalDate? = null,
    val advies: GeopendAdvies? = null,
    val adviesBezig: Boolean = false,
) {
    val isGeldig: Boolean get() = naam.isNotBlank() && vervaldatum != null && aantal >= 1

    fun naarItem(barcode: String?, id: Long = 0, toegevoegdOp: Instant = Instant.now()): VoorraadItem = VoorraadItem(
        id = id,
        barcode = barcode,
        naam = naam.trim(),
        merk = merk.trim().ifEmpty { null },
        vervaldatum = requireNotNull(vervaldatum),
        type = type,
        locatie = locatie,
        aantal = aantal.coerceAtLeast(1),
        geopend = geopend,
        geopendOp = if (geopend) geopendOp ?: LocalDate.now() else null,
        notitie = notitie.trim().ifEmpty { null },
        toegevoegdOp = toegevoegdOp,
    )

    companion object {
        fun van(item: VoorraadItem) = ItemFormulier(
            naam = item.naam,
            merk = item.merk.orEmpty(),
            vervaldatum = item.vervaldatum,
            type = item.type,
            locatie = item.locatie,
            aantal = item.aantal,
            notitie = item.notitie.orEmpty(),
            geopend = item.geopend,
            geopendOp = item.geopendOp,
        )
    }
}

/** Vraagt eerst de AI om advies na openen; valt terug op lokale vuistregels. */
class GeopendAdviseur @Inject constructor(
    private val ai: AiRepository,
) {
    suspend fun advies(naam: String, categorie: String?, locatie: Locatie, geopendOp: LocalDate, vervaldatum: LocalDate?): GeopendAdvies? {
        if (naam.isBlank()) return null
        val aiDagen = ai.geopendAdvies(naam, categorie, locatie)
        val dagen = aiDagen ?: GeopendHoudbaarheid.dagenNaOpenen(naam, categorie, locatie)
        val datum = GeopendHoudbaarheid.voorgesteldeDatum(geopendOp, dagen, vervaldatum)
        // Alleen voorstellen als het echt korter is dan de huidige datum.
        if (vervaldatum != null && !datum.isBefore(vervaldatum)) return null
        return GeopendAdvies(dagen, datum, doorAi = aiDagen != null)
    }
}
