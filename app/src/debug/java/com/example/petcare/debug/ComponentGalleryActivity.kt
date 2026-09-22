package com.example.petcare.debug

import android.os.Bundle
import android.os.Build
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.example.petcare.R
import com.example.petcare.ui.PetAvatarView
import com.example.petcare.ui.home.SegmentedProgressView
import com.example.petcare.data.local.pet.PetColor

/** Debug-only catalogue for physical-device review of the shared XML component system. */
class ComponentGalleryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        delegate.localNightMode = if (intent.getBooleanExtra(EXTRA_LIGHT, false))
            AppCompatDelegate.MODE_NIGHT_NO else AppCompatDelegate.MODE_NIGHT_YES
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        setContentView(R.layout.activity_component_gallery)
        findViewById<PetAvatarView>(R.id.gallery_pet_avatar).setPet(
            getString(R.string.gallery_luna), 1, 2, 3, true)
        findViewById<SegmentedProgressView>(R.id.gallery_segments).setSegments(listOf(
            PetColor.SKY.color(this) to true,
            PetColor.VIOLET.color(this) to true,
            PetColor.SKY.color(this) to false,
            PetColor.VIOLET.color(this) to false
        ))
    }

    private companion object { const val EXTRA_LIGHT = "light" }
}
