package com.freshkeep.app.ui.add

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.freshkeep.app.data.local.Product
import com.freshkeep.app.data.remote.Netwerkstatus
import com.freshkeep.app.data.repository.AiRepository
import com.freshkeep.app.data.repository.AiResultaat
import com.freshkeep.app.data.repository.ProductRepository
import com.freshkeep.app.data.repository.VoorraadRepository
import com.freshkeep.app.data.repository.ZoekResultaat
import com.freshkeep.app.data.settings.InstellingenRepository
import com.freshkeep.app.ui.components.BannerSoort
import com.freshkeep.app.ui.form.GeopendAdviseur
import com.freshkeep.app.ui.form.ItemFormulier
import com.freshkeep.app.ui.navigation.AddRoute
import com.freshkeep.app.util.Fotos
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject

/** Stappen van "product toevoegen". */
enum class Stap { ZOEKEN, PRODUCT_FOTO, PRODUCT_NAAM, DATUM_FOTO, BEVESTIGEN }

data class Melding(val tekst: String, val soort: BannerSoort = BannerSoort.INFO)

data class AddUiState(
    val stap: Stap = Stap.ZOEKEN,
    val barcode: String? = null,
    val fotoUrl: String? = null,
    val hoeveelheid: String? = null,
    val categorie: String? = null,
    val uitCache: Boolean = false,
    val formulier: ItemFormulier = ItemFormulier(),
    /** Tekst tijdens het laden; null = niet bezig. */
    val bezig: String? = null,
    val melding: Melding? = null,
    val datumKiezerOpen: Boolean = false,
)

