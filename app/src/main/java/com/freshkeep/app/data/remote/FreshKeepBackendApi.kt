package com.freshkeep.app.data.remote

import kotlinx.serialization.Serializable
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.POST

/** Onze eigen Cloudflare Worker (zie /backend). De Gemini-sleutel zit alleen daar. */
interface FreshKeepBackendApi {
    /** Body: JPEG-bytes (image/jpeg). */
    @POST("read-date")
    suspend fun leesDatum(@Body foto: RequestBody): DatumAntwoord

    /** Body: JPEG-bytes (image/jpeg). */
    @POST("identify-product")
    suspend fun herkenProduct(@Body foto: RequestBody): ProductAntwoord

    @POST("opened-shelf-life")
    suspend fun geopendAdvies(@Body verzoek: GeopendVerzoek): GeopendAntwoord
}

@Serializable
data class DatumAntwoord(
    val datum: String? = null,
    val type: String? = null,
    val zekerheid: Double = 0.0,
)

@Serializable
data class ProductAntwoord(
    val naam: String? = null,
    val merk: String? = null,
    val zekerheid: Double = 0.0,
)

@Serializable
data class GeopendVerzoek(
    val naam: String,
    val categorie: String? = null,
    val locatie: String,
)

@Serializable
data class GeopendAntwoord(
    val dagen: Int? = null,
    val uitleg: String? = null,
)
