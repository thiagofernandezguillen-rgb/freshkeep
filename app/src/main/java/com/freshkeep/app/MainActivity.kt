package com.freshkeep.app

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.freshkeep.app.data.settings.InstellingenRepository
import com.freshkeep.app.domain.Vervalrekenaar
import com.freshkeep.app.domain.model.Thema
import com.freshkeep.app.ui.navigation.FreshKeepNavHost
import com.freshkeep.app.ui.theme.FreshKeepTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(instellingen: InstellingenRepository) : ViewModel() {
    val thema: StateFlow<Thema> = instellingen.instellingen.map { it.thema }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Thema.AUTOMATISCH)
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    /** Telt op telkens een melding de tab "Binnenkort" vraagt. */
    private val openBinnenkort = MutableStateFlow(0)

    /** De app is volledig Nederlandstalig: ook datumkiezers en systeemteksten van Compose. */
    override fun attachBaseContext(newBase: Context) {
        Locale.setDefault(Vervalrekenaar.NEDERLANDS)
        val config = Configuration(newBase.resources.configuration).apply { setLocale(Vervalrekenaar.NEDERLANDS) }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        verwerk(intent)
        setContent {
            val thema by viewModel.thema.collectAsStateWithLifecycle()
            val signaal by openBinnenkort.collectAsStateWithLifecycle()
            val donker = when (thema) {
                Thema.AUTOMATISCH -> isSystemInDarkTheme()
                Thema.LICHT -> false
                Thema.DONKER -> true
            }
            DisposableEffect(donker) {
                val stijl = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { donker }
                enableEdgeToEdge(statusBarStyle = stijl, navigationBarStyle = stijl)
                onDispose { }
            }
            FreshKeepTheme(thema) {
                FreshKeepNavHost(openBinnenkortSignaal = signaal)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        verwerk(intent)
    }

    private fun verwerk(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_BINNENKORT, false) == true) {
            openBinnenkort.value += 1
            intent.removeExtra(EXTRA_OPEN_BINNENKORT)
        }
    }

    companion object {
        const val EXTRA_OPEN_BINNENKORT = "open_binnenkort"
    }
}
