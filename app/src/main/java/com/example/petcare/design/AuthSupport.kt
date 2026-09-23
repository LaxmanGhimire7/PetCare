package com.example.petcare.design

import android.text.Editable
import android.text.TextWatcher
import android.util.Patterns
import android.widget.EditText
import androidx.annotation.StringRes
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout
import java.util.Locale

/** Small helpers shared by the log-in, sign-up and reset screens. */

internal fun EditText.afterTextChanged(block: (String) -> Unit) {
    addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) = block(s?.toString().orEmpty())
    })
}

/** Trimmed text, for names and emails. */
internal fun EditText.value(): String = text?.toString()?.trim().orEmpty()

/** Password as a CharArray, so the caller can wipe it after use instead of leaving a String in memory. */
internal fun EditText.passwordChars(): CharArray {
    val editable = text ?: return CharArray(0)
    val out = CharArray(editable.length)
    editable.getChars(0, editable.length, out, 0)
    return out
}

internal fun isValidEmail(address: String): Boolean = Patterns.EMAIL_ADDRESS.matcher(address).matches()

/** Clears the field's error as soon as the user starts fixing it. */
internal fun TextInputLayout.clearErrorOnEdit() {
    editText?.afterTextChanged { if (error != null) error = null }
}

/** Disables the button and swaps its label while work is running. */
internal fun MaterialButton.setBusy(busy: Boolean, @StringRes busyText: Int, @StringRes idleText: Int) {
    isEnabled = !busy
    setText(if (busy) busyText else idleText)
}

/** 28000 ms -> "0:28". */
internal fun formatLock(millis: Long): String {
    val seconds = (millis + 999L) / 1000L
    return String.format(Locale.US, "%d:%02d", seconds / 60L, seconds % 60L)
}
