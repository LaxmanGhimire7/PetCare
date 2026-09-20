package com.example.petcare.ui

/** Every data screen reports an explicit initial, empty, populated, or failed state. */
sealed interface ScreenState<out T> {
    /** Source data is still being read. */
    data object Loading : ScreenState<Nothing>
    /** The source has no records for this screen. */
    data object Empty : ScreenState<Nothing>
    /** The source returned usable records. */
    data class Content<T>(val data: T) : ScreenState<T>
    /** Loading failed and the screen should offer a retry path. */
    data class Error(val message: String) : ScreenState<Nothing>
}
