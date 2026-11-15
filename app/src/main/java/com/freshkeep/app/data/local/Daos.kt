package com.freshkeep.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface ProductDao {
    @Query("SELECT * FROM product WHERE barcode = :barcode")
    suspend fun get(barcode: String): Product?

    @Upsert
    suspend fun upsert(product: Product)

    @Query("SELECT * FROM product")
    suspend fun alle(): List<Product>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(producten: List<Product>)

    @Query("DELETE FROM product")
    suspend fun deleteAll()
}

@Dao
interface VoorraadDao {
    @Query(
        """
        SELECT v.*, p.fotoUrl AS fotoUrl, p.categorie AS categorie
        FROM voorraad v LEFT JOIN product p ON v.barcode = p.barcode
        ORDER BY v.vervaldatum ASC, v.naam COLLATE NOCASE ASC
        """,
    )
    fun observeAlle(): Flow<List<VoorraadWeergave>>

    @Query("SELECT * FROM voorraad WHERE id = :id")
    suspend fun get(id: Long): VoorraadItem?

    @Query("SELECT * FROM voorraad ORDER BY vervaldatum ASC")
    suspend fun alle(): List<VoorraadItem>

    @Query("SELECT * FROM voorraad WHERE vervaldatum BETWEEN :van AND :tot ORDER BY vervaldatum ASC")
    suspend fun tussen(van: LocalDate, tot: LocalDate): List<VoorraadItem>

    @Insert
    suspend fun insert(item: VoorraadItem): Long

    @Update
    suspend fun update(item: VoorraadItem)

    @Upsert
    suspend fun upsert(item: VoorraadItem)

    @Delete
    suspend fun delete(item: VoorraadItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<VoorraadItem>)

    @Query("DELETE FROM voorraad")
    suspend fun deleteAll()
}

@Dao
interface GeschiedenisDao {
    @Insert
    suspend fun insert(item: Geschiedenis): Long

    @Query("DELETE FROM geschiedenis WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM geschiedenis WHERE datum >= :vanaf")
    fun observeVanaf(vanaf: LocalDate): Flow<List<Geschiedenis>>

    @Query(
        """
        SELECT productnaam AS naam, SUM(aantal) AS totaal FROM geschiedenis
        WHERE resultaat = 'WEGGEGOOID'
        GROUP BY LOWER(productnaam) ORDER BY totaal DESC, naam ASC LIMIT 5
        """,
    )
    fun observeTopWeggegooid(): Flow<List<ProductTelling>>

    @Query("SELECT * FROM geschiedenis")
    suspend fun alle(): List<Geschiedenis>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<Geschiedenis>)

    @Query("DELETE FROM geschiedenis")
    suspend fun deleteAll()
}
