package com.example.petcare.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.petcare.R
import com.example.petcare.data.local.AuthPreferences
import com.example.petcare.databinding.FragmentSplashBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashFragment : Fragment() {

    private var _binding: FragmentSplashBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSplashBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            delay(SPLASH_DURATION_MS)

            if (findNavController().currentDestination?.id == R.id.splashFragment) {
                val destination = if (AuthPreferences(requireContext()).isSignedIn()) {
                    R.id.action_splash_to_home
                } else {
                    R.id.action_splash_to_login
                }
                findNavController().navigate(destination)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val SPLASH_DURATION_MS = 1_500L
    }
}
