package com.freshkeep.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freshkeep.app.domain.Houdbaarheid
import com.freshkeep.app.domain.model.Thema

/** Kleuren die Material 3 niet kent: statuskleuren en de iOS-achtige grijstinten. */
@Immutable
data class FreshKeepKleuren(
    val groen: Color,
    val geel: Color,
    val oranje: Color,
    val rood: Color,
    val groenTekst: Color,
    val geelTekst: Color,
    val oranjeTekst: Color,
    val roodTekst: Color,
    val kaart: Color,
    val veld: Color,
    val scheiding: Color,
    val isDonker: Boolean,
) {
    /** Achtergrond- en tekstkleur voor het statuslabel. */
    fun status(h: Houdbaarheid): Pair<Color, Color> = when (h) {
        Houdbaarheid.GOED -> groen to groenTekst
        Houdbaarheid.BINNENKORT -> geel to geelTekst
        Houdbaarheid.DRINGEND -> oranje to oranjeTekst
        Houdbaarheid.VERLOPEN -> rood to roodTekst
    }
}

private val LichtKleuren = FreshKeepKleuren(
    groen = Color(0xFF34C759), geel = Color(0xFFFFCC00), oranje = Color(0xFFFF9500), rood = Color(0xFFFF3B30),
    groenTekst = Color(0xFF1E7E34), geelTekst = Color(0xFF8A6A00), oranjeTekst = Color(0xFFB45F00), roodTekst = Color(0xFFD70015),
    kaart = Color.White, veld = Color(0xFFE9E9EE), scheiding = Color(0x1F3C3C43), isDonker = false,
)

private val DonkerKleuren = FreshKeepKleuren(
    groen = Color(0xFF30D158), geel = Color(0xFFFFD60A), oranje = Color(0xFFFF9F0A), rood = Color(0xFFFF453A),
    groenTekst = Color(0xFF30D158), geelTekst = Color(0xFFFFD60A), oranjeTekst = Color(0xFFFF9F0A), roodTekst = Color(0xFFFF6961),
    kaart = Color(0xFF1C1C1E), veld = Color(0xFF2C2C2E), scheiding = Color(0x33545458), isDonker = true,
)

private val LichtSchema: ColorScheme = lightColorScheme(
    primary = Color(0xFF1E8E3E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8F5DF),
    onPrimaryContainer = Color(0xFF0B3D19),
    secondary = Color(0xFF6C6C70),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE9E9EE),
    onSecondaryContainer = Color(0xFF1C1C1E),
    background = Color(0xFFF2F2F7),
    onBackground = Color(0xFF1C1C1E),
    surface = Color(0xFFF2F2F7),
    onSurface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFFE9E9EE),
    onSurfaceVariant = Color(0xFF6C6C70),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color(0xFFE9E9EE),
    outline = Color(0xFFC7C7CC),
    outlineVariant = Color(0xFFE5E5EA),
    error = Color(0xFFD70015),
    onError = Color.White,
    errorContainer = Color(0xFFFFE5E3),
    onErrorContainer = Color(0xFF8A0010),
    inverseSurface = Color(0xFF2C2C2E),
    inverseOnSurface = Color.White,
    inversePrimary = Color(0xFF30D158),
)

private val DonkerSchema: ColorScheme = darkColorScheme(
    primary = Color(0xFF30D158),
    onPrimary = Color(0xFF00210B),
    primaryContainer = Color(0xFF14421F),
    onPrimaryContainer = Color(0xFFB8F2C6),
    secondary = Color(0xFF98989F),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF2C2C2E),
    onSecondaryContainer = Color.White,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2C2C2E),
    onSurfaceVariant = Color(0xFF98989F),
    surfaceContainerLowest = Color(0xFF1C1C1E),
    surfaceContainerLow = Color(0xFF1C1C1E),
    surfaceContainer = Color(0xFF1C1C1E),
    surfaceContainerHigh = Color(0xFF2C2C2E),
    surfaceContainerHighest = Color(0xFF3A3A3C),
    outline = Color(0xFF545458),
    outlineVariant = Color(0xFF38383A),
    error = Color(0xFFFF6961),
    onError = Color.Black,
    errorContainer = Color(0xFF4A1210),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFF2F2F7),
    inverseOnSurface = Color(0xFF1C1C1E),
    inversePrimary = Color(0xFF1E8E3E),
)

/** Typografie in de geest van Apple's Human Interface Guidelines. */
private val FreshKeepTypografie = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Medium),
)

private val FreshKeepVormen = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

val LocalFreshKeepKleuren = staticCompositionLocalOf { LichtKleuren }

/** Snelle toegang: `FreshKeepTheme.kleuren.groen`. */
object FreshKeepTheme {
    val kleuren: FreshKeepKleuren
        @Composable get() = LocalFreshKeepKleuren.current
}

@Composable
fun FreshKeepTheme(
    thema: Thema = Thema.AUTOMATISCH,
    content: @Composable () -> Unit,
) {
    val donker = when (thema) {
        Thema.AUTOMATISCH -> isSystemInDarkTheme()
        Thema.LICHT -> false
        Thema.DONKER -> true
    }
    val schema = if (donker) DonkerSchema else LichtSchema
    CompositionLocalProvider(LocalFreshKeepKleuren provides if (donker) DonkerKleuren else LichtKleuren) {
        MaterialTheme(
            colorScheme = schema,
            typography = FreshKeepTypografie,
            shapes = FreshKeepVormen,
        ) {
            // Standaard tekstkleur voor schermen zonder Scaffold/Surface (anders zwart, onleesbaar in donker thema).
            CompositionLocalProvider(LocalContentColor provides schema.onBackground, content = content)
        }
    }
}
