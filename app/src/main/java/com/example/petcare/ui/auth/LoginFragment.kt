package com.example.petcare.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.petcare.R
import com.example.petcare.data.local.AuthPreferences
import com.example.petcare.databinding.FragmentLoginBinding
import android.util.Patterns

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.signInButton.setOnClickListener {
            if (isValidInput()) {
                signIn()
            }
        }

        binding.signUpButton.setOnClickListener {
            findNavController().navigate(com.example.petcare.R.id.action_login_to_register)
        }
        playEntrance()
    }

    private fun playEntrance() {
        binding.authHeader.alpha = 0f
        binding.authHeader.animate().alpha(1f).setDuration(420L).start()
        binding.authFormCard.alpha = 0f
        binding.authFormCard.translationY = 42f
        binding.authFormCard.scaleX = 0.98f
        binding.authFormCard.scaleY = 0.98f
        binding.authFormCard.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
            .setStartDelay(110L).setDuration(480L).start()
    }

    private fun isValidInput(): Boolean {
        val email = binding.emailInput.text?.toString()?.trim().orEmpty()
        val password = binding.passwordInput.text?.toString().orEmpty()

        binding.emailLayout.error = when {
            email.isEmpty() -> getString(com.example.petcare.R.string.error_email_required)
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                getString(com.example.petcare.R.string.error_email_invalid)
            }
            else -> null
        }
        binding.passwordLayout.error = when {
            password.isEmpty() -> getString(com.example.petcare.R.string.error_password_required)
            password.length < MINIMUM_PASSWORD_LENGTH -> {
                getString(com.example.petcare.R.string.error_password_short)
            }
            else -> null
        }

        return binding.emailLayout.error == null && binding.passwordLayout.error == null
    }

    private fun signIn() {
        val email = binding.emailInput.text?.toString().orEmpty()
        val password = binding.passwordInput.text?.toString().orEmpty()

        if (AuthPreferences(requireContext()).signIn(email, password)) {
            findNavController().navigate(R.id.action_login_to_home)
        } else {
            binding.passwordLayout.error = getString(R.string.error_invalid_credentials)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val MINIMUM_PASSWORD_LENGTH = 6
    }
}
