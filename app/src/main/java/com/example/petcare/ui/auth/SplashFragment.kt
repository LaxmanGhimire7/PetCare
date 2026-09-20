package com.example.petcare.ui.auth

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.petcare.R
import com.example.petcare.data.local.AuthPreferences
import com.example.petcare.data.local.user.AuthRepository
import com.example.petcare.ui.onboarding.OnboardingPrefs
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/** Routes after the AndroidX splash animation without adding a second waiting screen. */
class SplashFragment : Fragment(R.layout.fragment_splash) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewLifecycleOwner.lifecycleScope.launch {
            val ownerId = AuthRepository(requireContext()).bootstrap()
            val destination = when {
                ownerId <= 0 -> R.id.action_splash_to_login
                !OnboardingPrefs(requireContext()).isComplete(ownerId) ->
                    R.id.action_splash_to_onboarding
                else -> R.id.action_splash_to_home
            }
            findNavController().navigate(destination)
        }
    }
}
