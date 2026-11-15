package com.freshkeep.app.ui.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freshkeep.app.data.local.VoorraadItem
import com.freshkeep.app.domain.Vervalrekenaar
import com.freshkeep.app.domain.model.Locatie
import com.freshkeep.app.ui.components.GroteKnop
import com.freshkeep.app.ui.components.Kaart
import com.freshkeep.app.ui.components.LeegScherm
import com.freshkeep.app.ui.components.ProductFoto
import com.freshkeep.app.ui.components.Segmenten
import com.freshkeep.app.ui.components.StatusLabel
import com.freshkeep.app.ui.theme.FreshKeepTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate

private const val UNDO_DUUR_MS = 5_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    openBinnenkortSignaal: Int,
    toegevoegd: String?,
    onToegevoegdGetoond: () -> Unit,
    onScan: () -> Unit,
    onHandmatig: () -> Unit,
    onAanpassen: (Long) -> Unit,
    onStatistieken: () -> Unit,
    onInstellingen: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var detailId by rememberSaveable { mutableStateOf<Long?>(null) }

    LaunchedEffect(openBinnenkortSignaal) { viewModel.verwerkSignaal(openBinnenkortSignaal) }

    // Snackbar met "Ongedaan maken", precies 5 seconden zichtbaar.
    LaunchedEffect(viewModel) {
        viewModel.undoMeldingen.collect { melding ->
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                val resultaat = withTimeoutOrNull(UNDO_DUUR_MS) {
                    snackbar.showSnackbar(melding.tekst, actionLabel = "Ongedaan maken", duration = SnackbarDuration.Indefinite)
                }
                if (resultaat == SnackbarResult.ActionPerformed) viewModel.ongedaanMaken(melding.actie)
            }
        }
    }

    LaunchedEffect(toegevoegd) {
        if (toegevoegd != null) {
            onToegevoegdGetoond()
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                withTimeoutOrNull(2_500) { snackbar.showSnackbar("$toegevoegd toegevoegd", duration = SnackbarDuration.Indefinite) }
            }
        }
    }

    // Toestemming voor meldingen: vragen zodra er een eerste product is (Android 13+).
    val toestemming = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(state.totaal, state.toestemmingGevraagd) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && state.totaal > 0 && !state.toestemmingGevraagd && !state.laden) {
            viewModel.toestemmingGevraagd()
            toestemming.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val leeg = !state.laden && state.totaal == 0

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {
            SnackbarHost(snackbar, modifier = Modifier.padding(bottom = 72.dp)) { data ->
                Snackbar(
                    snackbarData = data,
                    shape = RoundedCornerShape(16.dp),
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    actionColor = MaterialTheme.colorScheme.inversePrimary,
                )
            }
        },
        floatingActionButton = {
            AnimatedVisibility(visible = !leeg, enter = scaleIn(), exit = scaleOut()) {
                ExtendedFloatingActionButton(
                    onClick = onScan,
                    icon = { Icon(Icons.Rounded.QrCodeScanner, contentDescription = null) },
                    text = { Text("Scannen", style = MaterialTheme.typography.labelLarge) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.height(60.dp).heightIn(min = 60.dp),
                )
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 110.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item("kop") { Kop(onStatistieken, onInstellingen) }

            if (leeg) {
                item("leeg") {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        LeegScherm(
                            icoon = Icons.Outlined.Kitchen,
                            titel = "Nog niets in huis",
                            uitleg = "Scan de barcode van een product en maak een foto van de datum. FreshKeep waarschuwt je op tijd, zodat er niets meer weggegooid wordt.",
                            knopTekst = "Scannen",
                            knopIcoon = Icons.Rounded.QrCodeScanner,
                            onKnop = onScan,
                        )
                        TextButton(onClick = onHandmatig) { Text("Of voeg handmatig toe") }
                    }
                }
            } else if (!state.laden) {
                item("zoek") { ZoekBalk(state.zoekterm, viewModel::zoek) }
                item("tabs") {
                    Segmenten(
                        opties = Tab.entries.map { tab -> "${tab.label} ${state.perTab[tab] ?: 0}" },
                        geselecteerd = state.tab.ordinal,
                        onKies = { viewModel.kiesTab(Tab.entries[it]) },
                    )
                }
                item("locaties") { LocatieFilter(state.locatie, viewModel::kiesLocatie) }

                if (state.items.isEmpty()) {
                    item("geen") {
                        LeegScherm(
                            icoon = Icons.Outlined.SearchOff,
                            titel = when {
                                state.zoekterm.isNotBlank() -> "Niets gevonden"
                                state.tab == Tab.VERLOPEN -> "Niets verlopen"
                                state.tab == Tab.BINNENKORT -> "Alles is nog vers"
                                else -> "Geen producten hier"
                            },
                            uitleg = when {
                                state.zoekterm.isNotBlank() -> "Geen product past bij \"${state.zoekterm}\"."
                                state.tab == Tab.VERLOPEN -> "Top! Er is niets over datum."
                                state.tab == Tab.BINNENKORT -> "Niets vervalt in de komende ${state.dagenVooraf} dagen."
                                else -> "Kies een andere locatie of scan een product."
                            },
                        )
                    }
                }

                items(state.items, key = { it.item.id }) { rij ->
                    VoorraadRij(
                        rij = rij,
                        onTik = { detailId = rij.item.id },
                        onOp = { viewModel.op(rij.item) },
                        onWeggegooid = { viewModel.weggegooid(rij.item) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }

    val detail = state.items.firstOrNull { it.item.id == detailId }
    if (detailId != null && detail != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { detailId = null },
            sheetState = sheetState,
            containerColor = FreshKeepTheme.kleuren.kaart,
        ) {
            val sluitEn: (() -> Unit) -> Unit = { actie ->
                scope.launch { sheetState.hide() }.invokeOnCompletion { detailId = null; actie() }
            }
            DetailInhoud(
                rij = detail,
                onOp = { sluitEn { viewModel.op(detail.item) } },
                onWeggegooid = { sluitEn { viewModel.weggegooid(detail.item) } },
                onAanpassen = { sluitEn { onAanpassen(detail.item.id) } },
            )
        }
    } else if (detailId != null && !state.laden) {
        detailId = null
    }
}

@Composable
private fun Kop(onStatistieken: () -> Unit, onInstellingen: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) {
        Column(Modifier.weight(1f)) {
            Text(
                Vervalrekenaar.formatLang(LocalDate.now()).substringBeforeLast(' ').uppercase(Vervalrekenaar.NEDERLANDS),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("FreshKeep", style = MaterialTheme.typography.displaySmall)
        }
        RondeIconKnop(Icons.Outlined.BarChart, "Statistieken", onStatistieken)
        Spacer(Modifier.width(8.dp))
        RondeIconKnop(Icons.Outlined.Settings, "Instellingen", onInstellingen)
    }
}

@Composable
private fun RondeIconKnop(icoon: androidx.compose.ui.graphics.vector.ImageVector, beschrijving: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(50))
            .background(FreshKeepTheme.kleuren.kaart),
    ) {
        Icon(icoon, contentDescription = beschrijving, tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ZoekBalk(term: String, onZoek: (String) -> Unit) {
    TextField(
        value = term,
        onValueChange = onZoek,
        placeholder = { Text("Zoeken") },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            if (term.isNotEmpty()) {
                IconButton(onClick = { onZoek("") }) { Icon(Icons.Rounded.Close, contentDescription = "Wissen") }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = FreshKeepTheme.kleuren.veld,
            unfocusedContainerColor = FreshKeepTheme.kleuren.veld,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun LocatieFilter(gekozen: Locatie?, onKies: (Locatie?) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Pil("Alle", gekozen == null) { onKies(null) }
        Locatie.entries.forEach { loc -> Pil(loc.label, gekozen == loc) { onKies(if (gekozen == loc) null else loc) } }
    }
}

@Composable
private fun Pil(tekst: String, actief: Boolean, onClick: () -> Unit) {
    val achtergrond by animateColorAsState(
        if (actief) MaterialTheme.colorScheme.primary else FreshKeepTheme.kleuren.kaart, label = "pil",
    )
    Text(
        tekst,
        style = MaterialTheme.typography.titleSmall,
        color = if (actief) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(achtergrond)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    )
}

/** Rij met vegen: naar rechts = Op, naar links = Weggegooid. */
@Composable
private fun VoorraadRij(
    rij: LijstItem,
    onTik: () -> Unit,
    onOp: () -> Unit,
    onWeggegooid: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val swipe = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()
    SwipeToDismissBox(
        state = swipe,
        modifier = modifier,
        backgroundContent = { VeegAchtergrond(swipe.dismissDirection) },
        onDismiss = { richting ->
            when (richting) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    // Bij aantal > 1 blijft het item staan: kaart terugzetten.
                    if (rij.item.aantal > 1) scope.launch { swipe.reset() }
                    onOp()
                }
                SwipeToDismissBoxValue.EndToStart -> onWeggegooid()
                SwipeToDismissBoxValue.Settled -> Unit
            }
        },
    ) {
        Kaart(onClick = onTik, contentPadding = PaddingValues(12.dp)) {
            RijInhoud(rij)
        }
    }
}

@Composable
private fun VeegAchtergrond(richting: SwipeToDismissBoxValue) {
    val kleuren = FreshKeepTheme.kleuren
    val (kleur, icoon, tekst, uitlijning) = when (richting) {
        SwipeToDismissBoxValue.StartToEnd -> Quad(kleuren.groen, Icons.Outlined.CheckCircle, "Op", Alignment.CenterStart)
        SwipeToDismissBoxValue.EndToStart -> Quad(kleuren.rood, Icons.Outlined.DeleteOutline, "Weggegooid", Alignment.CenterEnd)
        SwipeToDismissBoxValue.Settled -> Quad(Color.Transparent, null, "", Alignment.Center)
    }
    val schaal by animateFloatAsState(if (richting == SwipeToDismissBoxValue.Settled) 0.7f else 1f, label = "veeg")
    Box(
        Modifier
            .fillMaxSize()
            .clip(MaterialTheme.shapes.large)
            .background(kleur)
            .padding(horizontal = 24.dp),
        contentAlignment = uitlijning,
    ) {
        if (icoon != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.scale(schaal)) {
                Icon(icoon, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text(tekst, color = Color.White, style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

@Composable
private fun RijInhoud(rij: LijstItem) {
    val item = rij.item
    Row(verticalAlignment = Alignment.CenterVertically) {
        ProductFoto(rij.weergave.fotoUrl)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.naam, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(item.merk, item.locatie.label).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val extra = listOfNotNull(
                if (item.aantal > 1) "${item.aantal} stuks" else null,
                if (item.geopend) "geopend" else null,
            )
            if (extra.isNotEmpty()) {
                Text(
                    extra.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            StatusLabel(rij.dagen)
            Spacer(Modifier.height(4.dp))
            Text(
                Vervalrekenaar.formatKort(item.vervaldatum),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DetailInhoud(
    rij: LijstItem,
    onOp: () -> Unit,
    onWeggegooid: () -> Unit,
    onAanpassen: () -> Unit,
) {
    val item: VoorraadItem = rij.item
    val kleuren = FreshKeepTheme.kleuren
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProductFoto(rij.weergave.fotoUrl, grootte = 72.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(item.naam, style = MaterialTheme.typography.headlineSmall)
                Text(
                    listOfNotNull(item.merk, item.locatie.label, if (item.aantal > 1) "${item.aantal} stuks" else null).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusLabel(rij.dagen)
            Spacer(Modifier.width(8.dp))
            Text(
                "${item.type.label.takeIf { item.type != com.freshkeep.app.domain.model.DatumType.ONBEKEND }?.plus(" ") ?: ""}${Vervalrekenaar.formatLang(item.vervaldatum)}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (item.geopend && item.geopendOp != null) {
            Spacer(Modifier.height(6.dp))
            Text("Geopend op ${Vervalrekenaar.formatKort(item.geopendOp)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!item.notitie.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            Row {
                Icon(Icons.AutoMirrored.Outlined.Notes, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(item.notitie, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(24.dp))
        GroteKnop(
            tekst = if (item.aantal > 1) "Op (1 van ${item.aantal})" else "Op",
            onClick = onOp,
            icoon = Icons.Outlined.CheckCircle,
            kleur = kleuren.groen,
            tekstKleur = Color.White,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GroteKnop(
                tekst = "Weggegooid",
                onClick = onWeggegooid,
                icoon = Icons.Outlined.DeleteOutline,
                kleur = kleuren.rood.copy(alpha = 0.14f),
                tekstKleur = kleuren.roodTekst,
                modifier = Modifier.weight(1f),
            )
            GroteKnop(
                tekst = "Aanpassen",
                onClick = onAanpassen,
                icoon = Icons.Outlined.Edit,
                kleur = kleuren.veld,
                tekstKleur = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
