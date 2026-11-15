package com.freshkeep.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.freshkeep.app.domain.model.DatumType
import com.freshkeep.app.domain.model.Locatie
import com.freshkeep.app.domain.model.Resultaat
import java.time.Instant
import java.time.LocalDate

/** Productcache: gevonden via Open Food Facts of door de gebruiker/AI ingevuld. */
@Entity(tableName = "product")
data class Product(
    @PrimaryKey val barcode: String,
    val naam: String,
    val merk: String?,
    val hoeveelheid: String?,
    val fotoUrl: String?,
    val categorie: String?,
)

/**
 * Een product dat nu in huis is.
 * [merk] staat er ook in zodat handmatig toegevoegde producten (zonder barcode) een merk kunnen hebben.
 */
@Entity(
    tableName = "voorraad",
    indices = [Index("barcode"), Index("vervaldatum")],
)
data class VoorraadItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val barcode: String?,
    val naam: String,
    val merk: String?,
    val vervaldatum: LocalDate,
    val type: DatumType,
    val locatie: Locatie,
    val aantal: Int,
    val geopend: Boolean,
    val geopendOp: LocalDate?,
    val notitie: String?,
    val toegevoegdOp: Instant,
)

/** Wat er met een product gebeurd is; basis voor de statistieken. */
@Entity(tableName = "geschiedenis", indices = [Index("datum")])
data class Geschiedenis(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productnaam: String,
    val resultaat: Resultaat,
    val datum: LocalDate,
    @ColumnInfo(defaultValue = "1") val aantal: Int = 1,
)

/** Voorraaditem samen met de foto en categorie uit de productcache. */
data class VoorraadWeergave(
    @Embedded val item: VoorraadItem,
    val fotoUrl: String?,
    val categorie: String?,
)

/** Resultaat van de top-5-query. */
data class ProductTelling(val naam: String, val totaal: Int)
