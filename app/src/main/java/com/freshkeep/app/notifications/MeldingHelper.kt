package com.freshkeep.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.freshkeep.app.MainActivity
import com.freshkeep.app.R
import com.freshkeep.app.domain.MeldingTekst
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MeldingHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun maakKanalen() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(KANAAL_OVERZICHT, "Dagelijks overzicht", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "Eén samenvatting per dag van producten die binnenkort vervallen" },
                NotificationChannel(KANAAL_VANDAAG, "Vervalt vandaag", NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = "Melding voor producten die vandaag vervallen" },
            ),
        )
    }

    fun magMelden(): Boolean {
        val toestemming = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return toestemming && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun toonSamenvatting(tekst: MeldingTekst.Tekst) = toon(ID_OVERZICHT, KANAAL_OVERZICHT, tekst)

    fun toonVandaag(tekst: MeldingTekst.Tekst) = toon(ID_VANDAAG, KANAAL_VANDAAG, tekst)

    private fun toon(id: Int, kanaal: String, tekst: MeldingTekst.Tekst) {
        if (!magMelden()) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_OPEN_BINNENKORT, true)
        }
        val pending = PendingIntent.getActivity(
            context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val melding = NotificationCompat.Builder(context, kanaal)
            .setSmallIcon(R.drawable.ic_melding)
            .setColor(0xFF34C759.toInt())
            .setContentTitle(tekst.titel)
            .setContentText(tekst.inhoud)
            .setStyle(NotificationCompat.BigTextStyle().bigText(tekst.inhoud))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, melding)
        } catch (_: SecurityException) {
            // Toestemming net ingetrokken: niets doen.
        }
    }

    companion object {
        const val KANAAL_OVERZICHT = "overzicht"
        const val KANAAL_VANDAAG = "vandaag"
        private const val ID_OVERZICHT = 1001
        private const val ID_VANDAAG = 1002
    }
}
