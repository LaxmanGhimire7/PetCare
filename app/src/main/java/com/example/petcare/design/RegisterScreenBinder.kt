package com.example.petcare.design

import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout
import com.example.petcare.R
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * What the user entered. [password] is a CharArray and is wiped after [RegisterActions.register]
 * returns, so hash it inside register() with AccountSecurity.hashPassword(password).
 * Store AccountSecurity.hashAnswer(securityAnswer), never the answer itself.
 */
class RegisterForm(
    val name: String,
    val email: String,
    val phone: String?,
    val password: CharArray,
    val securityQuestion: String,
    val securityAnswer: String,
)

sealed interface RegisterResult {
    object Success : RegisterResult
    object EmailTaken : RegisterResult
    data class Failed(val message: String?) : RegisterResult
}

interface RegisterActions {
    suspend fun register(form: RegisterForm): RegisterResult
    fun onRegistered()
    fun onLogIn()
    fun onBack()
}

/** Binds pc_fragment_register.xml with inline validation and a live password meter. */
class RegisterScreenBinder(
    root: View,
    private val lifecycleOwner: LifecycleOwner,
    private val actions: RegisterActions,
) {
    private val context = root.context
    private val nameLayout: TextInputLayout = root.findViewById(R.id.pcRegisterNameLayout)
    private val emailLayout: TextInputLayout = root.findViewById(R.id.pcRegisterEmailLayout)
    private val passwordLayout: TextInputLayout = root.findViewById(R.id.pcRegisterPasswordLayout)
    private val confirmLayout: TextInputLayout = root.findViewById(R.id.pcRegisterConfirmLayout)
    private val questionLayout: TextInputLayout = root.findViewById(R.id.pcRegisterQuestionLayout)
    private val answerLayout: TextInputLayout = root.findViewById(R.id.pcRegisterAnswerLayout)
    private val name: EditText = root.findViewById(R.id.pcRegisterName)
    private val email: EditText = root.findViewById(R.id.pcRegisterEmail)
    private val phone: EditText = root.findViewById(R.id.pcRegisterPhone)
    private val password: EditText = root.findViewById(R.id.pcRegisterPassword)
    private val confirm: EditText = root.findViewById(R.id.pcRegisterConfirm)
    private val question: MaterialAutoCompleteTextView = root.findViewById(R.id.pcRegisterQuestion)
    private val answer: EditText = root.findViewById(R.id.pcRegisterAnswer)
    private val submit: MaterialButton = root.findViewById(R.id.pcRegisterSubmit)
    private val strength = PasswordStrengthBinder(
        root.findViewById(R.id.pcRegisterStrength),
        password,
    ) { listOf(name.value(), email.value()) }
    private var busy = false

    init {
        root.applySystemBarPaddingWithKeyboard()
        listOf(nameLayout, emailLayout, passwordLayout, confirmLayout, answerLayout).forEach { it.clearErrorOnEdit() }
        name.afterTextChanged { strength.update() }
        email.afterTextChanged { strength.update() }

        val questions = context.resources.getStringArray(R.array.pc_security_questions).toList()
        question.setAdapter(ArrayAdapter(context, R.layout.pc_item_dropdown, questions))
        question.setOnItemClickListener { _, _, _, _ -> questionLayout.error = null }

        submit.setOnClickListener { submit() }
        root.findViewById<View>(R.id.pcRegisterLogIn).setOnClickListener { actions.onLogIn() }
        root.findViewById<View>(R.id.pcRegisterBack).setOnClickListener { actions.onBack() }
    }

    private fun submit() {
        if (busy) return
        strength.update()
        var firstInvalid: View? = null
        fun fail(layout: TextInputLayout, message: Int) {
            layout.error = context.getString(message)
            if (firstInvalid == null) firstInvalid = layout
        }

        if (name.value().isEmpty()) fail(nameLayout, R.string.pc_err_name)
        if (!isValidEmail(email.value())) fail(emailLayout, R.string.pc_err_email)
        if (!strength.current.acceptable) fail(passwordLayout, R.string.pc_err_password_weak)
        if (confirm.text?.toString() != password.text?.toString()) fail(confirmLayout, R.string.pc_err_password_match)
        val chosen = question.text?.toString().orEmpty()
        if (chosen.isEmpty()) fail(questionLayout, R.string.pc_err_question)
        if (AccountSecurity.normalizeAnswer(answer.text?.toString().orEmpty()).length < 2) fail(answerLayout, R.string.pc_err_answer)
        firstInvalid?.let {
            it.requestFocus()
            return
        }

        val form = RegisterForm(
            name = name.value(),
            email = email.value(),
            phone = phone.value().ifEmpty { null },
            password = password.passwordChars(),
            securityQuestion = chosen,
            securityAnswer = answer.text?.toString().orEmpty(),
        )
        setBusy(true)
        lifecycleOwner.lifecycleScope.launch {
            val result = try {
                actions.register(form)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                RegisterResult.Failed(e.message)
            } finally {
                form.password.fill('\u0000')
            }
            setBusy(false)
            when (result) {
                RegisterResult.Success -> {
                    Haptics.confirm(submit)
                    actions.onRegistered()
                }
                RegisterResult.EmailTaken -> {
                    emailLayout.error = context.getString(R.string.pc_err_email_taken)
                    emailLayout.requestFocus()
                }
                is RegisterResult.Failed -> {
                    passwordLayout.error = result.message ?: context.getString(R.string.pc_err_generic)
                }
            }
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        submit.setBusy(value, R.string.pc_creating, R.string.pc_create_account)
    }
}
