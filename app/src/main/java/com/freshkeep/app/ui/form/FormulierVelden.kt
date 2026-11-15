package com.freshkeep.app.ui.form

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freshkeep.app.domain.Vervalrekenaar
import com.freshkeep.app.domain.model.DatumType
import com.freshkeep.app.domain.model.Locatie
import com.freshkeep.app.ui.components.Kaart
import com.freshkeep.app.ui.components.Segmenten
import com.freshkeep.app.ui.components.SectieTitel
import com.freshkeep.app.ui.components.StatusLabel
import com.freshkeep.app.ui.theme.FreshKeepTheme
import java.time.LocalDate

private const val DAG_MS = 24L * 60 * 60 * 1000

/** Grote weergave van de vervaldatum; één tik opent de datumkiezer. */
@Composable
fun DatumKaart(
    datum: LocalDate?,
    type: DatumType,
    zekerheid: Double?,
    onTik: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Kaart(modifier = modifier.fillMaxWidth(), onClick = onTik, contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (type == DatumType.ONBEKEND) "Vervaldatum" else "Vervaldatum · ${type.label}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Outlined.Edit, contentDescription = "Datum aanpassen", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(8.dp))
        if (datum == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Tik om een datum te kiezen", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            }
        } else {
            Text(
                Vervalrekenaar.formatLang(datum),
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 30.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusLabel(Vervalrekenaar.dagenTot(datum))
                if (zekerheid != null) {
                    Text(
                        "Gelezen door AI · ${(zekerheid * 100).toInt()}% zeker",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (type != DatumType.ONBEKEND) {
                Spacer(Modifier.height(8.dp))
                Text(type.uitleg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Velden onder de datum: naam (optioneel), locatie, aantal, notitie, geopend. */
@Composable
fun FormulierVelden(
    formulier: ItemFormulier,
    onWijzig: (ItemFormulier) -> Unit,
    onGeopend: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    toonNaam: Boolean = true,
) {
    Column(modifier) {
        if (toonNaam) {
            SectieTitel("Product")
            Kaart(Modifier.fillMaxWidth()) {
                TekstVeld("Naam", formulier.naam, { onWijzig(formulier.copy(naam = it)) })
                Spacer(Modifier.height(8.dp))
                TekstVeld("Merk (optioneel)", formulier.merk, { onWijzig(formulier.copy(merk = it)) })
            }
        }

        SectieTitel("Locatie")
        Segmenten(
            opties = Locatie.entries.map { it.label },
            geselecteerd = formulier.locatie.ordinal,
            onKies = { onWijzig(formulier.copy(locatie = Locatie.entries[it], advies = null)) },
        )

        SectieTitel("Details")
        Kaart(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Aantal", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                AantalStepper(formulier.aantal, { onWijzig(formulier.copy(aantal = it)) })
            }
            HorizontalDivider(color = FreshKeepTheme.kleuren.scheiding)
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Geopend", style = MaterialTheme.typography.bodyLarge)
                    if (formulier.geopend && formulier.geopendOp != null) {
                        Text(
                            "sinds ${Vervalrekenaar.formatKort(formulier.geopendOp)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Switch(
                    checked = formulier.geopend,
                    onCheckedChange = onGeopend,
                    colors = SwitchDefaults.colors(checkedTrackColor = FreshKeepTheme.kleuren.groen),
                )
            }
        }

        AnimatedVisibility(visible = formulier.adviesBezig || formulier.advies != null) {
            AdviesKaart(formulier, onToepassen = { advies ->
                onWijzig(formulier.copy(vervaldatum = advies.datum, advies = null, zekerheid = null))
            })
        }

        SectieTitel("Notitie")
        Kaart(Modifier.fillMaxWidth()) {
            TekstVeld("Bv. halve pot, voor zondag…", formulier.notitie, { onWijzig(formulier.copy(notitie = it)) }, enkeleRegel = false)
        }
    }
}

@Composable
private fun AdviesKaart(formulier: ItemFormulier, onToepassen: (GeopendAdvies) -> Unit) {
    Column(Modifier.padding(top = 12.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (formulier.adviesBezig) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text("Houdbaarheid na openen opzoeken…", style = MaterialTheme.typography.bodyMedium)
            } else {
                val advies = formulier.advies ?: return@Row
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Na openen nog ${advies.dagen} ${if (advies.dagen == 1) "dag" else "dagen"} goed",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        "Voorstel: ${Vervalrekenaar.formatKort(advies.datum)}" + if (advies.doorAi) " (AI)" else " (vuistregel)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { onToepassen(advies) }) { Text("Gebruik") }
            }
        }
    }
}

@Composable
fun TekstVeld(label: String, waarde: String, onWijzig: (String) -> Unit, enkeleRegel: Boolean = true) {
    OutlinedTextField(
        value = waarde,
        onValueChange = onWijzig,
        placeholder = { Text(label) },
        singleLine = enkeleRegel,
        minLines = if (enkeleRegel) 1 else 2,
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
            focusedContainerColor = FreshKeepTheme.kleuren.veld,
            unfocusedContainerColor = FreshKeepTheme.kleuren.veld,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** - aantal + met grote, ronde knoppen. */
@Composable
fun AantalStepper(aantal: Int, onWijzig: (Int) -> Unit, min: Int = 1, max: Int = 99) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = { onWijzig((aantal - 1).coerceAtLeast(min)) }, enabled = aantal > min, shape = CircleShape) {
            Icon(Icons.Rounded.Remove, contentDescription = "Eén minder")
        }
        Text(
            "$aantal",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 40.dp),
        )
        FilledTonalIconButton(onClick = { onWijzig((aantal + 1).coerceAtMost(max)) }, enabled = aantal < max, shape = CircleShape) {
            Icon(Icons.Rounded.Add, contentDescription = "Eén meer")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatumKiezerDialoog(
    initieel: LocalDate?,
    onKies: (LocalDate) -> Unit,
    onSluit: () -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = (initieel ?: LocalDate.now()).toEpochDay() * DAG_MS,
    )
    DatePickerDialog(
        onDismissRequest = onSluit,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { onKies(LocalDate.ofEpochDay(it / DAG_MS)) }
                    onSluit()
                },
                enabled = state.selectedDateMillis != null,
            ) { Text("Kies datum") }
        },
        dismissButton = { TextButton(onClick = onSluit) { Text("Annuleren") } },
    ) {
        DatePicker(
            state = state,
            title = { Text("Vervaldatum", modifier = Modifier.padding(start = 24.dp, top = 16.dp)) },
        )
    }
}