@HiltViewModel
class AddViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val producten: ProductRepository,
    private val voorraad: VoorraadRepository,
    private val ai: AiRepository,
    private val netwerk: Netwerkstatus,
    private val instellingen: InstellingenRepository,
    private val adviseur: GeopendAdviseur,
) : ViewModel() {

    private val barcode: String? = savedStateHandle.toRoute<AddRoute>().barcode

    private val _state = MutableStateFlow(AddUiState(barcode = barcode))
    val state: StateFlow<AddUiState> = _state.asStateFlow()

    private val _klaar = Channel<String>(Channel.BUFFERED)
    /** Productnaam zodra het product bewaard is. */
    val klaar: Flow<String> = _klaar.receiveAsFlow()

    init {
        viewModelScope.launch {
            val standaard = instellingen.huidige().standaardLocatie
            _state.update { it.copy(formulier = it.formulier.copy(locatie = standaard)) }
            if (barcode == null) {
                _state.update {
                    it.copy(
                        stap = Stap.PRODUCT_NAAM,
                        melding = if (!netwerk.isOnline()) GEEN_INTERNET else null,
                    )
                }
            } else {
                zoek(barcode)
            }
        }
    }

    private suspend fun zoek(code: String) {
        _state.update { it.copy(stap = Stap.ZOEKEN, bezig = "Product opzoeken…") }
        when (val r = producten.zoek(code)) {
            is ZoekResultaat.Gevonden -> {
                _state.update {
                    it.copy(
                        bezig = null,
                        fotoUrl = r.product.fotoUrl,
                        hoeveelheid = r.product.hoeveelheid,
                        categorie = r.product.categorie,
                        uitCache = r.uitCache,
                        formulier = it.formulier.copy(naam = r.product.naam, merk = r.product.merk.orEmpty()),
                    )
                }
                naarDatumStap()
            }
            ZoekResultaat.NietGevonden -> _state.update {
                if (ai.isIngesteld && netwerk.isOnline()) {
                    it.copy(
                        stap = Stap.PRODUCT_FOTO, bezig = null,
                        melding = Melding("Dit product staat nog niet in de databank. Maak een foto van de verpakking, dan herkent de AI het."),
                    )
                } else {
                    it.copy(
                        stap = Stap.PRODUCT_NAAM, bezig = null,
                        melding = Melding("Product niet gevonden. Typ de naam zelf; de volgende keer herkent FreshKeep het meteen."),
                    )
                }
            }
            ZoekResultaat.GeenInternet -> _state.update {
                it.copy(stap = Stap.PRODUCT_NAAM, bezig = null, melding = GEEN_INTERNET)
            }
            is ZoekResultaat.Fout -> _state.update {
                it.copy(
                    stap = Stap.PRODUCT_NAAM, bezig = null,
                    melding = Melding("Opzoeken lukte niet. Typ de naam zelf.", BannerSoort.WAARSCHUWING),
                )
            }
        }
    }

    /** Naar de datumfoto, of meteen naar de datumkiezer als de AI niet bereikbaar is. */
    private fun naarDatumStap() {
        if (ai.isIngesteld && netwerk.isOnline()) {
            _state.update { it.copy(stap = Stap.DATUM_FOTO, bezig = null, melding = null) }
        } else {
            val melding = if (!netwerk.isOnline()) {
                Melding("Geen internet: kies de vervaldatum zelf.", BannerSoort.OFFLINE)
            } else {
                Melding("Datum lezen met AI is niet ingesteld. Kies de datum zelf.")
            }
            _state.update { it.copy(stap = Stap.BEVESTIGEN, bezig = null, melding = melding, datumKiezerOpen = true) }
        }
    }

    fun productFotoGenomen(jpeg: ByteArray) = viewModelScope.launch {
        _state.update { it.copy(bezig = "Product herkennen…") }
        val code = barcode ?: "handmatig_${System.currentTimeMillis()}"
        val foto = withContext(Dispatchers.IO) { runCatching { Fotos.bewaarProductFoto(context, code, jpeg) }.getOrNull() }
        when (val r = ai.herkenProduct(jpeg)) {
            is AiResultaat.Succes -> _state.update {
                it.copy(
                    stap = Stap.PRODUCT_NAAM, bezig = null, fotoUrl = foto,
                    formulier = it.formulier.copy(naam = r.waarde.naam, merk = r.waarde.merk.orEmpty()),
                    melding = Melding("Herkend door de AI. Klopt de naam?"),
                )
            }
            is AiResultaat.Mislukt -> _state.update {
                it.copy(
                    stap = Stap.PRODUCT_NAAM, bezig = null, fotoUrl = foto,
                    melding = Melding("${r.reden} Typ de naam zelf.", BannerSoort.WAARSCHUWING),
                )
            }
        }
    }

    fun fotoMislukt() = _state.update {
        it.copy(bezig = null, melding = Melding("De foto lukte niet. Probeer opnieuw.", BannerSoort.WAARSCHUWING))
    }

    fun zelfTypen() = _state.update { it.copy(stap = Stap.PRODUCT_NAAM, melding = null) }

    /** Naam is ingevuld: product cachen (bij barcode) en verder naar de datum. */
    fun naamBevestigd() {
        val f = _state.value.formulier
        if (f.naam.isBlank()) return
        viewModelScope.launch {
            if (barcode != null) {
                val s = _state.value
                producten.bewaar(
                    Product(barcode, f.naam.trim(), f.merk.trim().ifEmpty { null }, s.hoeveelheid, s.fotoUrl, s.categorie),
                )
            }
            naarDatumStap()
        }
    }

    fun datumFotoGenomen(jpeg: ByteArray) = viewModelScope.launch {
        _state.update { it.copy(bezig = "Datum lezen…") }
        when (val r = ai.leesDatum(jpeg)) {
            is AiResultaat.Succes -> {
                val h = r.waarde
                val onzeker = h.datum == null || h.zekerheid < MIN_ZEKERHEID
                _state.update {
                    it.copy(
                        stap = Stap.BEVESTIGEN,
                        bezig = null,
                        formulier = it.formulier.copy(
                            vervaldatum = h.datum ?: it.formulier.vervaldatum,
                            type = h.type,
                            zekerheid = h.datum?.let { h.zekerheid },
                        ),
                        datumKiezerOpen = onzeker,
                        melding = when {
                            h.datum == null -> Melding("Geen datum gevonden op de foto. Kies de datum zelf.", BannerSoort.WAARSCHUWING)
                            onzeker -> Melding("De AI is niet helemaal zeker. Controleer de datum.")
                            else -> null
                        },
                    )
                }
            }
            is AiResultaat.Mislukt -> _state.update {
                it.copy(
                    stap = Stap.BEVESTIGEN, bezig = null, datumKiezerOpen = true,
                    melding = Melding("Datum lezen lukte niet. ${r.reden} Kies de datum zelf.", BannerSoort.WAARSCHUWING),
                )
            }
        }
    }

    fun datumZelfKiezen() = _state.update { it.copy(stap = Stap.BEVESTIGEN, datumKiezerOpen = true, melding = null) }

    fun openDatumKiezer() = _state.update { it.copy(datumKiezerOpen = true) }

    fun sluitDatumKiezer() = _state.update { it.copy(datumKiezerOpen = false) }

    fun kiesDatum(datum: LocalDate) = _state.update {
        it.copy(formulier = it.formulier.copy(vervaldatum = datum, zekerheid = null), datumKiezerOpen = false, melding = null)
    }

    fun wijzig(formulier: ItemFormulier) = _state.update { it.copy(formulier = formulier) }

    fun zetGeopend(aan: Boolean) {
        _state.update {
            it.copy(
                formulier = it.formulier.copy(
                    geopend = aan,
                    geopendOp = if (aan) LocalDate.now() else null,
                    advies = null,
                    adviesBezig = aan,
                ),
            )
        }
        if (!aan) return
        viewModelScope.launch {
            val s = _state.value
            val advies = adviseur.advies(s.formulier.naam, s.categorie, s.formulier.locatie, LocalDate.now(), s.formulier.vervaldatum)
            _state.update { it.copy(formulier = it.formulier.copy(advies = advies, adviesBezig = false)) }
        }
    }

    fun opslaan() {
        val s = _state.value
        if (s.formulier.vervaldatum == null) {
            _state.update { it.copy(datumKiezerOpen = true) }
            return
        }
        if (!s.formulier.isGeldig || s.bezig != null) return
        viewModelScope.launch {
            _state.update { it.copy(bezig = "Bewaren…") }
            voorraad.voegToe(s.formulier.naarItem(barcode))
            _klaar.send(s.formulier.naam.trim())
        }
    }

    companion object {
        /** Onder deze zekerheid opent de datumkiezer automatisch. */
        const val MIN_ZEKERHEID = 0.8
        private val GEEN_INTERNET = Melding(
            "Geen internet: voeg het product handmatig toe en kies zelf de datum.",
            BannerSoort.OFFLINE,
        )
    }
}
