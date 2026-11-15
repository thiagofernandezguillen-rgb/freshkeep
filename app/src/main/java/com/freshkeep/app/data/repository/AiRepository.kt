package com.freshkeep.app.data.repository

import com.freshkeep.app.BuildConfig
import com.freshkeep.app.data.remote.DatumAntwoord
import com.freshkeep.app.data.remote.FreshKeepBackendApi
import com.freshkeep.app.data.remote.GeopendVerzoek
import com.freshkeep.app.data.remote.Netwerkstatus
import com.freshkeep.app.domain.VervaldatumParser
import com.freshkeep.app.domain.model.DatumType
import com.freshkeep.app.domain.model.Locatie
import kotlinx.coroutines.CancellationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AiResultaat<out T> {
    data class Succes<T>(val waarde: T) : AiResultaat<T>
    data class Mislukt(val reden: String) : AiResultaat<Nothing>
}

data class DatumHerkenning(val datum: LocalDate?, val type: DatumType, val zekerheid: Double)

data class ProductHerkenning(val naam: String, val merk: String?)

/** Praat met onze backend. Faalt nooit met een exception: altijd een [AiResultaat]. */
@Singleton
class AiRepository @Inject constructor(
    private val api: FreshKeepBackendApi,
    private val netwerk: Netwerkstatus,
) {
    /** False zolang er geen echte backend-URL en app-token in local.properties staan. */
    val isIngesteld: Boolean
        get() = BuildConfig.APP_TOKEN.isNotBlank() && !BuildConfig.BACKEND_URL.contains("example.workers.dev")

    suspend fun leesDatum(jpeg: ByteArray): AiResultaat<DatumHerkenning> = veilig {
        verwerkDatum(api.leesDatum(jpeg.toRequestBody(JPEG)))
    }

    suspend fun herkenProduct(jpeg: ByteArray): AiResultaat<ProductHerkenning> = veilig {
        val antwoord = api.herkenProduct(jpeg.toRequestBody(JPEG))
        val naam = antwoord.naam?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw OnherkendException()
        ProductHerkenning(naam, antwoord.merk?.trim()?.takeIf { it.isNotEmpty() })
    }

    /** Aantal dagen houdbaar na openen volgens de AI, of null als dat niet lukt. */
    suspend fun geopendAdvies(naam: String, categorie: String?, locatie: Locatie): Int? {
        val resultaat = veilig {
            api.geopendAdvies(GeopendVerzoek(naam, categorie, locatie.label)).dagen
                ?.takeIf { it in 0..365 } ?: throw OnherkendException()
        }
        return (resultaat as? AiResultaat.Succes)?.waarde
    }

    private suspend fun <T> veilig(blok: suspend () -> T): AiResultaat<T> {
        if (!isIngesteld) return AiResultaat.Mislukt("De AI-dienst is nog niet ingesteld.")
        if (!netwerk.isOnline()) return AiResultaat.Mislukt("Geen internet.")
        return try {
            AiResultaat.Succes(blok())
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            AiResultaat.Mislukt(
                when (e.code()) {
                    401, 403 -> "De app heeft geen toegang tot de AI-dienst."
                    413 -> "De foto is te groot."
                    429 -> "Daglimiet van de AI bereikt. Probeer morgen opnieuw."
                    else -> "De AI-dienst is even niet beschikbaar."
                },
            )
        } catch (e: IOException) {
            AiResultaat.Mislukt("Geen verbinding met de AI-dienst.")
        } catch (e: OnherkendException) {
            AiResultaat.Mislukt("De AI kon niets herkennen.")
        } catch (e: Exception) {
            AiResultaat.Mislukt("Er ging iets mis bij de AI.")
        }
    }

    private class OnherkendException : Exception()

    companion object {
        private val JPEG = "image/jpeg".toMediaType()

        /** Zet het antwoord van de backend om; vangt ook niet-ISO-datums op. */
        fun verwerkDatum(antwoord: DatumAntwoord): DatumHerkenning {
            val tekst = antwoord.datum?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
            val iso = tekst?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val geparseerd = if (iso == null && tekst != null) VervaldatumParser.parse(tekst) else null
            val datum = iso ?: geparseerd?.datum
            val type = DatumType.fromApi(antwoord.type).takeIf { it != DatumType.ONBEKEND }
                ?: geparseerd?.type ?: DatumType.ONBEKEND
            val zekerheid = if (datum == null) 0.0 else antwoord.zekerheid.coerceIn(0.0, 1.0)
            return DatumHerkenning(datum, type, zekerheid)
        }
    }
}
