package com.example.antigravityfinance.service.security

import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Real platform biometric authentication for the PIN gate.
 *
 * The gate previously unlocked by submitting a hardcoded PIN, which meant any user
 * whose PIN happened to match it could be unlocked by anyone tapping the fingerprint
 * key. Unlock is now driven only by an actual BiometricPrompt success callback.
 */
object BiometricGate {

    private const val TAG = "BiometricGate"

    /** True when the device has strong biometrics enrolled and usable right now. */
    fun isAvailable(context: Context): Boolean {
        val manager = BiometricManager.from(context)
        return manager.canAuthenticate(Authenticators) == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Shows the system biometric prompt. [onSuccess] runs only on a genuine
     * authentication; every other outcome leaves the gate locked.
     */
    fun authenticate(context: Context, onSuccess: () -> Unit) {
        val activity = context.findFragmentActivity()
        if (activity == null) {
            Log.w(TAG, "No FragmentActivity available; leaving gate locked")
            return
        }
        if (!isAvailable(activity)) {
            Log.w(TAG, "No strong biometric enrolled; leaving gate locked")
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(code: Int, message: CharSequence) {
                    // The message can contain vendor detail; log only the code.
                    Log.w(TAG, "Biometric prompt ended with code $code")
                }

                override fun onAuthenticationFailed() {
                    // A single non-matching finger. The prompt stays open.
                }
            }
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock FinKlar")
            .setSubtitle("Confirm your identity to open the app")
            .setAllowedAuthenticators(Authenticators)
            .setConfirmationRequired(false)
            .build()

        prompt.authenticate(info)
    }

    private val Authenticators =
        BiometricManager.Authenticators.BIOMETRIC_STRONG

    private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
        is FragmentActivity -> this
        is ContextWrapper -> baseContext.findFragmentActivity()
        else -> null
    }
}