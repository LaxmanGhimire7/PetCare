package com.example.petcare.design

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import android.view.View
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.example.petcare.R
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

sealed interface GoogleSignInResult {
    /** [email] is the Google account address; [idToken] is only needed if you add a server later. */
    data class Success(val email: String, val displayName: String?, val idToken: String, val photoUrl: String?) : GoogleSignInResult
    object Cancelled : GoogleSignInResult
    object NoAccount : GoogleSignInResult
    object NotConfigured : GoogleSignInResult
    data class Failed(val message: String?) : GoogleSignInResult
}

/**
 * "Sign in with Google" through Android's Credential Manager (the current recommended API;
 * the old GoogleSignInClient is deprecated). [webClientId] is the *Web application* OAuth
 * client ID from Google Cloud Console, e.g. BuildConfig.GOOGLE_WEB_CLIENT_ID.
 */
class GoogleSignInClient(private val webClientId: String) {

    suspend fun signIn(activity: Activity): GoogleSignInResult {
        if (webClientId.isBlank() || !webClientId.endsWith(".apps.googleusercontent.com")) {
            return GoogleSignInResult.NotConfigured
        }
        val option = GetSignInWithGoogleOption.Builder(webClientId).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            val credential = CredentialManager.create(activity).getCredential(activity, request).credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val google = GoogleIdTokenCredential.createFrom(credential.data)
                GoogleSignInResult.Success(google.id, google.displayName, google.idToken, google.profilePictureUri?.toString())
            } else {
                GoogleSignInResult.Failed("Unexpected credential type")
            }
        } catch (e: GetCredentialCancellationException) {
            GoogleSignInResult.Cancelled
        } catch (e: NoCredentialException) {
            GoogleSignInResult.NoAccount
        } catch (e: GoogleIdTokenParsingException) {
            GoogleSignInResult.Failed(e.message)
        } catch (e: GetCredentialException) {
            GoogleSignInResult.Failed(e.message)
        }
    }

    /** Call on log-out so the next sign-in asks which account to use. */
    suspend fun signOut(context: Context) {
        try {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't clear Google credential state", e)
        }
    }

    internal companion object {
        const val TAG = "PetCareGoogle"
    }
}

/**
 * Wires a "Continue with Google" button on Welcome, Log in or Create account: shows it,
 * runs the sign-in with a busy state, and reports problems in a Snackbar.
 */
class GoogleAuthFlow(
    private val client: GoogleSignInClient,
    private val lifecycleOwner: LifecycleOwner,
    /** Find or create the local account for this Google user and start the session. Return false on failure. */
    private val onAccount: suspend (GoogleSignInResult.Success) -> Boolean,
    private val onSignedIn: () -> Unit,
) {
    /** Shows [button] (and anything in [alsoShow], such as the "or" divider) and handles taps. */
    fun attach(button: MaterialButton, vararg alsoShow: View) {
        button.visibility = View.VISIBLE
        alsoShow.forEach { it.visibility = View.VISIBLE }
        button.setOnClickListener { start(button) }
    }

    private fun start(button: MaterialButton) {
        val activity = button.context.findActivity() ?: return
        button.setBusy(true, R.string.pc_google_connecting, R.string.pc_continue_google)
        lifecycleOwner.lifecycleScope.launch {
            var signedIn = false
            val message: Int? = try {
                when (val result = client.signIn(activity)) {
                    is GoogleSignInResult.Success -> {
                        signedIn = onAccount(result)
                        if (signedIn) null else R.string.pc_err_google_failed
                    }
                    GoogleSignInResult.Cancelled -> null
                    GoogleSignInResult.NoAccount -> R.string.pc_err_google_no_account
                    GoogleSignInResult.NotConfigured -> R.string.pc_err_google_setup
                    is GoogleSignInResult.Failed -> {
                        // The real reason goes to Logcat, e.g. an OAuth client or SHA-1 mismatch.
                        Log.w(GoogleSignInClient.TAG, "Google sign-in failed: ${result.message}")
                        R.string.pc_err_google_failed
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(GoogleSignInClient.TAG, "Google sign-in error", e)
                R.string.pc_err_google_failed
            }
            button.setBusy(false, R.string.pc_google_connecting, R.string.pc_continue_google)
            when {
                signedIn -> {
                    Haptics.confirm(button)
                    onSignedIn()
                }
                message != null -> Snackbar.make(button, message, Snackbar.LENGTH_LONG).show()
            }
        }
    }
}

/** Walks the Context chain to the hosting Activity. */
internal fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
