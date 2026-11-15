package com.freshkeep.app.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.freshkeep.app.ui.add.AddScreen
import com.freshkeep.app.ui.edit.EditScreen
import com.freshkeep.app.ui.home.HomeScreen
import com.freshkeep.app.ui.scan.ScanScreen
import com.freshkeep.app.ui.settings.SettingsScreen
import com.freshkeep.app.ui.stats.StatsScreen
import kotlinx.serialization.Serializable

@Serializable object HomeRoute
@Serializable object ScanRoute
@Serializable data class AddRoute(val barcode: String? = null)
@Serializable data class EditRoute(val id: Long)
@Serializable object StatsRoute
@Serializable object SettingsRoute

const val RESULTAAT_TOEGEVOEGD = "toegevoegd"

@Composable
fun FreshKeepNavHost(openBinnenkortSignaal: Int) {
    val nav = rememberNavController()

    // Tik op een melding: altijd terug naar het startscherm (tab Binnenkort wordt daar gekozen).
    LaunchedEffect(openBinnenkortSignaal) {
        if (openBinnenkortSignaal > 0) nav.popBackStack(HomeRoute, inclusive = false)
    }

    NavHost(
        navController = nav,
        startDestination = HomeRoute,
        enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(320)) + fadeIn(tween(320)) },
        exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(320)) { it / 4 } + fadeOut(tween(200)) },
        popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(320)) { it / 4 } + fadeIn(tween(320)) },
        popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(320)) + fadeOut(tween(200)) },
    ) {
        composable<HomeRoute> { entry ->
            val toegevoegd by entry.savedStateHandle.getStateFlow<String?>(RESULTAAT_TOEGEVOEGD, null)
                .collectAsStateWithLifecycle()
            HomeScreen(
                openBinnenkortSignaal = openBinnenkortSignaal,
                toegevoegd = toegevoegd,
                onToegevoegdGetoond = { entry.savedStateHandle[RESULTAAT_TOEGEVOEGD] = null },
                onScan = { nav.navigate(ScanRoute) },
                onHandmatig = { nav.navigate(AddRoute()) },
                onAanpassen = { nav.navigate(EditRoute(it)) },
                onStatistieken = { nav.navigate(StatsRoute) },
                onInstellingen = { nav.navigate(SettingsRoute) },
            )
        }
        composable<ScanRoute>(
            enterTransition = { slideInVertically(tween(320)) { it } + fadeIn() },
            popExitTransition = { slideOutVertically(tween(320)) { it } + fadeOut() },
        ) {
            ScanScreen(
                onBarcode = { code -> nav.navigate(AddRoute(code)) { popUpTo(ScanRoute) { inclusive = true } } },
                onZonderBarcode = { nav.navigate(AddRoute()) { popUpTo(ScanRoute) { inclusive = true } } },
                onTerug = { nav.popBackStack() },
            )
        }
        composable<AddRoute> {
            AddScreen(
                onKlaar = { naam ->
                    nav.previousBackStackEntry?.savedStateHandle?.set(RESULTAAT_TOEGEVOEGD, naam)
                    nav.popBackStack()
                },
                onTerug = { nav.popBackStack() },
            )
        }
        composable<EditRoute> { EditScreen(onTerug = { nav.popBackStack() }) }
        composable<StatsRoute> { StatsScreen(onTerug = { nav.popBackStack() }) }
        composable<SettingsRoute> { SettingsScreen(onTerug = { nav.popBackStack() }) }
    }
}
