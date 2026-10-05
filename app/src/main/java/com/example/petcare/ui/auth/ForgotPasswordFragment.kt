package com.example.petcare.ui.auth

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.petcare.R
import com.example.petcare.data.local.user.AuthRepository
import com.example.petcare.design.AccountRecovery
import com.example.petcare.design.ForgotPasswordBinder

/** Three-step v3 password recovery flow. */
class ForgotPasswordFragment : Fragment(R.layout.pc_fragment_forgot_password), AccountRecovery {
    private val repository by lazy { AuthRepository(requireContext()) }
    private var binder: ForgotPasswordBinder? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binder = ForgotPasswordBinder(
            root = view,
            lifecycleOwner = viewLifecycleOwner,
            actions = this,
            prefillEmail = arguments?.getString(LoginFragment.ARG_EMAIL).orEmpty(),
        )
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() { binder?.handleBack() }
            },
        )
    }

    override suspend fun securityQuestionFor(email: String): String? = repository.securityQuestionFor(email)

    override suspend fun checkSecurityAnswer(email: String, answer: String): Boolean =
        repository.checkSecurityAnswer(email, answer)

    override suspend fun resetPassword(email: String, newPassword: CharArray) {
        repository.resetPassword(email, newPassword)
    }

    override fun onBackToLogIn(email: String) {
        findNavController().navigate(
            R.id.action_forgot_password_to_login,
            Bundle().apply { putString(LoginFragment.ARG_EMAIL, email) },
        )
    }

    override fun onBack() { findNavController().navigateUp() }

    override fun onDestroyView() {
        binder = null
        super.onDestroyView()
    }
}
