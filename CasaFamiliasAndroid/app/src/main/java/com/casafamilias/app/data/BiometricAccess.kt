package com.casafamilias.app.data

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.casafamilias.app.ui.CasaViewModel

/** Android owns the biometric templates and authentication dialog. */
class BiometricAccess(private val activity: FragmentActivity, private val vm: CasaViewModel) {
    private val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG
    private val manager = BiometricManager.from(activity)
    private val prompt =
        BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult
                ) {
                    vm.biometricVerified()
                }

                override fun onAuthenticationError(code: Int, message: CharSequence) {
                    val password = code == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                    vm.biometricCancelled(
                        if (password) null
                        else
                            "No se pudo verificar la huella. Puedes reintentar o usar tu contraseña.",
                        password,
                    )
                }
                // A rejected fingerprint is not a successful authentication.
            },
        )

    fun available() = manager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS

    fun canEnroll() =
        manager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED

    fun explanation(): String =
        when (manager.canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS ->
                "Usa la biometría registrada en este teléfono. Android la verifica; la aplicación no recibe tu huella."
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                "Primero registra una huella en la configuración de seguridad del teléfono."
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                "El lector no está disponible ahora. Puedes seguir usando tu contraseña."
            else ->
                "Este teléfono no tiene biometría compatible disponible. Puedes ingresar con tu contraseña."
        }

    fun enroll() {
        val intent =
            if (Build.VERSION.SDK_INT >= 30)
                Intent(Settings.ACTION_BIOMETRIC_ENROLL)
                    .putExtra(Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED, authenticators)
            else Intent(Settings.ACTION_SECURITY_SETTINGS)
        runCatching { activity.startActivity(intent) }
            .onFailure {
                vm.inform("Abre Ajustes → Seguridad en tu teléfono y registra una huella.")
            }
    }

    fun authenticate(enable: Boolean = false) {
        if (!available()) {
            vm.biometricCancelled(explanation())
            return
        }
        if (!vm.beginBiometric(enable)) return
        val info =
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(if (enable) "Activar acceso con huella" else "Entrar a Casa Familias")
                .setSubtitle("Confirma con la biometría de este teléfono")
                .setAllowedAuthenticators(authenticators)
                .setNegativeButtonText(if (enable) "Cancelar" else "Usar contraseña")
                .setConfirmationRequired(true)
                .build()
        runCatching { prompt.authenticate(info) }
            .onFailure {
                vm.biometricCancelled("No se pudo abrir el lector. Ingresa con tu contraseña.")
            }
    }
}
