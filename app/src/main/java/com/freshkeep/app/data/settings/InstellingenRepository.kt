package com.freshkeep.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.freshkeep.app.domain.model.Locatie
import com.freshkeep.app.domain.model.Thema
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "instellingen")

data class Instellingen(
    val meldingUur: Int = 18,
    val meldingMinuut: Int = 0,
    val dagenVooraf: Int = 7,
    val standaardLocatie: Locatie = Locatie.KOELKAST,
    val thema: Thema = Thema.AUTOMATISCH,
    val meldingToestemmingGevraagd: Boolean = false,
)

@Singleton
class InstellingenRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Sleutels {
        val UUR = intPreferencesKey("melding_uur")
        val MINUUT = intPreferencesKey("melding_minuut")
        val DAGEN_VOORAF = intPreferencesKey("dagen_vooraf")
        val LOCATIE = stringPreferencesKey("standaard_locatie")
        val THEMA = stringPreferencesKey("thema")
        val TOESTEMMING_GEVRAAGD = booleanPreferencesKey("toestemming_gevraagd")
        val TOESTEL_ID = stringPreferencesKey("toestel_id")
    }

    val instellingen: Flow<Instellingen> = context.dataStore.data.map { p ->
        Instellingen(
            meldingUur = p[Sleutels.UUR] ?: 18,
            meldingMinuut = p[Sleutels.MINUUT] ?: 0,
            dagenVooraf = (p[Sleutels.DAGEN_VOORAF] ?: 7).coerceIn(1, 14),
            standaardLocatie = Locatie.fromName(p[Sleutels.LOCATIE]),
            thema = Thema.entries.firstOrNull { it.name == p[Sleutels.THEMA] } ?: Thema.AUTOMATISCH,
            meldingToestemmingGevraagd = p[Sleutels.TOESTEMMING_GEVRAAGD] ?: false,
        )
    }

    suspend fun huidige(): Instellingen = instellingen.first()

    suspend fun zetMeldingTijd(uur: Int, minuut: Int) = context.dataStore.edit {
        it[Sleutels.UUR] = uur.coerceIn(0, 23)
        it[Sleutels.MINUUT] = minuut.coerceIn(0, 59)
    }

    suspend fun zetDagenVooraf(dagen: Int) = context.dataStore.edit { it[Sleutels.DAGEN_VOORAF] = dagen.coerceIn(1, 14) }

    suspend fun zetStandaardLocatie(locatie: Locatie) = context.dataStore.edit { it[Sleutels.LOCATIE] = locatie.name }

    suspend fun zetThema(thema: Thema) = context.dataStore.edit { it[Sleutels.THEMA] = thema.name }

    suspend fun markeerToestemmingGevraagd() = context.dataStore.edit { it[Sleutels.TOESTEMMING_GEVRAAGD] = true }

    /** Willekeurige, anonieme ID voor de daglimiet op de backend. */
    suspend fun toestelId(): String {
        context.dataStore.data.first()[Sleutels.TOESTEL_ID]?.let { return it }
        val nieuw = UUID.randomUUID().toString()
        context.dataStore.edit { p -> if (p[Sleutels.TOESTEL_ID] == null) p[Sleutels.TOESTEL_ID] = nieuw }
        return context.dataStore.data.first()[Sleutels.TOESTEL_ID] ?: nieuw
    }
}
