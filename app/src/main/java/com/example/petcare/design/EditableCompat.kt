package com.example.petcare.design

import android.text.Editable
import android.text.TextUtils

/** Kotlin compatibility shim for the Java GetChars method used by the supplied auth binder. */
internal fun Editable.getChars(start: Int, end: Int, destination: CharArray, destinationOffset: Int) {
    TextUtils.getChars(this, start, end, destination, destinationOffset)
}
