package com.freshkeep.app.data.repository

import com.freshkeep.app.data.local.Product
import com.freshkeep.app.data.local.ProductDao
import com.freshkeep.app.data.remote.Netwerkstatus
import com.freshkeep.app.data.remote.OpenFoodFactsApi
import kotlinx.coroutines.CancellationException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ZoekResultaat {
    data class Gevonden(val product: Product, val uitCache: Boolean) : ZoekResultaat
    data object NietGevonden : ZoekResultaat
    data object GeenInternet : ZoekResultaat
    data class Fout(val melding: String) : ZoekResultaat
}

/** Zoekt producten op: eerst in de lokale cache, daarna bij Open Food Facts. */
@Singleton
class ProductRepository @Inject constructor(
    private val dao: ProductDao,
    private val api: OpenFoodFactsApi,
    private val netwerk: Netwerkstatus,
) {
    suspend fun zoek(barcode: String): ZoekResultaat {
        dao.get(barcode)?.let { return ZoekResultaat.Gevonden(it, uitCache = true) }
        if (!netwerk.isOnline()) return ZoekResultaat.GeenInternet

        return try {
            val antwoord = api.product(barcode)
            val off = antwoord.body()?.product
            val naam = off?.besteNaam
            if (!antwoord.isSuccessful || antwoord.body()?.status != 1 || off == null || naam == null) {
                ZoekResultaat.NietGevonden
            } else {
                val product = Product(
                    barcode = barcode,
                    naam = naam,
                    merk = off.eersteMerk,
                    hoeveelheid = off.quantity?.trim()?.takeIf { it.isNotEmpty() },
                    fotoUrl = off.foto,
                    categorie = off.specifiekeCategorie,
                )
                dao.upsert(product) // cache: volgende scan werkt direct en offline
                ZoekResultaat.Gevonden(product, uitCache = false)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            ZoekResultaat.GeenInternet
        } catch (e: Exception) {
            ZoekResultaat.Fout(e.message ?: "Onbekende fout")
        }
    }

    /** Bewaart een product dat de gebruiker of de AI heeft ingevuld. */
    suspend fun bewaar(product: Product) = dao.upsert(product)

    /** Alleen de lokale cache, zonder netwerk. */
    suspend fun uitCache(barcode: String): Product? = dao.get(barcode)
}
