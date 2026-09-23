package com.example.petcare.ui.auth

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.petcare.R
import com.example.petcare.design.WelcomeScreenBinder

/** Signed-out entry point supplied by the v3 kit. */
class WelcomeFragment : Fragment(R.layout.pc_fragment_welcome) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        WelcomeScreenBinder(
            root = view,
            lifecycleOwner = viewLifecycleOwner,
            onCreateAccount = { findNavController().navigate(R.id.action_welcome_to_register) },
            onLogIn = { findNavController().navigate(R.id.action_welcome_to_login) },
        )
    }
}
