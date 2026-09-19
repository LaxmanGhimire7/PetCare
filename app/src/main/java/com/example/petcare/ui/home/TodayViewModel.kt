package com.example.petcare.ui.home

import androidx.lifecycle.ViewModel

/** Retains the selected pet and prevents the first-load motion replaying on tab returns. */
class TodayViewModel : ViewModel() {
    var selectedPetId: Long? = null

    fun consumeFirstEntrance(): Boolean {
        if (playedThisProcess) return false
        playedThisProcess = true
        return true
    }

    private companion object {
        var playedThisProcess = false
    }
}
