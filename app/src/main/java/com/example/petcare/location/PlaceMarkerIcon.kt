package com.example.petcare.location

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.core.content.ContextCompat
import com.example.petcare.R

/** Draws category-specific map pins without a marker image dependency. */
object PlaceMarkerIcon {
    /** Larger, plain destination pin for an address searched on the map. */
    fun createSearch(context: Context): Bitmap {
        val size = (context.resources.getDimensionPixelSize(R.dimen.place_marker_size) * 1.4f).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val center = size / 2f
        val outerTail = Path().apply {
            moveTo(center - size * 0.22f, size * 0.58f)
            lineTo(center, size * 0.99f)
            lineTo(center + size * 0.22f, size * 0.58f)
            close()
        }
        paint.color = ContextCompat.getColor(context, R.color.on_primary)
        canvas.drawPath(outerTail, paint)
        canvas.drawCircle(center, size * 0.38f, size * 0.37f, paint)
        val innerTail = Path().apply {
            moveTo(center - size * 0.16f, size * 0.57f)
            lineTo(center, size * 0.91f)
            lineTo(center + size * 0.16f, size * 0.57f)
            close()
        }
        paint.color = ContextCompat.getColor(context, R.color.primary)
        canvas.drawPath(innerTail, paint)
        canvas.drawCircle(center, size * 0.38f, size * 0.31f, paint)
        paint.color = ContextCompat.getColor(context, R.color.on_primary)
        canvas.drawCircle(center, size * 0.38f, size * 0.12f, paint)
        return bitmap
    }

    fun keyFor(type: String): String = when {
        type.contains("clinic", true) -> "clinic"
        type.contains("groom", true) -> "grooming"
        type.contains("park", true) -> "park"
        type.contains("supply", true) || type.contains("store", true) -> "supply"
        else -> "shelter"
    }

    fun create(context: Context, type: String): Bitmap {
        val symbol = when (keyFor(type)) {
            "clinic" -> R.string.marker_clinic_symbol
            "grooming" -> R.string.marker_grooming_symbol
            "park" -> R.string.marker_park_symbol
            "supply" -> R.string.marker_supply_symbol
            else -> R.string.marker_shelter_symbol
        }
        val size = context.resources.getDimensionPixelSize(R.dimen.place_marker_size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.primary)
        }
        val center = size / 2f
        val circleRadius = size * 0.36f
        canvas.drawCircle(center, size * 0.39f, circleRadius, paint)
        val tail = Path().apply {
            moveTo(center - size * 0.17f, size * 0.61f)
            lineTo(center, size * 0.96f)
            lineTo(center + size * 0.17f, size * 0.61f)
            close()
        }
        canvas.drawPath(tail, paint)
        paint.color = ContextCompat.getColor(context, R.color.on_primary)
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = context.resources.getDimension(R.dimen.place_marker_letter)
        paint.isFakeBoldText = true
        val baseline = size * 0.39f - (paint.ascent() + paint.descent()) / 2f
        canvas.drawText(context.getString(symbol), center, baseline, paint)
        return bitmap
    }
}
