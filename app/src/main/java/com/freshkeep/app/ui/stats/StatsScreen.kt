package com.freshkeep.app.ui.stats

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.freshkeep.app.data.local.ProductTelling
import com.freshkeep.app.data.repository.StatistiekRepository
import com.freshkeep.app.domain.MaandStat
import com.freshkeep.app.domain.Statistiek
import com.freshkeep.app.domain.Vervalrekenaar
import com.freshkeep.app.ui.components.Bovenbalk
import com.freshkeep.app.ui.components.Kaart
import com.freshkeep.app.ui.components.LeegScherm
import com.freshkeep.app.ui.components.SectieTitel
import com.freshkeep.app.ui.theme.FreshKeepTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.format.TextStyle
import javax.inject.Inject

data class StatsUiState(
    val laden: Boolean = true,
    val maanden: List<MaandStat> = emptyList(),
    val top: List<ProductTelling> = emptyList(),
) {
    val gered: Int get() = maanden.sumOf { it.gered }
    val weggegooid: Int get() = maanden.sumOf { it.weggegooid }
    val percentage: Int? get() = Statistiek.percentageGered(gered, weggegooid)
    val isLeeg: Boolean get() = !laden && gered == 0 && weggegooid == 0 && top.isEmpty()
}

@HiltViewModel
class StatsViewModel @Inject constructor(repo: StatistiekRepository) : ViewModel() {
    val state: StateFlow<StatsUiState> = combine(repo.perMaand(), repo.topWeggegooid()) { maanden, top ->
        StatsUiState(laden = false, maanden = maanden, top = top)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())
}

@Composable
fun StatsScreen(onTerug: () -> Unit, viewModel: StatsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        Bovenbalk("Statistieken", onTerug)
        if (state.isLeeg) {
            LeegScherm(
                icoon = Icons.Outlined.Insights,
                titel = "Nog geen cijfers",
                uitleg = "Markeer producten als \"Op\" of \"Weggegooid\". Hier zie je dan hoeveel je redt.",
            )
            return@Column
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
        ) {
            Kopcijfer(state)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tegel("Gered", state.gered, Icons.Outlined.CheckCircle, FreshKeepTheme.kleuren.groenTekst, Modifier.weight(1f))
                Tegel("Weggegooid", state.weggegooid, Icons.Outlined.DeleteOutline, FreshKeepTheme.kleuren.roodTekst, Modifier.weight(1f))
            }
            SectieTitel("Per maand")
            Kaart(Modifier.fillMaxWidth()) {
                Legenda()
                Spacer(Modifier.height(16.dp))
                StaafGrafiek(state.maanden)
            }
            SectieTitel("Vaakst weggegooid")
            Kaart(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                if (state.top.isEmpty()) {
                    Text(
                        "Nog niets weggegooid. Proficiat!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
                state.top.forEachIndexed { index, telling ->
                    if (index > 0) HorizontalDivider(color = FreshKeepTheme.kleuren.scheiding)
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${index + 1}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(28.dp))
                        Text(telling.naam, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text("${telling.totaal}×", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Kopcijfer(state: StatsUiState) {
    Kaart(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Eco, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    state.percentage?.let { "$it% gered" } ?: "—",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    "van alles wat je de laatste 6 maanden opmaakte of weggooide",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Tegel(label: String, waarde: Int, icoon: ImageVector, kleur: Color, modifier: Modifier) {
    Kaart(modifier) {
        Icon(icoon, contentDescription = null, tint = kleur)
        Spacer(Modifier.height(8.dp))
        Text("$waarde", style = MaterialTheme.typography.headlineMedium)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Legenda() {
    val k = FreshKeepTheme.kleuren
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendaItem("Gered", Icons.Outlined.CheckCircle, k.groen, gearceerd = false)
        LegendaItem("Weggegooid", Icons.Outlined.DeleteOutline, k.roodTekst, gearceerd = true)
    }
}

@Composable
private fun LegendaItem(tekst: String, icoon: ImageVector, kleur: Color, gearceerd: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Staaf(fractie = 1f, kleur = kleur, gearceerd = gearceerd, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(6.dp))
        Icon(icoon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(tekst, style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * Gegroepeerde staven per maand. Weggegooid is donkerder én gearceerd, zodat het ook zonder
 * kleur (kleurenblindheid, grijswaarden) te onderscheiden is. Waarden staan boven de staven.
 */
@Composable
private fun StaafGrafiek(maanden: List<MaandStat>, hoogte: Dp = 160.dp) {
    val k = FreshKeepTheme.kleuren
    val max = (maanden.maxOfOrNull { maxOf(it.gered, it.weggegooid) } ?: 0).coerceAtLeast(1)
    val groei = remember { Animatable(0f) }
    LaunchedEffect(maanden) { groei.snapTo(0f); groei.animateTo(1f, tween(700)) }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        maanden.forEach { m ->
            val naam = m.maand.month.getDisplayName(TextStyle.SHORT, Vervalrekenaar.NEDERLANDS).trimEnd('.')
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f).semantics {
                    contentDescription = "$naam: ${m.gered} gered, ${m.weggegooid} weggegooid"
                },
            ) {
                Row(Modifier.height(hoogte + 18.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    StaafMetWaarde(m.gered, m.gered / max.toFloat() * groei.value, k.groen, false, hoogte)
                    StaafMetWaarde(m.weggegooid, m.weggegooid / max.toFloat() * groei.value, k.roodTekst, true, hoogte)
                }
                Spacer(Modifier.height(6.dp))
                Text(naam, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun StaafMetWaarde(waarde: Int, fractie: Float, kleur: Color, gearceerd: Boolean, hoogte: Dp) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom, modifier = Modifier.fillMaxHeight()) {
        if (waarde > 0) {
            Text("$waarde", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
        }
        Staaf(fractie, kleur, gearceerd, Modifier.width(14.dp).height((hoogte * fractie).coerceAtLeast(if (waarde > 0) 3.dp else 0.dp)))
    }
}

/** Eén staaf met afgeronde bovenkant (4dp) en optionele 45°-arcering. */
@Composable
private fun Staaf(fractie: Float, kleur: Color, gearceerd: Boolean, modifier: Modifier) {
    if (fractie <= 0f) return
    val arcering = if (FreshKeepTheme.kleuren.isDonker) Color.Black.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.45f)
    Canvas(modifier.clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))) {
        drawRect(kleur)
        if (gearceerd) {
            val stap = 5.dp.toPx()
            var x = -size.height
            while (x < size.width) {
                drawLine(arcering, Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = 1.5.dp.toPx())
                x += stap
            }
        }
    }
}
