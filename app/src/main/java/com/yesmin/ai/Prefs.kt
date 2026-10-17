package com.yesmin.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** AES-GCM encryption with a key that lives inside the Android Keystore. */
object SecureStore {
    private const val ALIAS = "yesmin_secret_key"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(
                ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun encrypt(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val out = c.iv + c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    fun decrypt(stored: String): String? = try {
        val raw = Base64.decode(stored, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw, 0, 12))
        String(c.doFinal(raw, 12, raw.size - 12), Charsets.UTF_8)
    } catch (e: Exception) {
        null
    }
}

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("yesmin_prefs", Context.MODE_PRIVATE)

    val apiKey: String?
        get() = sp.getString("api_key", null)?.let { SecureStore.decrypt(it) }

    fun saveApiKey(k: String): Boolean = try {
        sp.edit().putString("api_key", SecureStore.encrypt(k)).apply()
        true
    } catch (e: Exception) {
        false
    }

    fun clearApiKey() { sp.edit().remove("api_key").apply() }

    var model: String
        get() = sp.getString("model", "gemini-2.5-flash") ?: "gemini-2.5-flash"
        set(v) { sp.edit().putString("model", v).apply() }
    var persona: String
        get() = sp.getString("persona", Personas.names[0]) ?: Personas.names[0]
        set(v) { sp.edit().putString("persona", v).apply() }
    /** hindi | hinglish | english */
    var language: String
        get() = sp.getString("language", "hinglish") ?: "hinglish"
        set(v) { sp.edit().putString("language", v).apply() }
    var voiceName: String?
        get() = sp.getString("voice", null)
        set(v) { sp.edit().putString("voice", v).apply() }
    var memoryEnabled: Boolean
        get() = sp.getBoolean("memory_enabled", true)
        set(v) { sp.edit().putBoolean("memory_enabled", v).apply() }
    var saveHistory: Boolean
        get() = sp.getBoolean("save_history", false)
        set(v) { sp.edit().putBoolean("save_history", v).apply() }
    var temperature: Float
        get() = sp.getFloat("temperature", 0.8f)
        set(v) { sp.edit().putFloat("temperature", v).apply() }
    /** system | dark | light */
    var theme: String
        get() = sp.getString("theme", "system") ?: "system"
        set(v) { sp.edit().putString("theme", v).apply() }
    var disclosure: Boolean
        get() = sp.getBoolean("disclosure", false)
        set(v) { sp.edit().putBoolean("disclosure", v).apply() }
}
