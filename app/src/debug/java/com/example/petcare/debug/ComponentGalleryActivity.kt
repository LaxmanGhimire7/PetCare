package com.example.petcare.debug

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.example.petcare.R
import com.example.petcare.ui.PetAvatarView

/** Debug-only catalogue for physical-device review of the shared XML component system. */
class ComponentGalleryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_component_gallery)
        findViewById<PetAvatarView>(R.id.gallery_pet_avatar).setPet("Luna", 2, 2, 3, true)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.gallery_root)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }
}
