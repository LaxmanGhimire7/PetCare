package com.example.petcare.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.util.Patterns
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.petcare.R
import com.example.petcare.data.local.AuthPreferences
import com.example.petcare.databinding.FragmentRegisterBinding

class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.createAccountButton.setOnClickListener {
            if (isValidInput()) {
                register()
            }
        }

        binding.signInInsteadButton.setOnClickListener {
            findNavController().navigate(R.id.action_register_to_login)
        }
        playEntrance()
    }

    private fun playEntrance() {
        binding.authHeader.alpha = 0f
        binding.authHeader.animate().alpha(1f).setDuration(420L).start()
        binding.authFormCard.alpha = 0f
        binding.authFormCard.translationY = 42f
        binding.authFormCard.animate().alpha(1f).translationY(0f)
            .setStartDelay(100L).setDuration(470L).start()
    }

    private fun isValidInput(): Boolean {
        val name = binding.nameInput.text?.toString()?.trim().orEmpty()
        val email = binding.emailInput.text?.toString()?.trim().orEmpty()
        val password = binding.passwordInput.text?.toString().orEmpty()
        val confirmation = binding.confirmPasswordInput.text?.toString().orEmpty()

        binding.nameLayout.error = if (name.isEmpty()) {
            getString(R.string.error_name_required)
        } else {
            null
        }
        binding.emailLayout.error = when {
            email.isEmpty() -> getString(R.string.error_email_required)
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                getString(R.string.error_email_invalid)
            }
            else -> null
        }
        binding.passwordLayout.error = when {
            password.isEmpty() -> getString(R.string.error_password_required)
            password.length < MINIMUM_PASSWORD_LENGTH -> {
                getString(R.string.error_password_short)
            }
            else -> null
        }
        binding.confirmPasswordLayout.error = when {
            confirmation.isEmpty() -> getString(R.string.error_password_confirmation_required)
            confirmation != password -> getString(R.string.error_passwords_do_not_match)
            else -> null
        }

        return binding.nameLayout.error == null &&
            binding.emailLayout.error == null &&
            binding.passwordLayout.error == null &&
            binding.confirmPasswordLayout.error == null
    }

    private fun register() {
        val name = binding.nameInput.text?.toString()?.trim().orEmpty()
        val email = binding.emailInput.text?.toString()?.trim().orEmpty()
        val password = binding.passwordInput.text?.toString().orEmpty()

        if (AuthPreferences(requireContext()).register(name, email, password)) {
            findNavController().navigate(R.id.action_register_to_home)
        } else {
            binding.emailLayout.error = getString(R.string.error_account_already_exists)
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
