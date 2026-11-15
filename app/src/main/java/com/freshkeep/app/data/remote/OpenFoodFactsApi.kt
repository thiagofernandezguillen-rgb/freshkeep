package com.freshkeep.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** https://world.openfoodfacts.org/api/v2/product/{barcode}.json */
interface OpenFoodFactsApi {
    @GET("api/v2/product/{barcode}.json")
    suspend fun product(
        @Path("barcode") barcode: String,
        @Query("fields") velden: String = VELDEN,
        @Query("lc") taal: String = "nl",
    ): Response<OffAntwoord>

    companion object {
        const val BASIS_URL = "https://world.openfoodfacts.org/"
        private const val VELDEN =
            "product_name,product_name_nl,generic_name_nl,brands,quantity,image_front_small_url,image_front_url,image_url,categories"
    }
}

@Serializable
data class OffAntwoord(
    val status: Int = 0,
    val product: OffProduct? = null,
)

@Serializable
data class OffProduct(
    @SerialName("product_name") val productName: String? = null,
    @SerialName("product_name_nl") val productNameNl: String? = null,
    @SerialName("generic_name_nl") val genericNameNl: String? = null,
    val brands: String? = null,
    val quantity: String? = null,
    @SerialName("image_front_small_url") val imageFrontSmallUrl: String? = null,
    @SerialName("image_front_url") val imageFrontUrl: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    val categories: String? = null,
) {
    /** Nederlandse naam als die er is, anders de algemene naam. */
    val besteNaam: String?
        get() = listOf(productNameNl, productName, genericNameNl).firstOrNull { !it.isNullOrBlank() }?.trim()

    /** Eerste merk uit "Merk A, Merk B". */
    val eersteMerk: String?
        get() = brands?.split(',')?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }

    val foto: String?
        get() = listOf(imageFrontUrl, imageFrontSmallUrl, imageUrl).firstOrNull { !it.isNullOrBlank() }

    /** Meest specifieke categorie (laatste in de lijst), zonder taalprefix zoals "en:". */
    val specifiekeCategorie: String?
        get() = categories?.split(',')?.map { it.trim().substringAfter(':') }?.lastOrNull { it.isNotEmpty() }
}
