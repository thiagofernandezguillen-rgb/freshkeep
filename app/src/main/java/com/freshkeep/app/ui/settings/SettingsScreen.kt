package com.freshkeep.app.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.freshkeep.app.BuildConfig
import com.freshkeep.app.data.repository.AiRepository
import com.freshkeep.app.data.repository.BackupRepository
import com.freshkeep.app.data.settings.Instellingen
import com.freshkeep.app.data.settings.InstellingenRepository
import com.freshkeep.app.domain.model.Locatie
import com.freshkeep.app.domain.model.Thema
import com.freshkeep.app.notifications.MeldingPlanner
import com.freshkeep.app.ui.components.Bovenbalk
import com.freshkeep.app.ui.components.Kaart
import com.freshkeep.app.ui.components.SectieTitel
import com.freshkeep.app.ui.components.Segmenten
import com.freshkeep.app.ui.form.AantalStepper
import com.freshkeep.app.ui.theme.FreshKeepTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: InstellingenRepository,
    private val planner: MeldingPlanner,
    private val backup: BackupRepository,
    ai: AiRepository,
) : ViewModel() {
    val instellingen: StateFlow<Instellingen> =
        repo.instellingen.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Instellingen())
    val aiIngesteld = ai.isIngesteld

    private val _berichten = Channel<String>(Channel.BUFFERED)
    val berichten = _berichten.receiveAsFlow()

    fun zetTijd(uur: Int, minuut: Int) = viewModelScope.launch {
        repo.zetMeldingTijd(uur, minuut)
        planner.planAlles(vervang = true)
        _berichten.send("Dagelijkse melding om %02d:%02d".format(uur, minuut))
    }

    fun zetDagen(dagen: Int) = viewModelScope.launch { repo.zetDagenVooraf(dagen) }
    fun zetLocatie(locatie: Locatie) = viewModelScope.launch { repo.zetStandaardLocatie(locatie) }
    fun zetThema(thema: Thema) = viewModelScope.launch { repo.zetThema(thema) }

    fun testMelding() {
        planner.stuurNu()
        viewModelScope.launch { _berichten.send("Overzicht wordt verstuurd (als er iets binnenkort vervalt).") }
    }

    fun exporteer(uri: Uri) = viewModelScope.launch {
        _berichten.send(
            try {
                val aantal = backup.exporteer(uri)
                "Back-up bewaard ($aantal producten)."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "Back-up mislukt: ${e.message}"
            },
        )
    }

    fun importeer(uri: Uri) = viewModelScope.launch {
        _berichten.send(
            try {
                val r = backup.importeer(uri)
                "Back-up teruggezet: ${r.voorraad} producten, ${r.geschiedenis} gebeurtenissen."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.message ?: "Terugzetten mislukt."
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onTerug: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val s by viewModel.instellingen.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var tijdDialoog by rememberSaveable { mutableStateOf(false) }
    var importUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(viewModel) { viewModel.berichten.collect { snackbar.showSnackbar(it) } }

    // Toestemming voor meldingen: status opnieuw lezen bij terugkeer naar de app.
    var meldingenAan by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) meldingenAan = NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    val vraagToestemming = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        meldingenAan = ok
        if (!ok) {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(viewModel::exporteer)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> importUri = uri }

    Scaffold(
        topBar = { Bovenbalk("Instellingen", onTerug) },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
        ) {
            SectieTitel("Meldingen")
            Kaart(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                if (!meldingenAan) {
                    Rij(Icons.Outlined.NotificationsOff, "Meldingen staan uit", "Tik om ze aan te zetten") {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            vraagToestemming.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    }
                    Scheiding()
                }
                Rij(Icons.Outlined.Schedule, "Dagelijkse melding", "%02d:%02d".format(s.meldingUur, s.meldingMinuut)) { tijdDialoog = true }
                Scheiding()
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Dagen op voorhand", style = MaterialTheme.typography.bodyLarge)
                        Text("${s.dagenVooraf} ${if (s.dagenVooraf == 1) "dag" else "dagen"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    AantalStepper(s.dagenVooraf, viewModel::zetDagen, min = 1, max = 14)
                }
                Scheiding()
                Rij(Icons.Outlined.NotificationsActive, "Stuur overzicht nu", "Om de melding te testen", onClick = viewModel::testMelding)
            }
            Text(
                "Je krijgt één overzicht per dag, en 's ochtends een extra melding voor wat vandaag vervalt.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )

            SectieTitel("Standaardlocatie")
            Segmenten(Locatie.entries.map { it.label }, s.standaardLocatie.ordinal, { viewModel.zetLocatie(Locatie.entries[it]) })

            SectieTitel("Thema")
            Segmenten(Thema.entries.map { it.label }, s.thema.ordinal, { viewModel.zetThema(Thema.entries[it]) })

            SectieTitel("Back-up")
            Kaart(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                Rij(Icons.Outlined.Upload, "Back-up exporteren", "Als JSON-bestand") {
                    exportLauncher.launch("freshkeep-backup-${LocalDate.now()}.json")
                }
                Scheiding()
                Rij(Icons.Outlined.Download, "Back-up importeren", "Vervangt alle huidige gegevens") {
                    importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                }
            }

            SectieTitel("Over")
            Kaart(Modifier.fillMaxWidth()) {
                Text("FreshKeep ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (viewModel.aiIngesteld) "AI-dienst verbonden" else "AI-dienst niet ingesteld: datums kies je zelf",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Productgegevens: Open Food Facts (ODbL). Foto's worden niet bewaard op de server.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (tijdDialoog) {
        val tijd = rememberTimePickerState(initialHour = s.meldingUur, initialMinute = s.meldingMinuut, is24Hour = true)
        AlertDialog(
            onDismissRequest = { tijdDialoog = false },
            title = { Text("Dagelijkse melding") },
            text = { TimePicker(state = tijd) },
            confirmButton = {
                TextButton(onClick = { tijdDialoog = false; viewModel.zetTijd(tijd.hour, tijd.minute) }) { Text("Bewaren") }
            },
            dismissButton = { TextButton(onClick = { tijdDialoog = false }) { Text("Annuleren") } },
        )
    }

    importUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { importUri = null },
            title = { Text("Back-up terugzetten?") },
            text = { Text("Alle huidige producten, instellingen van producten en statistieken worden vervangen door de inhoud van de back-up.") },
            confirmButton = {
                TextButton(onClick = { importUri = null; viewModel.importeer(uri) }) {
                    Text("Vervangen", color = FreshKeepTheme.kleuren.roodTekst)
                }
            },
            dismissButton = { TextButton(onClick = { importUri = null }) { Text("Annuleren") } },
        )
    }
}

@Composable
private fun Rij(icoon: ImageVector, titel: String, ondertitel: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icoon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(titel, style = MaterialTheme.typography.bodyLarge)
            if (ondertitel != null) {
                Text(ondertitel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun Scheiding() = HorizontalDivider(color = FreshKeepTheme.kleuren.scheiding, modifier = Modifier.padding(start = 38.dp))
