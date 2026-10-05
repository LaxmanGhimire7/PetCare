package com.example.petcare.ui.auth

import android.os.Bundle
import android.view.View
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.petcare.BuildConfig
import com.example.petcare.R
import com.example.petcare.data.local.AuthPreferences
import com.example.petcare.data.local.BiometricPreferences
import com.example.petcare.data.local.user.AuthRepository
import com.example.petcare.design.LoginActions
import com.example.petcare.design.LoginScreenBinder
import com.example.petcare.design.GoogleAuthFlow
import com.example.petcare.design.GoogleSignInClient
import com.example.petcare.design.SignInResult
import com.example.petcare.ui.UiSnackbar
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/** V3 login UI backed by the existing local account and biometric session. */
class LoginFragment : Fragment(R.layout.pc_fragment_login), LoginActions {
    private val repository by lazy { AuthRepository(requireContext()) }
    private val auth by lazy { AuthPreferences(requireContext()) }
    private val biometric by lazy { BiometricPreferences(requireContext()) }
    private val google by lazy { GoogleSignInClient(BuildConfig.GOOGLE_WEB_CLIENT_ID) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val biometricId = biometric.enabledUserId()
        val canUseBiometric = biometricId > 0 && BiometricManager.from(requireContext()).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL,
        ) == BiometricManager.BIOMETRIC_SUCCESS
        val binder = LoginScreenBinder(view, viewLifecycleOwner, this, canUseBiometric)
        binder.prefill(arguments?.getString(ARG_EMAIL).orEmpty())
        GoogleAuthFlow(google, viewLifecycleOwner,
            onAccount = { account -> repository.signInWithGoogle(account.email, account.displayName) },
            onSignedIn = { onSignedIn() },
        ).attach(binder.googleButton, binder.googleDivider)
    }

    override suspend fun signIn(email: String, password: CharArray): SignInResult =
        if (repository.usesGoogle(email)) {
            SignInResult.UsesGoogle
        } else if (repository.signIn(email, password, auth.staySignedIn())) {
            SignInResult.Success
        } else {
            SignInResult.WrongCredentials
        }

    override fun onSignedIn() {
        findNavController().navigate(R.id.action_login_to_home)
    }

    override fun onForgotPassword(email: String) {
        findNavController().navigate(
            R.id.action_login_to_forgot_password,
            Bundle().apply { putString(ARG_EMAIL, email) },
        )
    }

    override fun onCreateAccount() {
        findNavController().navigate(R.id.action_login_to_register)
    }

    override fun onBack() {
        findNavController().navigateUp()
    }

    override fun onBiometric() {
        val userId = biometric.enabledUserId()
        if (userId <= 0) return
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(requireContext()),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        if (repository.unlockWithBiometric(userId)) onSignedIn()
                        else view?.let {
                            UiSnackbar.make(it, R.string.biometric_account_unavailable, Snackbar.LENGTH_LONG).show()
                        }
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                    ) {
                        view?.let { UiSnackbar.make(it, R.string.biometric_failed, Snackbar.LENGTH_LONG).show() }
                    }
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.biometric_unlock))
                .setSubtitle(getString(R.string.biometric_prompt_subtitle))
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_WEAK or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                )
                .build(),
        )
    }

    companion object { const val ARG_EMAIL = "email" }
}
