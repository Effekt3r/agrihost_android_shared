package za.co.mjrsolutions.shared.auth

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

internal object BiometricHelper {

    enum class BiometricStatus {
        AVAILABLE,
        NO_HARDWARE,
        HARDWARE_UNAVAILABLE,
        NOT_ENROLLED,
        UNKNOWN_ERROR
    }

    fun checkBiometricStatus(context: Context): BiometricStatus {
        val manager = BiometricManager.from(context)
        return when (manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricStatus.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricStatus.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricStatus.HARDWARE_UNAVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricStatus.NOT_ENROLLED
            else -> BiometricStatus.UNKNOWN_ERROR
        }
    }

    fun isBiometricAvailable(context: Context): Boolean {
        return checkBiometricStatus(context) == BiometricStatus.AVAILABLE
    }

    /**
     * Shows a biometric prompt on the given [activity].
     *
     * @param title              Prompt dialog title (caller-supplied, supports localisation).
     * @param subtitle           Prompt dialog subtitle.
     * @param negativeButtonText Label for the cancel / fallback button (no hardcoded strings).
     * @param onSuccess          Invoked on the main thread when authentication succeeds.
     * @param onFailure          Invoked on the main thread with an error message when
     *                           authentication cannot complete (hardware error, user cancel, etc.).
     *                           Individual failed attempts are silently retried by the system prompt.
     */
    fun prompt(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        negativeButtonText: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        if (!isBiometricAvailable(activity)) {
            onFailure("Biometric authentication not available")
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onFailure(errString.toString())
            }

            override fun onAuthenticationFailed() {
                // Individual attempt failed — the biometric prompt retries automatically.
            }
        }

        val prompt = BiometricPrompt(activity, executor, callback)
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeButtonText)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        prompt.authenticate(info)
    }
}
