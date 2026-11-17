package com.shbeeb.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android Keystore encryption. Secrets are not stored as plain SharedPreferences
 * and Android backup is disabled in AndroidManifest.xml.
 * This protects data at rest, NOT a rooted/compromised phone.
 */
internal class SecureApiKeys(context: Context) {
    private val app = context.applicationContext
    private val preferences = app.getSharedPreferences("shbeeb_secure_api_keys", Context.MODE_PRIVATE)
    private val alias = "com.shbeeb.ai.user-api-keys.aes-v1"

    private fun preferenceName(engine: ChatEngine): String {
        require(engine == ChatEngine.OPENAI || engine == ChatEngine.GEMINI ||
                engine == ChatEngine.CLAUDE || engine == ChatEngine.DEEPSEEK ||
                engine == ChatEngine.OPENROUTER) { "هذا المحرك لا يحتاج مفتاح API سحابياً." }
        return "enc_${engine.id}"
    }

    private fun key(): SecretKey {
        val storage = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (storage.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    /** Persist one API key only after the user taps Save. */
    fun save(engine: ChatEngine, value: String) {
        val name = preferenceName(engine)
        val trimmed = value.trim()
        require(trimmed.isNotEmpty() && trimmed.length <= 300) {
            "المفتاح فارغ أو طويل جداً."
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(trimmed.toByteArray(Charsets.UTF_8))
        val record = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
        require(preferences.edit().putString(name, record).commit()) {
            "تعذّر حفظ المفتاح المشفر على الهاتف."
        }
    }

    /** Read only for a request initiated by the user; do not render in the UI. */
    fun read(engine: ChatEngine): String {
        val name = preferenceName(engine)
        val encrypted = preferences.getString(name, null) ?: return ""
        val parts = encrypted.split(":", limit = 2)
        require(parts.size == 2) { "المفتاح المحفوظ تالف؛ احذفه وأعد إدخاله." }
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                key(),
                GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP))
            )
            val raw = cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP))
            return String(raw, Charsets.UTF_8)
        } catch (_: Exception) {
            throw IllegalStateException("تعذّر فتح المفتاح المحفوظ. احذفه وأدخل مفتاحاً جديداً.")
        }
    }

    /** Encrypted record exists; it does not imply a usable account or credit. */
    fun has(engine: ChatEngine): Boolean =
        preferences.contains(preferenceName(engine))

    fun delete(engine: ChatEngine) {
        require(preferences.edit().remove(preferenceName(engine)).commit()) {
            "تعذّر حذف المفتاح من إعدادات الهاتف."
        }
    }
}

internal object ProviderKeyLinks {
    fun url(engine: ChatEngine): String = when (engine) {
        ChatEngine.OPENAI -> "https://platform.openai.com/api-keys"
        ChatEngine.GEMINI -> "https://aistudio.google.com/api-keys"
        ChatEngine.CLAUDE -> "https://console.anthropic.com/settings/keys"
        ChatEngine.DEEPSEEK -> "https://platform.deepseek.com/api_keys"
        ChatEngine.OPENROUTER -> "https://openrouter.ai/settings/keys"
        else -> error("المحرك المحلي وOllama لا يحتاجان مفتاح API سحابياً.")
    }
}
