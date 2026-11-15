package com.freshkeep.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freshkeep.app.data.local.VoorraadItem
import com.freshkeep.app.data.local.VoorraadWeergave
import com.freshkeep.app.data.repository.UndoActie
import com.freshkeep.app.data.repository.VoorraadRepository
import com.freshkeep.app.data.settings.InstellingenRepository
import com.freshkeep.app.domain.Vervalrekenaar
import com.freshkeep.app.domain.model.Locatie
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

enum class Tab(val label: String) {
    BINNENKORT("Binnenkort"),
    ALLES("Alles"),
    VERLOPEN("Verlopen"),
}

/** Eén rij in de lijst. */
data class LijstItem(val weergave: VoorraadWeergave, val dagen: Long) {
    val item: VoorraadItem get() = weergave.item
}

data class HomeUiState(
    val laden: Boolean = true,
    val items: List<LijstItem> = emptyList(),
    val tab: Tab = Tab.ALLES,
    val locatie: Locatie? = null,
    val zoekterm: String = "",
    val totaal: Int = 0,
    val perTab: Map<Tab, Int> = emptyMap(),
    val dagenVooraf: Int = 7,
    val toestemmingGevraagd: Boolean = true,
)

data class UndoMelding(val tekst: String, val actie: UndoActie)

/** Filtert en sorteert de lijst; puur zodat het makkelijk te testen is. */
object LijstFilter {
    fun telPerTab(items: List<LijstItem>, dagenVooraf: Int): Map<Tab, Int> = Tab.entries.associateWith { tab ->
        items.count { past(it, tab, dagenVooraf) }
    }

    fun filter(items: List<LijstItem>, tab: Tab, locatie: Locatie?, zoekterm: String, dagenVooraf: Int): List<LijstItem> {
        val zoek = zoekterm.trim().lowercase()
        return items
            .filter { past(it, tab, dagenVooraf) }
            .filter { locatie == null || it.item.locatie == locatie }
            .filter {
                zoek.isEmpty() || it.item.naam.lowercase().contains(zoek) ||
                    it.item.merk.orEmpty().lowercase().contains(zoek) || it.item.notitie.orEmpty().lowercase().contains(zoek)
            }
            .sortedWith(compareBy<LijstItem> { it.item.vervaldatum }.thenBy { it.item.naam.lowercase() })
    }

    private fun past(item: LijstItem, tab: Tab, dagenVooraf: Int) = when (tab) {
        Tab.ALLES -> true
        Tab.BINNENKORT -> item.dagen in 0..dagenVooraf.toLong()
        Tab.VERLOPEN -> item.dagen < 0
    }
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: VoorraadRepository,
    private val instellingen: InstellingenRepository,
) : ViewModel() {

    private data class Filters(val tab: Tab = Tab.ALLES, val locatie: Locatie? = null, val zoekterm: String = "")

    private val filters = MutableStateFlow(Filters())
    private val _undo = Channel<UndoMelding>(Channel.BUFFERED)
    val undoMeldingen: Flow<UndoMelding> = _undo.receiveAsFlow()

    private var laatsteSignaal = 0

    val state: StateFlow<HomeUiState> = combine(repo.items, filters, instellingen.instellingen) { lijst, f, s ->
        val vandaag = LocalDate.now()
        val items = lijst.map { LijstItem(it, Vervalrekenaar.dagenTot(it.item.vervaldatum, vandaag)) }
        HomeUiState(
            laden = false,
            items = LijstFilter.filter(items, f.tab, f.locatie, f.zoekterm, s.dagenVooraf),
            tab = f.tab,
            locatie = f.locatie,
            zoekterm = f.zoekterm,
            totaal = items.size,
            perTab = LijstFilter.telPerTab(items, s.dagenVooraf),
            dagenVooraf = s.dagenVooraf,
            toestemmingGevraagd = s.meldingToestemmingGevraagd,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun kiesTab(tab: Tab) = filters.update { it.copy(tab = tab) }

    fun kiesLocatie(locatie: Locatie?) = filters.update { it.copy(locatie = locatie) }

    fun zoek(term: String) = filters.update { it.copy(zoekterm = term) }

    /** Melding aangetikt: tab Binnenkort tonen (maar slechts één keer per signaal). */
    fun verwerkSignaal(signaal: Int) {
        if (signaal > laatsteSignaal) {
            laatsteSignaal = signaal
            filters.update { Filters(tab = Tab.BINNENKORT) }
        }
    }

    fun op(item: VoorraadItem) = viewModelScope.launch {
        val actie = repo.markeerOp(item)
        val tekst = if (actie.itemBlijft) "1 × ${item.naam} op, nog ${item.aantal - 1} over" else "${item.naam} is op. Gered!"
        _undo.send(UndoMelding(tekst, actie))
    }

    fun weggegooid(item: VoorraadItem) = viewModelScope.launch {
        _undo.send(UndoMelding("${item.naam} weggegooid", repo.markeerWeggegooid(item)))
    }

    fun ongedaanMaken(actie: UndoActie) = viewModelScope.launch { repo.ongedaanMaken(actie) }

    fun toestemmingGevraagd() = viewModelScope.launch { instellingen.markeerToestemmingGevraagd() }
}
