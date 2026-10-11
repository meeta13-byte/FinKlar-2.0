package com.example.antigravityfinance.ui.viewmodel

import android.app.Activity
import androidx.lifecycle.ViewModel
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit

sealed interface OtpState {
    data object Idle : OtpState
    data object Sending : OtpState
    data object CodeSent : OtpState          // show the code entry field
    data object TimedOut : OtpState          // auto-retrieval timed out; user can still type the code or resend
    data object Verifying : OtpState
    data class Success(val uid: String) : OtpState
    data class Error(val message: String) : OtpState
}

class PhoneAuthViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()

    private val _state = MutableStateFlow<OtpState>(OtpState.Idle)
    val state: StateFlow<OtpState> = _state.asStateFlow()

    private var verificationId: String? = null
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null
    private var lastPhone: String? = null

    private val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

        // Instant verification or SMS auto-retrieval: no code typing needed
        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            signIn(credential)
        }

        override fun onVerificationFailed(e: FirebaseException) {
            _state.value = OtpState.Error(
                when (e) {
                    is FirebaseAuthInvalidCredentialsException ->
                        "That phone number looks invalid. Use the full format, e.g. +91XXXXXXXXXX."
                    is FirebaseTooManyRequestsException ->
                        "Too many attempts. Please wait a while and try again."
                    else -> "Could not send the code: ${e.localizedMessage ?: "unknown error"}"
                }
            )
        }

        override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
            verificationId = id
            resendToken = token
            _state.value = OtpState.CodeSent
        }

        override fun onCodeAutoRetrievalTimeOut(id: String) {
            verificationId = id
            if (_state.value is OtpState.CodeSent) _state.value = OtpState.TimedOut
        }
    }

    /** Phone must be in E.164 format, e.g. +919876543210 */
    fun sendCode(activity: Activity, phone: String) {
        lastPhone = phone
        startVerification(activity, phone, resend = false)
    }

    fun resendCode(activity: Activity) {
        val phone = lastPhone ?: run {
            _state.value = OtpState.Error("Enter your phone number first.")
            return
        }
        startVerification(activity, phone, resend = true)
    }

    private fun startVerification(activity: Activity, phone: String, resend: Boolean) {
        _state.value = OtpState.Sending
        val builder = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phone)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
        if (resend) resendToken?.let { builder.setForceResendingToken(it) }
        PhoneAuthProvider.verifyPhoneNumber(builder.build())
    }

    fun verifyCode(code: String) {
        val id = verificationId
        if (id == null) {
            _state.value = OtpState.Error("Request a code first.")
            return
        }
        if (code.length != 6) {
            _state.value = OtpState.Error("Enter the 6-digit code.")
            return
        }
        _state.value = OtpState.Verifying
        signIn(PhoneAuthProvider.getCredential(id, code))
    }

    private fun signIn(credential: PhoneAuthCredential) {
        auth.signInWithCredential(credential).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val uid = task.result?.user?.uid
                _state.value = if (uid != null) OtpState.Success(uid)
                else OtpState.Error("Sign-in failed. Please try again.")
            } else {
                _state.value = OtpState.Error(
                    when (task.exception) {
                        is FirebaseAuthInvalidCredentialsException -> "Wrong code. Please check it and try again."
                        else -> "Verification failed: ${task.exception?.localizedMessage ?: "unknown error"}"
                    }
                )
            }
        }
    }

    /** Use this everywhere you previously used the phone number as the user id. */
    fun currentUid(): String? = auth.currentUser?.uid
}