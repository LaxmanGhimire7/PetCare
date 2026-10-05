package com.example.petcare.ui.auth

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.petcare.BuildConfig
import com.example.petcare.R
import com.example.petcare.data.local.user.AuthRepository
import com.example.petcare.design.GoogleAuthFlow
import com.example.petcare.design.GoogleSignInClient
import com.example.petcare.design.WelcomeScreenBinder

/** Signed-out entry point supplied by the v3 kit. */
class WelcomeFragment : Fragment(R.layout.pc_fragment_welcome) {
    private val repository by lazy { AuthRepository(requireContext()) }
    private val google by lazy { GoogleSignInClient(BuildConfig.GOOGLE_WEB_CLIENT_ID) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binder = WelcomeScreenBinder(
            root = view,
            lifecycleOwner = viewLifecycleOwner,
            onCreateAccount = { findNavController().navigate(R.id.action_welcome_to_register) },
            onLogIn = { findNavController().navigate(R.id.action_welcome_to_login) },
        )
        GoogleAuthFlow(google, viewLifecycleOwner,
            onAccount = { account -> repository.signInWithGoogle(account.email, account.displayName) },
            onSignedIn = { findNavController().navigate(R.id.action_welcome_to_home) },
        ).attach(binder.googleButton)
    }
}
