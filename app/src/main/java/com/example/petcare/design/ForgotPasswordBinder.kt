package com.example.petcare.design

import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout
import com.example.petcare.R
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/** Implement by calling your existing account store. */
interface AccountRecovery {
    /** The security question for this email, or null if no account uses it. */
    suspend fun securityQuestionFor(email: String): String?

    /** Compare with AccountSecurity.checkAnswer(answer, storedAnswerHash). */
    suspend fun checkSecurityAnswer(email: String, answer: String): Boolean

    /** Save AccountSecurity.hashPassword(newPassword) for this account. */
    suspend fun resetPassword(email: String, newPassword: CharArray)
    fun onBackToLogIn(email: String)
    fun onBack()
}

/**
 * Binds pc_fragment_forgot_password.xml: find account, answer the security question,
 * choose a new password. Wrong answers are throttled like log-in attempts.
 */
class ForgotPasswordBinder(
    root: View,
    private val lifecycleOwner: LifecycleOwner,
    private val actions: AccountRecovery,
    prefillEmail: String = "",
) {
    private val context = root.context
    private val stepBar: SegmentedBarView = root.findViewById(R.id.pcResetSteps)
    private val stepLabel: TextView = root.findViewById(R.id.pcResetStepLabel)
    private val stepViews = listOf<View>(
        root.findViewById(R.id.pcResetStepEmail),
        root.findViewById(R.id.pcResetStepAnswer),
        root.findViewById(R.id.pcResetStepPassword),
        root.findViewById(R.id.pcResetDone),
    )
    private val emailLayout: TextInputLayout = root.findViewById(R.id.pcResetEmailLayout)
    private val email: EditText = root.findViewById(R.id.pcResetEmail)
    private val emailNext: MaterialButton = root.findViewById(R.id.pcResetEmailNext)
    private val question: TextView = root.findViewById(R.id.pcResetQuestion)
    private val answerLayout: TextInputLayout = root.findViewById(R.id.pcResetAnswerLayout)
    private val answer: EditText = root.findViewById(R.id.pcResetAnswer)
    private val answerNext: MaterialButton = root.findViewById(R.id.pcResetAnswerNext)
    private val passwordLayout: TextInputLayout = root.findViewById(R.id.pcResetPasswordLayout)
    private val password: EditText = root.findViewById(R.id.pcResetPassword)
    private val confirmLayout: TextInputLayout = root.findViewById(R.id.pcResetConfirmLayout)
    private val confirm: EditText = root.findViewById(R.id.pcResetConfirm)
    private val save: MaterialButton = root.findViewById(R.id.pcResetSave)
    private val strength = PasswordStrengthBinder(root.findViewById(R.id.pcResetStrength), password) { listOf(account) }

    private var step = STEP_EMAIL
    private var account = ""
    private var busy = false

    init {
        root.applySystemBarPaddingWithKeyboard()
        listOf(emailLayout, answerLayout, passwordLayout, confirmLayout).forEach { it.clearErrorOnEdit() }
        email.setText(prefillEmail)
        emailNext.setOnClickListener { findAccount() }
        answerNext.setOnClickListener { verifyAnswer() }
        save.setOnClickListener { savePassword() }
        root.findViewById<View>(R.id.pcResetBackToLogin).setOnClickListener { actions.onBackToLogIn(account) }
        root.findViewById<View>(R.id.pcResetBack).setOnClickListener { handleBack() }
        show(STEP_EMAIL)
    }

    /** Call from your fragment's back handler too. Returns true if it moved back a step. */
    fun handleBack(): Boolean = when (step) {
        STEP_ANSWER -> {
            show(STEP_EMAIL)
            true
        }
        STEP_DONE -> {
            actions.onBackToLogIn(account)
            true
        }
        else -> {
            actions.onBack()
            false
        }
    }

    private fun findAccount() {
        if (busy) return
        val address = email.value()
        if (!isValidEmail(address)) {
            emailLayout.error = context.getString(R.string.pc_err_email)
            return
        }
        run(emailNext, R.string.pc_checking, R.string.pc_continue) {
            val found = actions.securityQuestionFor(address)
            if (found == null) {
                emailLayout.error = context.getString(R.string.pc_err_no_account)
            } else {
                account = address
                question.text = found
                answer.text?.clear()
                show(STEP_ANSWER)
            }
        }
    }

    private fun verifyAnswer() {
        if (busy) return
        val throttle = LoginThrottle(context, "reset:$account")
        val locked = throttle.remainingLockMillis()
        if (locked > 0L) {
            answerLayout.error = context.getString(R.string.pc_locked, formatLock(locked))
            return
        }
        val typed = answer.text?.toString().orEmpty()
        if (AccountSecurity.normalizeAnswer(typed).length < 2) {
            answerLayout.error = context.getString(R.string.pc_err_answer)
            return
        }
        run(answerNext, R.string.pc_checking, R.string.pc_verify) {
            if (actions.checkSecurityAnswer(account, typed)) {
                throttle.recordSuccess()
                password.text?.clear()
                confirm.text?.clear()
                show(STEP_PASSWORD)
            } else {
                val lockMs = throttle.recordFailure()
                answerLayout.error = if (lockMs > 0L) {
                    context.getString(R.string.pc_locked, formatLock(lockMs))
                } else {
                    val left = throttle.attemptsBeforeLock()
                    val wrong = context.getString(R.string.pc_err_wrong_answer)
                    if (left in 1..2) wrong + ". " + context.resources.getQuantityString(R.plurals.pc_attempts_left, left, left) else wrong
                }
            }
        }
    }

    private fun savePassword() {
        if (busy) return
        strength.update()
        if (!strength.current.acceptable) {
            passwordLayout.error = context.getString(R.string.pc_err_password_weak)
            return
        }
        if (confirm.text?.toString() != password.text?.toString()) {
            confirmLayout.error = context.getString(R.string.pc_err_password_match)
            return
        }
        val secret = password.passwordChars()
        run(save, R.string.pc_saving, R.string.pc_save_password) {
            try {
                actions.resetPassword(account, secret)
            } finally {
                secret.fill('\u0000')
            }
            // A successful reset also clears any log-in lock on this account.
            LoginThrottle(context, account).recordSuccess()
            InboxStore.get(context).add(
                type = InboxType.ACCOUNT,
                title = context.getString(R.string.pc_inbox_password_changed_title),
                body = context.getString(R.string.pc_inbox_password_changed_body),
            )
            password.text?.clear()
            confirm.text?.clear()
            Haptics.confirm(save)
            show(STEP_DONE)
        }
    }

    /** Runs [block] with the button showing a busy label; failures show a generic error. */
    private fun run(button: MaterialButton, busyText: Int, idleText: Int, block: suspend () -> Unit) {
        busy = true
        button.setBusy(true, busyText, idleText)
        lifecycleOwner.lifecycleScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                currentLayout()?.error = e.message ?: context.getString(R.string.pc_err_generic)
            } finally {
                busy = false
                button.setBusy(false, busyText, idleText)
            }
        }
    }

    private fun currentLayout(): TextInputLayout? = when (step) {
        STEP_EMAIL -> emailLayout
        STEP_ANSWER -> answerLayout
        STEP_PASSWORD -> passwordLayout
        else -> null
    }

    private fun show(newStep: Int) {
        step = newStep
        stepViews.forEachIndexed { index, view -> view.visibility = if (index == newStep) View.VISIBLE else View.GONE }
        val done = newStep == STEP_DONE
        val color = ContextCompat.getColor(context, if (done) R.color.pc_success else R.color.pc_primary)
        stepBar.setEqualSegments(List(STEP_COUNT) { index -> if (done || index <= newStep) color else null }, animate = false)
        stepLabel.visibility = if (done) View.GONE else View.VISIBLE
        stepLabel.text = context.getString(R.string.pc_step_of, newStep + 1, STEP_COUNT)
    }

    private companion object {
        const val STEP_EMAIL = 0
        const val STEP_ANSWER = 1
        const val STEP_PASSWORD = 2
        const val STEP_DONE = 3
        const val STEP_COUNT = 3
    }
}
