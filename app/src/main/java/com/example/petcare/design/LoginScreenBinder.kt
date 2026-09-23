package com.example.petcare.design

import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout
import com.example.petcare.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

sealed interface SignInResult {
    object Success : SignInResult
    object WrongCredentials : SignInResult
    data class Failed(val message: String?) : SignInResult
}

/** Implement by calling your existing account store. */
interface LoginActions {
    /**
     * Look up the account and compare with AccountSecurity.checkPassword(password, stored).
     * On MATCH_NEEDS_UPGRADE, save AccountSecurity.hashPassword(password) before returning Success.
     * Return WrongCredentials both for an unknown email and a wrong password.
     */
    suspend fun signIn(email: String, password: CharArray): SignInResult
    fun onSignedIn()
    fun onForgotPassword(email: String)
    fun onCreateAccount()
    fun onBack()
    fun onBiometric() = Unit
}

/** Binds pc_fragment_login.xml, including lockout after repeated failures. */
class LoginScreenBinder(
    root: View,
    private val lifecycleOwner: LifecycleOwner,
    private val actions: LoginActions,
    showBiometric: Boolean = false,
) {
    private val context = root.context
    private val emailLayout: TextInputLayout = root.findViewById(R.id.pcLoginEmailLayout)
    private val passwordLayout: TextInputLayout = root.findViewById(R.id.pcLoginPasswordLayout)
    private val email: EditText = root.findViewById(R.id.pcLoginEmail)
    private val password: EditText = root.findViewById(R.id.pcLoginPassword)
    private val submit: MaterialButton = root.findViewById(R.id.pcLoginSubmit)
    private val lockBanner: View = root.findViewById(R.id.pcLoginLock)
    private val lockText: TextView = root.findViewById(R.id.pcLoginLockText)
    private var busy = false

    init {
        root.applySystemBarPaddingWithKeyboard()
        emailLayout.clearErrorOnEdit()
        passwordLayout.clearErrorOnEdit()
        email.afterTextChanged { refreshLock() }
        password.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit()
                true
            } else {
                false
            }
        }
        submit.setOnClickListener { submit() }
        root.findViewById<View>(R.id.pcLoginForgot).setOnClickListener { actions.onForgotPassword(email.value()) }
        root.findViewById<View>(R.id.pcLoginCreate).setOnClickListener { actions.onCreateAccount() }
        root.findViewById<View>(R.id.pcLoginBack).setOnClickListener { actions.onBack() }

        val biometric = root.findViewById<View>(R.id.pcLoginBiometric)
        biometric.visibility = if (showBiometric) View.VISIBLE else View.GONE
        biometric.setOnClickListener { actions.onBiometric() }

        // Keeps the lock countdown current while the screen is visible.
        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    refreshLock()
                    delay(1_000L)
                }
            }
        }
    }

    /** Pre-fills the email, e.g. after a password reset. */
    fun prefill(address: String) {
        email.setText(address)
        password.requestFocus()
    }

    private fun submit() {
        if (busy) return
        val address = email.value()
        if (!isValidEmail(address)) {
            emailLayout.error = context.getString(R.string.pc_err_email)
            return
        }
        val throttle = LoginThrottle(context, address)
        if (throttle.remainingLockMillis() > 0L) {
            refreshLock()
            return
        }
        val secret = password.passwordChars()
        if (secret.isEmpty()) {
            passwordLayout.error = context.getString(R.string.pc_err_password_required)
            return
        }

        setBusy(true)
        lifecycleOwner.lifecycleScope.launch {
            val result = try {
                actions.signIn(address, secret)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SignInResult.Failed(e.message)
            } finally {
                secret.fill('\u0000')
            }
            setBusy(false)
            when (result) {
                SignInResult.Success -> {
                    throttle.recordSuccess()
                    Haptics.confirm(submit)
                    actions.onSignedIn()
                }
                SignInResult.WrongCredentials -> {
                    val lockMs = throttle.recordFailure()
                    password.text?.clear()
                    if (lockMs > 0L) {
                        refreshLock()
                    } else {
                        val left = throttle.attemptsBeforeLock()
                        val wrong = context.getString(R.string.pc_err_wrong_credentials)
                        passwordLayout.error = if (left in 1..2) {
                            wrong + ". " + context.resources.getQuantityString(R.plurals.pc_attempts_left, left, left)
                        } else {
                            wrong
                        }
                    }
                }
                is SignInResult.Failed -> {
                    passwordLayout.error = result.message ?: context.getString(R.string.pc_err_generic)
                }
            }
        }
    }

    private fun refreshLock() {
        val address = email.value()
        val remaining = if (isValidEmail(address)) LoginThrottle(context, address).remainingLockMillis() else 0L
        if (remaining > 0L) {
            lockBanner.visibility = View.VISIBLE
            lockText.text = context.getString(R.string.pc_locked, formatLock(remaining))
            submit.isEnabled = false
        } else {
            lockBanner.visibility = View.GONE
            submit.isEnabled = !busy
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        submit.setBusy(value, R.string.pc_logging_in, R.string.pc_log_in)
        email.isEnabled = !value
        password.isEnabled = !value
    }
}
