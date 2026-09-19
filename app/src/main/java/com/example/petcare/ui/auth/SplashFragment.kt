package com.example.petcare.ui.auth

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.petcare.R
import com.example.petcare.data.local.AuthPreferences

/** Routes after the AndroidX splash animation without adding a second waiting screen. */
class SplashFragment : Fragment(R.layout.fragment_splash) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val destination = if (AuthPreferences(requireContext()).isSignedIn()) {
            R.id.action_splash_to_home
        } else {
            R.id.action_splash_to_login
        }
        findNavController().navigate(destination)
    }
}
