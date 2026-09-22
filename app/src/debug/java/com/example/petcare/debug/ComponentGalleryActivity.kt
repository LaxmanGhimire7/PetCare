package com.example.petcare.debug

import android.os.Bundle
import android.os.Build
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.petcare.R
import com.example.petcare.ui.PetAvatarView

/** Debug-only catalogue for physical-device review of the shared XML component system. */
class ComponentGalleryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        setContentView(R.layout.activity_component_gallery)
        findViewById<PetAvatarView>(R.id.gallery_pet_avatar).setPet("Luna", 2, 2, 3, true)
    }
}
