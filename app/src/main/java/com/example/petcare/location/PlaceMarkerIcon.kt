package com.example.petcare.location

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.core.content.ContextCompat
import com.example.petcare.R
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory

/** Draws category-specific map pins without a marker image dependency. */
object PlaceMarkerIcon {
    fun create(context: Context, type: String): BitmapDescriptor {
        val symbol = when {
            type.contains("clinic", true) -> R.string.marker_clinic_symbol
            type.contains("groom", true) -> R.string.marker_grooming_symbol
            type.contains("park", true) -> R.string.marker_park_symbol
            type.contains("supply", true) || type.contains("store", true) ->
                R.string.marker_supply_symbol
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
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }
}
