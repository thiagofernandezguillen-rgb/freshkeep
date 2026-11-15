package com.freshkeep.app.ui.add

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freshkeep.app.ui.components.Bovenbalk
import com.freshkeep.app.ui.components.GroteKnop
import com.freshkeep.app.ui.components.InfoBanner
import com.freshkeep.app.ui.components.Kaart
import com.freshkeep.app.ui.components.ProductFoto
import com.freshkeep.app.ui.form.DatumKaart
import com.freshkeep.app.ui.form.DatumKiezerDialoog
import com.freshkeep.app.ui.form.FormulierVelden
import com.freshkeep.app.ui.form.TekstVeld
import com.freshkeep.app.ui.scan.AlternatiefKnop
import com.freshkeep.app.ui.scan.CameraToestemming
import com.freshkeep.app.ui.scan.FotoCamera
import com.freshkeep.app.ui.scan.neemFoto
import com.freshkeep.app.ui.scan.rememberFotoController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun AddScreen(
    onKlaar: (String) -> Unit,
    onTerug: () -> Unit,
    viewModel: AddViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) { viewModel.klaar.collect(onKlaar) }

    AnimatedContent(
        targetState = state.stap,
        transitionSpec = {
            (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 } + fadeOut())
        },
        label = "stap",
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) { stap ->
        when (stap) {
            Stap.ZOEKEN -> Laden(state.bezig ?: "Even geduld…", state.barcode, onTerug)
            Stap.PRODUCT_FOTO -> CameraStap(
                titel = "Foto van de verpakking",
                uitleg = "Fotografeer de voorkant, met naam en merk goed zichtbaar.",
                state = state,
                alternatiefTekst = "Zelf typen",
                alternatiefIcoon = Icons.Outlined.Keyboard,
                onAlternatief = viewModel::zelfTypen,
                onFoto = viewModel::productFotoGenomen,
                onFout = viewModel::fotoMislukt,
                onTerug = onTerug,
            )
            Stap.PRODUCT_NAAM -> NaamStap(state, viewModel, onTerug)
            Stap.DATUM_FOTO -> CameraStap(
                titel = "Foto van de vervaldatum",
                uitleg = "Zoek THT, TGT of \"best before\" en houd de datum in het vak.",
                state = state,
                alternatiefTekst = "Datum zelf kiezen",
                alternatiefIcoon = Icons.Outlined.CalendarMonth,
                onAlternatief = viewModel::datumZelfKiezen,
                onFoto = viewModel::datumFotoGenomen,
                onFout = viewModel::fotoMislukt,
                onTerug = onTerug,
            )
            Stap.BEVESTIGEN -> BevestigStap(state, viewModel, onTerug)
        }
    }

    if (state.datumKiezerOpen && state.stap == Stap.BEVESTIGEN) {
        DatumKiezerDialoog(
            initieel = state.formulier.vervaldatum,
            onKies = viewModel::kiesDatum,
            onSluit = viewModel::sluitDatumKiezer,
        )
    }
}

