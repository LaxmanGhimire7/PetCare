package com.example.petcare.ui

import android.widget.AutoCompleteTextView
import androidx.annotation.StringRes
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Makes exposed selectors reliable for touch and TalkBack on API 24 and newer. */
object SelectionDialog {
    fun attach(input: AutoCompleteTextView, @StringRes title: Int,
        labels: List<String>, onSelect: (Int) -> Unit) {
        input.setOnClickListener {
            if (labels.isEmpty()) return@setOnClickListener
            MaterialAlertDialogBuilder(input.context)
                .setTitle(title)
                .setItems(labels.toTypedArray()) { _, index ->
                    input.setText(labels[index], false)
                    onSelect(index)
                }.show()
        }
    }
}
