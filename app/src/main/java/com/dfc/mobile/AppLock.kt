package com.dfc.mobile

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * The app-lock gate. When enabled in Settings, every cold start and every
 * return from the background runs the phone's own screen lock (biometric or
 * PIN — whatever the user already set up; the app never manages a second
 * secret). A device without any screen lock reports as unavailable instead of
 * pretending to protect anything.
 */
object AppLock {

    fun isAvailable(activity: FragmentActivity): Boolean {
        val bm = BiometricManager.from(activity)
        return bm.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    /** Run the lock check; [onUnlocked] fires only after the user authenticates. */
    fun show(activity: FragmentActivity, onSuccess: () -> Unit) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // The user backed out or the system cancelled: the gate stays
                    // shut. (NON_INTERRUPTIBLE / user-cancel both land here.)
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Otherworld Drive")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
        prompt.authenticate(info)
    }
}
