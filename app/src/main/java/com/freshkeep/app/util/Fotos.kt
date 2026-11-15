package com.freshkeep.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

object Fotos {

    /** Max 1024 px aan de langste zijde, zoals de backend verwacht. */
    const val MAX_ZIJDE = 1024

    /** Draait volgens de sensor, verkleint en comprimeert tot JPEG. */
    fun naarJpeg(bron: Bitmap, rotatie: Int, maxZijde: Int = MAX_ZIJDE, kwaliteit: Int = 85): ByteArray {
        val schaal = (maxZijde.toFloat() / max(bron.width, bron.height)).coerceAtMost(1f)
        val matrix = Matrix().apply {
            postScale(schaal, schaal)
            if (rotatie != 0) postRotate(rotatie.toFloat())
        }
        val klein = Bitmap.createBitmap(bron, 0, 0, bron.width, bron.height, matrix, true)
        return ByteArrayOutputStream().use { out ->
            klein.compress(Bitmap.CompressFormat.JPEG, kwaliteit, out)
            if (klein !== bron) klein.recycle()
            out.toByteArray()
        }
    }

    /** Berekent de nieuwe afmetingen (handig voor tests). */
    fun doelGrootte(breedte: Int, hoogte: Int, maxZijde: Int = MAX_ZIJDE): Pair<Int, Int> {
        val schaal = (maxZijde.toFloat() / max(breedte, hoogte)).coerceAtMost(1f)
        return (breedte * schaal).roundToInt() to (hoogte * schaal).roundToInt()
    }

    /** Bewaart een verpakkingsfoto lokaal als productfoto en geeft de file-URI terug. */
    fun bewaarProductFoto(context: Context, barcode: String, jpeg: ByteArray): String {
        val map = File(context.filesDir, "productfotos").apply { mkdirs() }
        val veiligeNaam = barcode.filter { it.isLetterOrDigit() }.ifEmpty { "product" }
        val bestand = File(map, "$veiligeNaam.jpg")
        bestand.writeBytes(jpeg)
        return bestand.toURI().toString()
    }
}
