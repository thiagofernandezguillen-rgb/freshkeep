package com.freshkeep.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.freshkeep.app.domain.Vervalrekenaar
import com.freshkeep.app.ui.theme.FreshKeepTheme

/** Afgeronde witte kaart met een heel subtiele schaduw (Apple-stijl). */
@Composable
fun Kaart(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val kleuren = FreshKeepTheme.kleuren
    val vorm = MaterialTheme.shapes.large
    Column(
        modifier = modifier
            .shadow(
                elevation = if (kleuren.isDonker) 0.dp else 10.dp,
                shape = vorm,
                ambientColor = Color.Black.copy(alpha = 0.04f),
                spotColor = Color.Black.copy(alpha = 0.10f),
            )
            .clip(vorm)
            .background(kleuren.kaart)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
    ) {
        // Zonder Surface erboven is de standaard tekstkleur zwart; in donker thema onleesbaar.
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            content()
        }
    }
}

/** Groot, duimvriendelijk knopje (56dp hoog). */
@Composable
fun GroteKnop(
    tekst: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icoon: ImageVector? = null,
    kleur: Color = MaterialTheme.colorScheme.primary,
    tekstKleur: Color = MaterialTheme.colorScheme.onPrimary,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = kleur, contentColor = tekstKleur),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
    ) {
        if (icoon != null) {
            Icon(icoon, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(tekst, style = MaterialTheme.typography.labelLarge)
    }
}

/** iOS-achtige gesegmenteerde keuze met een meeschuivend "pilletje". */
@Composable
fun Segmenten(
    opties: List<String>,
    geselecteerd: Int,
    onKies: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val kleuren = FreshKeepTheme.kleuren
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(kleuren.veld)
            .padding(3.dp),
    ) {
        val breedte = maxWidth / opties.size
        val offset by animateDpAsState(breedte * geselecteerd, spring(dampingRatio = 0.8f, stiffness = 500f), label = "segment")
        val density = LocalDensity.current
        Box(
            Modifier
                .offset { IntOffset(with(density) { offset.roundToPx() }, 0) }
                .width(breedte)
                .fillMaxHeight()
                .shadow(2.dp, RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
                .background(if (kleuren.isDonker) Color(0xFF636366) else Color.White),
        )
        Row(Modifier.fillMaxSize()) {
            opties.forEachIndexed { index, optie ->
                val actief = index == geselecteerd
                Box(
                    modifier = Modifier
                        .width(breedte)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                        ) { onKies(index) }
                        .semantics { selected = actief },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        optie,
                        style = if (actief) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Gekleurd pilletje met "nog X dagen" / "X dagen verlopen". */
@Composable
fun StatusLabel(dagen: Long, modifier: Modifier = Modifier) {
    val (achtergrond, tekst) = FreshKeepTheme.kleuren.status(Vervalrekenaar.status(dagen))
    val bg by animateColorAsState(achtergrond.copy(alpha = 0.16f), label = "statusbg")
    Text(
        text = Vervalrekenaar.label(dagen),
        style = MaterialTheme.typography.labelMedium,
        color = tekst,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Productfoto (URL of lokaal bestand) met een rustige placeholder. */
@Composable
fun ProductFoto(url: String?, modifier: Modifier = Modifier, grootte: Dp = 56.dp) {
    val vorm = RoundedCornerShape(14.dp)
    val kleuren = FreshKeepTheme.kleuren
    Box(
        modifier = modifier
            .size(grootte)
            .clip(vorm)
            .background(kleuren.veld),
        contentAlignment = Alignment.Center,
    ) {
        val placeholder = @Composable {
            Icon(
                Icons.Outlined.Kitchen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(grootte * 0.45f),
            )
        }
        if (url.isNullOrBlank()) {
            placeholder()
        } else {
            SubcomposeAsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().background(Color.White),
                loading = { placeholder() },
                error = { placeholder() },
            )
        }
    }
}

enum class BannerSoort { INFO, WAARSCHUWING, OFFLINE }

/** Duidelijke melding bovenaan een scherm (geen internet, AI mislukt, ...). */
@Composable
fun InfoBanner(tekst: String, modifier: Modifier = Modifier, soort: BannerSoort = BannerSoort.INFO) {
    val kleuren = FreshKeepTheme.kleuren
    val (icoon, kleur) = when (soort) {
        BannerSoort.INFO -> Icons.Outlined.Info to MaterialTheme.colorScheme.primary
        BannerSoort.WAARSCHUWING -> Icons.Outlined.ErrorOutline to kleuren.oranjeTekst
        BannerSoort.OFFLINE -> Icons.Outlined.WifiOff to kleuren.oranjeTekst
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(kleur.copy(alpha = 0.12f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icoon, contentDescription = null, tint = kleur, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(tekst, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Vriendelijk leeg scherm met icoon, uitleg en optionele knop. */
@Composable
fun LeegScherm(
    icoon: ImageVector,
    titel: String,
    uitleg: String,
    modifier: Modifier = Modifier,
    knopTekst: String? = null,
    knopIcoon: ImageVector? = null,
    onKnop: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            modifier = Modifier.size(96.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icoon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(titel, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            uitleg,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (knopTekst != null && onKnop != null) {
            Spacer(Modifier.height(28.dp))
            GroteKnop(knopTekst, onKnop, icoon = knopIcoon)
        }
    }
}

/** Kop boven een groep instellingen of velden, in iOS-stijl (klein en grijs). */
@Composable
fun SectieTitel(tekst: String, modifier: Modifier = Modifier) {
    Text(
        tekst.uppercase(Vervalrekenaar.NEDERLANDS),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 16.dp, bottom = 6.dp, top = 20.dp),
    )
}
