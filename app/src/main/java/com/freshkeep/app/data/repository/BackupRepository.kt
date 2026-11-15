package com.freshkeep.app.data.repository

import android.content.Context
import android.net.Uri
import com.freshkeep.app.data.local.Geschiedenis
import com.freshkeep.app.data.local.GeschiedenisDao
import com.freshkeep.app.data.local.Product
import com.freshkeep.app.data.local.ProductDao
import com.freshkeep.app.data.local.TransactieUitvoerder
import com.freshkeep.app.data.local.VoorraadDao
import com.freshkeep.app.data.local.VoorraadItem
import com.freshkeep.app.domain.model.DatumType
import com.freshkeep.app.domain.model.Locatie
import com.freshkeep.app.domain.model.Resultaat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class BackupBestand(
    val app: String = "FreshKeep",
    val versie: Int = 1,
    val gemaaktOp: String,
    val producten: List<ProductDto> = emptyList(),
    val voorraad: List<VoorraadDto> = emptyList(),
    val geschiedenis: List<GeschiedenisDto> = emptyList(),
)

@Serializable
data class ProductDto(
    val barcode: String,
    val naam: String,
    val merk: String? = null,
    val hoeveelheid: String? = null,
    val fotoUrl: String? = null,
    val categorie: String? = null,
)

@Serializable
data class VoorraadDto(
    val id: Long,
    val barcode: String? = null,
    val naam: String,
    val merk: String? = null,
    val vervaldatum: String,
    val type: String = DatumType.ONBEKEND.name,
    val locatie: String = Locatie.KOELKAST.name,
    val aantal: Int = 1,
    val geopend: Boolean = false,
    val geopendOp: String? = null,
    val notitie: String? = null,
    val toegevoegdOp: Long,
)

@Serializable
data class GeschiedenisDto(
    val id: Long,
    val productnaam: String,
    val resultaat: String,
    val datum: String,
    val aantal: Int = 1,
)

data class ImportResultaat(val producten: Int, val voorraad: Int, val geschiedenis: Int)

/** Omzetting tussen Room-entiteiten en het JSON-formaat van de back-up. Puur en testbaar. */
object BackupMapper {
    val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun naarJson(producten: List<Product>, voorraad: List<VoorraadItem>, geschiedenis: List<Geschiedenis>, nu: Instant): String =
        json.encodeToString(
            BackupBestand.serializer(),
            BackupBestand(
                gemaaktOp = nu.toString(),
                producten = producten.map { ProductDto(it.barcode, it.naam, it.merk, it.hoeveelheid, it.fotoUrl, it.categorie) },
                voorraad = voorraad.map {
                    VoorraadDto(
                        id = it.id, barcode = it.barcode, naam = it.naam, merk = it.merk,
                        vervaldatum = it.vervaldatum.toString(), type = it.type.name, locatie = it.locatie.name,
                        aantal = it.aantal, geopend = it.geopend, geopendOp = it.geopendOp?.toString(),
                        notitie = it.notitie, toegevoegdOp = it.toegevoegdOp.toEpochMilli(),
                    )
                },
                geschiedenis = geschiedenis.map {
                    GeschiedenisDto(it.id, it.productnaam, it.resultaat.name, it.datum.toString(), it.aantal)
                },
            ),
        )

    /** Gooit een [IllegalArgumentException] met een Nederlandse uitleg als het bestand ongeldig is. */
    fun vanJson(tekst: String): Triple<List<Product>, List<VoorraadItem>, List<Geschiedenis>> {
        val bestand = try {
            json.decodeFromString(BackupBestand.serializer(), tekst)
        } catch (e: Exception) {
            throw IllegalArgumentException("Dit is geen geldig FreshKeep-back-upbestand.", e)
        }
        require(bestand.app == "FreshKeep") { "Dit bestand komt niet van FreshKeep." }
        require(bestand.versie <= 1) { "Deze back-up is gemaakt met een nieuwere versie van FreshKeep." }
        return try {
            Triple(
                bestand.producten.map { Product(it.barcode, it.naam, it.merk, it.hoeveelheid, it.fotoUrl, it.categorie) },
                bestand.voorraad.map {
                    VoorraadItem(
                        id = it.id, barcode = it.barcode, naam = it.naam, merk = it.merk,
                        vervaldatum = LocalDate.parse(it.vervaldatum),
                        type = DatumType.entries.firstOrNull { t -> t.name == it.type } ?: DatumType.ONBEKEND,
                        locatie = Locatie.fromName(it.locatie),
                        aantal = it.aantal.coerceAtLeast(1), geopend = it.geopend,
                        geopendOp = it.geopendOp?.let(LocalDate::parse), notitie = it.notitie,
                        toegevoegdOp = Instant.ofEpochMilli(it.toegevoegdOp),
                    )
                },
                bestand.geschiedenis.map {
                    Geschiedenis(
                        id = it.id, productnaam = it.productnaam,
                        resultaat = Resultaat.valueOf(it.resultaat),
                        datum = LocalDate.parse(it.datum), aantal = it.aantal,
                    )
                },
            )
        } catch (e: Exception) {
            throw IllegalArgumentException("De back-up bevat ongeldige gegevens.", e)
        }
    }
}

@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val productDao: ProductDao,
    private val voorraadDao: VoorraadDao,
    private val geschiedenisDao: GeschiedenisDao,
    private val transacties: TransactieUitvoerder,
) {
    suspend fun exporteer(uri: Uri): Int = withContext(Dispatchers.IO) {
        val voorraad = voorraadDao.alle()
        val tekst = BackupMapper.naarJson(productDao.alle(), voorraad, geschiedenisDao.alle(), Instant.now())
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(tekst.toByteArray()) }
            ?: error("Kan het bestand niet openen.")
        voorraad.size
    }

    /** Vervangt alle gegevens door de inhoud van de back-up. */
    suspend fun importeer(uri: Uri): ImportResultaat = withContext(Dispatchers.IO) {
        val tekst = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            ?: error("Kan het bestand niet openen.")
        val (producten, voorraad, geschiedenis) = BackupMapper.vanJson(tekst)
        transacties.uitvoeren {
            voorraadDao.deleteAll()
            geschiedenisDao.deleteAll()
            productDao.deleteAll()
            productDao.insertAll(producten)
            voorraadDao.insertAll(voorraad)
            geschiedenisDao.insertAll(geschiedenis)
        }
        ImportResultaat(producten.size, voorraad.size, geschiedenis.size)
    }
}
