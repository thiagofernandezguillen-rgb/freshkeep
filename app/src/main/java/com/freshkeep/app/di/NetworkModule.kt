package com.freshkeep.app.di

import com.freshkeep.app.BuildConfig
import com.freshkeep.app.data.remote.FreshKeepBackendApi
import com.freshkeep.app.data.remote.OpenFoodFactsApi
import com.freshkeep.app.data.settings.InstellingenRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val USER_AGENT = "FreshKeep/${BuildConfig.VERSION_NAME} (Android)"

    @Provides
    @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
        isLenient = true
    }

    @Provides
    @Singleton
    fun okHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
        }
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            }
        }
        .build()

    @Provides
    @Singleton
    fun openFoodFacts(client: OkHttpClient, json: Json): OpenFoodFactsApi = Retrofit.Builder()
        .baseUrl(OpenFoodFactsApi.BASIS_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(OpenFoodFactsApi::class.java)

    @Provides
    @Singleton
    fun backend(client: OkHttpClient, json: Json, instellingen: InstellingenRepository): FreshKeepBackendApi {
        // Het toestel-ID wordt één keer gelezen en daarna hergebruikt.
        val toestelId by lazy { runBlocking { instellingen.toestelId() } }
        val backendClient = client.newBuilder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("X-App-Token", BuildConfig.APP_TOKEN)
                        .header("X-Device-Id", toestelId)
                        .build(),
                )
            }
            .build()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.BACKEND_URL)
            .client(backendClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(FreshKeepBackendApi::class.java)
    }
}
