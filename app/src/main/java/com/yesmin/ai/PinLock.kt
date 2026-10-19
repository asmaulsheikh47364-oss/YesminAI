package com.yesmin.ai

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.util.Base64
import androidx.core.content.ContextCompat
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** PIN is never stored: only a salted PBKDF2 hash. */
object PinLock {
    private fun hash(pin: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 10000, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun set(prefs: Prefs, pin: String) {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        prefs.lockSalt = Base64.encodeToString(salt, Base64.NO_WRAP)
        prefs.lockHash = hash(pin, salt)
    }

    fun check(prefs: Prefs, pin: String): Boolean {
        val s = prefs.lockSalt
        val h = prefs.lockHash
        if (s.isEmpty() || h.isEmpty()) return false
        return try {
            MessageDigest.isEqual(hash(pin, Base64.decode(s, Base64.NO_WRAP)).toByteArray(), h.toByteArray())
        } catch (e: Exception) {
            false
        }
    }

    fun clear(prefs: Prefs) {
        prefs.lockSalt = ""
        prefs.lockHash = ""
    }
}

object Biometric {
    fun available(ctx: Context): Boolean = try {
        Build.VERSION.SDK_INT >= 29 &&
            ctx.getSystemService(BiometricManager::class.java)?.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS
    } catch (e: Exception) {
        false
    }

    fun prompt(activity: Activity, onSuccess: () -> Unit, onFail: () -> Unit) {
        if (Build.VERSION.SDK_INT < 28) { onFail(); return }
        try {
            val executor = ContextCompat.getMainExecutor(activity)
            val bp = BiometricPrompt.Builder(activity)
                .setTitle("Yesmin AI unlock")
                .setNegativeButton("PIN use karo", executor) { _, _ -> onFail() }
                .build()
            bp.authenticate(CancellationSignal(), executor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) { onSuccess() }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) { onFail() }
            })
        } catch (e: Exception) {
            onFail()
        }
    }
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
