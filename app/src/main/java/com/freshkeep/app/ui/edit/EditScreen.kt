package com.freshkeep.app.ui.edit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.freshkeep.app.data.local.VoorraadItem
import com.freshkeep.app.data.repository.ProductRepository
import com.freshkeep.app.data.repository.VoorraadRepository
import com.freshkeep.app.ui.components.Bovenbalk
import com.freshkeep.app.ui.components.GroteKnop
import com.freshkeep.app.ui.form.DatumKaart
import com.freshkeep.app.ui.form.DatumKiezerDialoog
import com.freshkeep.app.ui.form.FormulierVelden
import com.freshkeep.app.ui.form.GeopendAdviseur
import com.freshkeep.app.ui.form.ItemFormulier
import com.freshkeep.app.ui.navigation.EditRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class EditUiState(
    val laden: Boolean = true,
    val origineel: VoorraadItem? = null,
    val categorie: String? = null,
    val formulier: ItemFormulier = ItemFormulier(),
    val datumKiezerOpen: Boolean = false,
)

@HiltViewModel
class EditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val voorraad: VoorraadRepository,
    private val producten: ProductRepository,
    private val adviseur: GeopendAdviseur,
) : ViewModel() {

    private val id = savedStateHandle.toRoute<EditRoute>().id
    private val _state = MutableStateFlow(EditUiState())
    val state: StateFlow<EditUiState> = _state.asStateFlow()
    private val _klaar = Channel<Unit>(Channel.CONFLATED)
    val klaar = _klaar.receiveAsFlow()

    init {
        viewModelScope.launch {
            val item = voorraad.get(id)
            if (item == null) {
                _klaar.send(Unit)
                return@launch
            }
            val categorie = item.barcode?.let { producten.uitCache(it)?.categorie }
            _state.update { EditUiState(laden = false, origineel = item, categorie = categorie, formulier = ItemFormulier.van(item)) }
        }
    }

    fun wijzig(f: ItemFormulier) = _state.update { it.copy(formulier = f) }
    fun openDatumKiezer() = _state.update { it.copy(datumKiezerOpen = true) }
    fun sluitDatumKiezer() = _state.update { it.copy(datumKiezerOpen = false) }
    fun kiesDatum(d: LocalDate) = _state.update { it.copy(formulier = it.formulier.copy(vervaldatum = d, zekerheid = null)) }

    fun zetGeopend(aan: Boolean) {
        _state.update {
            it.copy(formulier = it.formulier.copy(geopend = aan, geopendOp = if (aan) LocalDate.now() else null, advies = null, adviesBezig = aan))
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
        val origineel = s.origineel ?: return
        if (!s.formulier.isGeldig) return
        viewModelScope.launch {
            voorraad.bewaar(s.formulier.naarItem(origineel.barcode, origineel.id, origineel.toegevoegdOp))
            _klaar.send(Unit)
        }
    }
}

@Composable
fun EditScreen(onTerug: () -> Unit, viewModel: EditViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.klaar.collect { onTerug() } }

    Column(Modifier.fillMaxSize().imePadding()) {
        Bovenbalk("Aanpassen", onTerug, terugTekst = "Annuleren")
        if (state.laden) {
            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(32.dp), color = MaterialTheme.colorScheme.primary)
            return@Column
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            DatumKaart(state.formulier.vervaldatum, state.formulier.type, null, onTik = viewModel::openDatumKiezer)
            FormulierVelden(state.formulier, viewModel::wijzig, viewModel::zetGeopend, toonNaam = true)
            Spacer(Modifier.height(24.dp))
        }
        GroteKnop(
            tekst = "Bewaren",
            onClick = viewModel::opslaan,
            icoon = Icons.Rounded.Check,
            enabled = state.formulier.isGeldig,
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
        )
    }

    if (state.datumKiezerOpen) {
        DatumKiezerDialoog(state.formulier.vervaldatum, viewModel::kiesDatum, viewModel::sluitDatumKiezer)
    }
}
