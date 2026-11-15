package com.freshkeep.app.ui.scan

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FlashlightOff
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.freshkeep.app.data.remote.Netwerkstatus
import com.freshkeep.app.ui.components.BannerSoort
import com.freshkeep.app.ui.components.InfoBanner
import com.freshkeep.app.util.Feedback
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ScanViewModel @Inject constructor(netwerk: Netwerkstatus) : ViewModel() {
    val online: StateFlow<Boolean> = netwerk.online.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
}

/** Geldige EAN-8, UPC-A (12) of EAN-13. UPC-E (6-8 cijfers) wordt ook toegelaten. */
fun isGeldigeBarcode(code: String): Boolean = code.length in 6..14 && code.all(Char::isDigit)

@Composable
fun ScanScreen(
    onBarcode: (String) -> Unit,
    onZonderBarcode: () -> Unit,
    onTerug: () -> Unit,
    viewModel: ScanViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val online by viewModel.online.collectAsStateWithLifecycle()
    var zaklamp by rememberSaveable { mutableStateOf(false) }
    var gevonden by remember { mutableStateOf(false) }
    var typDialoog by rememberSaveable { mutableStateOf(false) }

    val verwerk: (String) -> Unit = { code ->
        if (!gevonden) {
            gevonden = true
            Feedback.scanGelukt(context)
            onBarcode(code)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        CameraToestemming(
            alternatief = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AlternatiefKnop("Barcode typen") { typDialoog = true }
                    AlternatiefKnop("Zonder barcode toevoegen", onZonderBarcode)
                }
            },
        ) {
            BarcodeCamera(zaklamp = zaklamp, onBarcode = verwerk, modifier = Modifier.fillMaxSize())
            ScanVak()
        }

        // Bovenbalk
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlasKnop(Icons.Rounded.Close, "Sluiten", onTerug)
            Spacer(Modifier.weight(1f))
            GlasKnop(if (zaklamp) Icons.Outlined.FlashlightOn else Icons.Outlined.FlashlightOff, "Zaklamp") { zaklamp = !zaklamp }
        }

        // Onderkant
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!online) {
                InfoBanner(
                    "Geen internet. Gekende producten werken gewoon; nieuwe producten voeg je handmatig toe.",
                    soort = BannerSoort.OFFLINE,
                    modifier = Modifier.background(Color.White, RoundedCornerShape(14.dp)),
                )
                Spacer(Modifier.height(12.dp))
            }
            Text(
                "Richt de camera op de barcode",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OnderKnop(Icons.Outlined.Keyboard, "Barcode typen", Modifier.weight(1f)) { typDialoog = true }
                OnderKnop(Icons.Outlined.EditNote, "Zonder barcode", Modifier.weight(1f), onZonderBarcode)
            }
        }
    }

    if (typDialoog) {
        BarcodeTypenDialoog(onSluit = { typDialoog = false }, onZoek = { typDialoog = false; verwerk(it) })
    }
}

/** Donkere overlay met een uitgespaard, afgerond scanvak en een bewegende scanlijn. */
@Composable
private fun ScanVak() {
    val groen = MaterialTheme.colorScheme.primary
    val lijn = rememberInfiniteTransition(label = "scanlijn")
    val positie by lijn.animateFloat(0.1f, 0.9f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "pos")
    Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        val breedte = size.width * 0.78f
        val hoogte = breedte * 0.55f
        val links = (size.width - breedte) / 2
        val boven = (size.height - hoogte) / 2.3f
        val radius = CornerRadius(28.dp.toPx())
        drawRect(Color.Black.copy(alpha = 0.55f))
        drawRoundRect(Color.Transparent, Offset(links, boven), Size(breedte, hoogte), radius, blendMode = BlendMode.Clear)
        drawRoundRect(Color.White, Offset(links, boven), Size(breedte, hoogte), radius, style = Stroke(3.dp.toPx()))
        val y = boven + hoogte * positie
        drawLine(groen, Offset(links + 24.dp.toPx(), y), Offset(links + breedte - 24.dp.toPx(), y), strokeWidth = 3.dp.toPx())
    }
}

@Composable
private fun GlasKnop(icoon: ImageVector, beschrijving: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.45f), contentColor = Color.White),
        shape = CircleShape,
    ) { Icon(icoon, contentDescription = beschrijving) }
}

@Composable
private fun OnderKnop(icoon: ImageVector, tekst: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = modifier.height(56.dp).background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
    ) {
        Icon(icoon, contentDescription = null, tint = Color.White)
        Spacer(Modifier.size(8.dp))
        Text(tekst, color = Color.White, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun BarcodeTypenDialoog(onSluit: () -> Unit, onZoek: (String) -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onSluit,
        title = { Text("Barcode typen") },
        text = {
            OutlinedTextField(
                value = code,
                onValueChange = { nieuw -> code = nieuw.filter(Char::isDigit).take(14) },
                placeholder = { Text("bv. 5410000000000") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(12.dp),
            )
        },
        confirmButton = { TextButton(onClick = { onZoek(code) }, enabled = isGeldigeBarcode(code)) { Text("Zoeken") } },
        dismissButton = { TextButton(onClick = onSluit) { Text("Annuleren") } },
    )
}
