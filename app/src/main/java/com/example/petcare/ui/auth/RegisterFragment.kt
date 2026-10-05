package com.example.petcare.ui.auth

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.petcare.BuildConfig
import com.example.petcare.R
import com.example.petcare.data.local.user.AuthRepository
import com.example.petcare.design.RegisterActions
import com.example.petcare.design.RegisterForm
import com.example.petcare.design.RegisterResult
import com.example.petcare.design.RegisterScreenBinder
import com.example.petcare.design.GoogleAuthFlow
import com.example.petcare.design.GoogleSignInClient

/** V3 account creation with PBKDF2 storage and account recovery. */
class RegisterFragment : Fragment(R.layout.pc_fragment_register), RegisterActions {
    private val repository by lazy { AuthRepository(requireContext()) }
    private val google by lazy { GoogleSignInClient(BuildConfig.GOOGLE_WEB_CLIENT_ID) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binder = RegisterScreenBinder(view, viewLifecycleOwner, this)
        GoogleAuthFlow(google, viewLifecycleOwner,
            onAccount = { account -> repository.signInWithGoogle(account.email, account.displayName) },
            onSignedIn = {
                Toast.makeText(requireContext(), R.string.login_success, Toast.LENGTH_SHORT).show()
                findNavController().navigate(R.id.action_register_to_home)
            },
        ).attach(binder.googleButton, binder.googleDivider)
    }

    override suspend fun register(form: RegisterForm): RegisterResult =
        if (
            repository.register(
                form.name,
                form.email,
                form.phone,
                form.password,
                form.securityQuestion,
                form.securityAnswer,
            )
        ) {
            RegisterResult.Success
        } else {
            RegisterResult.EmailTaken
        }

    override fun onRegistered() {
        Toast.makeText(requireContext(), R.string.registration_success, Toast.LENGTH_SHORT).show()
        findNavController().navigate(R.id.action_register_to_home)
    }

    override fun onLogIn() {
        findNavController().navigate(R.id.action_register_to_login)
    }

    override fun onBack() {
        findNavController().navigateUp()
    }
}
