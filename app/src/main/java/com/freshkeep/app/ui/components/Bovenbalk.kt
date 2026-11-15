package com.freshkeep.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Eenvoudige iOS-achtige navigatiebalk: "‹ Terug" links, titel in het midden, acties rechts. */
@Composable
fun Bovenbalk(
    titel: String,
    onTerug: (() -> Unit)?,
    modifier: Modifier = Modifier,
    terugTekst: String = "Terug",
    acties: @Composable RowScope.() -> Unit = {},
) {
    Box(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(52.dp)
            .padding(horizontal = 4.dp),
    ) {
        if (onTerug != null) {
            TextButton(onClick = onTerug, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBackIos, contentDescription = null, modifier = Modifier.padding(end = 2.dp))
                Text(terugTekst, style = MaterialTheme.typography.bodyLarge)
            }
        }
        Text(
            titel,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.align(Alignment.Center),
            maxLines = 1,
        )
        Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically, content = acties)
    }
}