@Composable
private fun Laden(tekst: String, barcode: String?, onTerug: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Bovenbalk("Toevoegen", onTerug, terugTekst = "Annuleren")
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(20.dp))
            Text(tekst, style = MaterialTheme.typography.titleMedium)
            if (barcode != null) {
                Text(barcode, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Camera met kader en grote ontspanknop; gebruikt voor verpakking én datum. */
@Composable
private fun CameraStap(
    titel: String,
    uitleg: String,
    state: AddUiState,
    alternatiefTekst: String,
    alternatiefIcoon: androidx.compose.ui.graphics.vector.ImageVector,
    onAlternatief: () -> Unit,
    onFoto: (ByteArray) -> Unit,
    onFout: () -> Unit,
    onTerug: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = rememberFotoController()
    val bezig = state.bezig != null

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        CameraToestemming(alternatief = { AlternatiefKnop(alternatiefTekst, onAlternatief) }) {
            FotoCamera(controller, zaklamp = false, modifier = Modifier.fillMaxSize())
            FotoKader()
        }

        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onTerug,
                shape = CircleShape,
                colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.45f), contentColor = Color.White),
            ) { Icon(Icons.Rounded.Close, contentDescription = "Annuleren") }
            Spacer(Modifier.width(12.dp))
            if (state.formulier.naam.isNotBlank()) {
                Surface(shape = RoundedCornerShape(50), color = Color.Black.copy(alpha = 0.45f)) {
                    Text(
                        state.formulier.naam,
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            state.melding?.let {
                InfoBanner(it.tekst, soort = it.soort, modifier = Modifier.background(Color.White, RoundedCornerShape(14.dp)))
                Spacer(Modifier.height(12.dp))
            }
            Text(titel, color = Color.White, style = MaterialTheme.typography.titleLarge)
            Text(uitleg, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            // Ontspanknop
            Box(
                Modifier
                    .size(78.dp)
                    .border(BorderStroke(4.dp, Color.White), CircleShape)
                    .padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(
                    onClick = {
                        scope.launch {
                            try {
                                onFoto(controller.neemFoto(context))
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                onFout()
                            }
                        }
                    },
                    enabled = !bezig,
                    modifier = Modifier.fillMaxSize(),
                    colors = IconButtonDefaults.iconButtonColors(containerColor = Color.White, disabledContainerColor = Color.White.copy(alpha = 0.5f)),
                    shape = CircleShape,
                ) {}
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onAlternatief,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.height(52.dp),
            ) {
                Icon(alternatiefIcoon, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text(alternatiefTekst, color = Color.White, style = MaterialTheme.typography.titleSmall)
            }
        }

        AnimatedVisibility(visible = bezig, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(Modifier.height(16.dp))
                    Text(state.bezig.orEmpty(), color = Color.White, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun FotoKader() {
    Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        val breedte = size.width * 0.84f
        val hoogte = breedte * 0.62f
        val links = (size.width - breedte) / 2
        val boven = (size.height - hoogte) / 2.6f
        val radius = CornerRadius(24.dp.toPx())
        drawRect(Color.Black.copy(alpha = 0.45f))
        drawRoundRect(Color.Transparent, Offset(links, boven), Size(breedte, hoogte), radius, blendMode = BlendMode.Clear)
        drawRoundRect(Color.White, Offset(links, boven), Size(breedte, hoogte), radius, style = Stroke(2.dp.toPx()))
    }
}

@Composable
private fun NaamStap(state: AddUiState, viewModel: AddViewModel, onTerug: () -> Unit) {
    val focus = LocalFocusManager.current
    Column(Modifier.fillMaxSize().imePadding()) {
        Bovenbalk("Product", onTerug, terugTekst = "Annuleren")
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            state.melding?.let {
                InfoBanner(it.tekst, soort = it.soort)
                Spacer(Modifier.height(16.dp))
            }
            Text("Welk product is het?", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))
            Kaart(Modifier.fillMaxWidth()) {
                if (state.fotoUrl != null) {
                    ProductFoto(state.fotoUrl, grootte = 88.dp, modifier = Modifier.align(Alignment.CenterHorizontally))
                    Spacer(Modifier.height(12.dp))
                }
                TekstVeld("Naam, bv. Halfvolle melk", state.formulier.naam, { viewModel.wijzig(state.formulier.copy(naam = it)) })
                Spacer(Modifier.height(8.dp))
                TekstVeld("Merk (optioneel)", state.formulier.merk, { viewModel.wijzig(state.formulier.copy(merk = it)) })
            }
            if (state.barcode != null) {
                Text(
                    "Barcode ${state.barcode}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                )
            }
        }
        GroteKnop(
            tekst = "Verder",
            onClick = {
                // Toetsenbord sluiten, anders verschuift de datumkiezer tijdens het tikken.
                focus.clearFocus()
                viewModel.naamBevestigd()
            },
            enabled = state.formulier.naam.isNotBlank(),
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
        )
    }
}

@Composable
private fun BevestigStap(state: AddUiState, viewModel: AddViewModel, onTerug: () -> Unit) {
    val f = state.formulier
    Column(Modifier.fillMaxSize().imePadding()) {
        Bovenbalk("Bevestigen", onTerug, terugTekst = "Annuleren")
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            state.melding?.let {
                InfoBanner(it.tekst, soort = it.soort)
                Spacer(Modifier.height(12.dp))
            }
            Kaart(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProductFoto(state.fotoUrl)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(f.naam, style = MaterialTheme.typography.titleMedium)
                        val details = listOfNotNull(f.merk.ifBlank { null }, state.hoeveelheid).joinToString(" · ")
                        if (details.isNotEmpty()) {
                            Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            DatumKaart(f.vervaldatum, f.type, f.zekerheid, onTik = viewModel::openDatumKiezer)
            FormulierVelden(
                formulier = f,
                onWijzig = viewModel::wijzig,
                onGeopend = viewModel::zetGeopend,
                toonNaam = false,
            )
            Spacer(Modifier.height(24.dp))
        }
        GroteKnop(
            tekst = "Toevoegen",
            onClick = viewModel::opslaan,
            icoon = Icons.Rounded.Check,
            enabled = f.naam.isNotBlank() && state.bezig == null,
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
        )
    }
}
