package app.lifeos.security

import android.content.Context
import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Optionale App-Sperre über Androids BiometricPrompt (Fingerabdruck/Gesicht der Klasse „stark" oder
 * PIN/Muster/Passwort des Geräts).
 *
 * Ehrliche Einordnung: Die Sperre schützt die Oberfläche. Die Verschlüsselung der Daten hängt bewusst
 * NICHT an der Biometrie (sonst würde eine neu eingerichtete Biometrie alle Daten unlesbar machen, R-1).
 * Nach 30 Sekunden im Hintergrund wird erneut gesperrt.
 */
class AppLock {
    /** Startet gesperrt – greift aber nur, wenn die Sperre eingeschaltet ist (kein Aufblitzen von Inhalten beim Kaltstart). */
    private val _locked = MutableStateFlow(true)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()
    private var backgroundSince: Long? = null

    fun lockNow() {
        _locked.value = true
    }

    fun unlock() {
        _locked.value = false
    }

    fun onBackground() {
        backgroundSince = SystemClock.elapsedRealtime()
    }

    fun onForeground(enabled: Boolean) {
        val since = backgroundSince ?: return
        backgroundSince = null
        if (enabled && SystemClock.elapsedRealtime() - since > RELOCK_AFTER_MS) _locked.value = true
    }

    companion object {
        const val RELOCK_AFTER_MS = 30_000L
        /** STRONG|DEVICE_CREDENTIAL wird von Android 9/10 nicht unterstützt – dort WEAK|DEVICE_CREDENTIAL. */
        private val AUTHENTICATORS: Int
            get() = if (android.os.Build.VERSION.SDK_INT in 28..29) BiometricManager.Authenticators.BIOMETRIC_WEAK or DEVICE_CREDENTIAL else BIOMETRIC_STRONG or DEVICE_CREDENTIAL

        /** `null` = verfügbar, sonst verständlicher Grund. */
        fun unavailableReason(context: Context): String? = when (BiometricManager.from(context).canAuthenticate(AUTHENTICATORS)) {
            BiometricManager.BIOMETRIC_SUCCESS -> null
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "Auf diesem Gerät ist keine Displaysperre (PIN, Muster, Passwort oder Fingerabdruck) eingerichtet."
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE, BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "Die Gerätesperre ist derzeit nicht verfügbar."
            else -> "Die App-Sperre wird auf diesem Gerät nicht unterstützt."
        }

        fun authenticate(activity: FragmentActivity, title: String, onSuccess: () -> Unit, onFailure: (String?) -> Unit) {
            val prompt = BiometricPrompt(
                activity, ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        val cancelled = errorCode == BiometricPrompt.ERROR_USER_CANCELED || errorCode == BiometricPrompt.ERROR_CANCELED
                        onFailure(if (cancelled) null else errString.toString())
                    }
                },
            )
            prompt.authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setSubtitle("LifeOS")
                    .setAllowedAuthenticators(AUTHENTICATORS)
                    .build(),
            )
        }
    }
}
