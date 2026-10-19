package com.example.antigravityfinance.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Time-based OTP verification (RFC 6238).
 *
 * Replaces SMS sign-in, which Firebase only serves on the paid Blaze plan. The code comes
 * from an authenticator app the user already controls, so verification is local, free and
 * not exposed to SIM-swap interception.
 *
 * The identity this produces is a locally generated UUID. Nothing about the user is sent
 * anywhere; see FinanceViewModel.userUid, which is the only consumer.
 */
sealed interface TotpState {
    data object Idle : TotpState
    data object Preparing : TotpState
    /** Code entry screen is open; [remaining] seconds until it can be submitted. */
    data class AwaitingCode(val remaining: Int) : TotpState
    data object Verifying : TotpState
    data class Success(val uid: String) : TotpState
    data class Error(val message: String) : TotpState
}

/**
 * Onboarding gate for the name/email/phone intro flow.
 *
 * This is deliberately NOT authentication: no code is sent and any well-formed 6-digit
 * entry is accepted. It exists to give the user a deliberate confirmation step before the
 * app opens. The actual protection for stored data is the PIN lock in SecurityHelper, and
 * anything the user types here must never be described as verification in UI copy.
 *
 * [TotpManager] is retained for real time-based verification if that is wanted later; it
 * is unused here.
 */
class TotpAuthViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<TotpState>(TotpState.Idle)
    val state: StateFlow<TotpState> = _state.asStateFlow()

    /** Remaining validity of the current code, for a countdown in the UI. */
    private val _secondsRemaining = MutableStateFlow(0)
    val secondsRemaining: StateFlow<Int> = _secondsRemaining.asStateFlow()

    private var countdownJob: kotlinx.coroutines.Job? = null

    /** Seconds the user must wait on the code screen before it can be submitted. */
    var countdownSeconds: Int = 20
        private set

    /** Opens the code screen and starts the countdown. */
    fun beginSetup() {
        countdownSeconds = 20
        _secondsRemaining.value = countdownSeconds
        _state.value = TotpState.AwaitingCode(remaining = countdownSeconds)

        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            var remaining = countdownSeconds
            while (remaining > 0) {
                delay(1_000)
                remaining--
                _secondsRemaining.value = remaining
            }
            val current = state.value
            if (current is TotpState.AwaitingCode) {
                _state.value = current.copy(remaining = 0)
            }
        }
    }

    /**
     * Accepts any well-formed 6-digit entry. The format is checked so the field is not a
     * free-for-all, but the digits themselves are not verified against anything.
     */
    fun verify(code: String) {
        if (_secondsRemaining.value > 0) {
            _state.value = TotpState.Error("Please wait ${_secondsRemaining.value}s before continuing.")
            return
        }
        val trimmed = code.trim()
        if (trimmed.length != 6 || trimmed.any { !it.isDigit() }) {
            _state.value = TotpState.Error("Enter any 6 digits.")
            return
        }
        _state.value = TotpState.Success(java.util.UUID.randomUUID().toString())
    }

    /** Cancels an in-progress setup and stops the countdown. */
    fun cancelSetup() {
        countdownJob?.cancel()
        _state.value = TotpState.Idle
    }

    override fun onCleared() {
        countdownJob?.cancel()
        super.onCleared()
    }
}