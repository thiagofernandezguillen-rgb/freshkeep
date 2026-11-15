package com.freshkeep.app.util

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Trilling + korte toon bij een geslaagde scan. */
object Feedback {

    fun scanGelukt(context: Context) {
        tril(context)
        runCatching {
            val toon = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70)
            toon.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
            // ToneGenerator na de toon vrijgeven.
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ toon.release() }, 250)
        }
    }

    fun tril(context: Context, duurMs: Long = 60) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        if (vibrator?.hasVibrator() == true) {
            vibrator.vibrate(VibrationEffect.createOneShot(duurMs, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
}
