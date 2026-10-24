package tn.careerpilot.data

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
 * Jetons chiffrés en AES-GCM avec une clé de l'Android Keystore (la clé ne quitte jamais le téléphone),
 * puis rangés dans des SharedPreferences privées à l'application.
 */
class KeystoreTokenStore(context: Context) : TokenStore {

    private val prefs = context.getSharedPreferences("careerpilot_session", Context.MODE_PRIVATE)

    override val accessToken: String? get() = read(KEY_ACCESS)
    override val refreshToken: String? get() = read(KEY_REFRESH)

    @Synchronized
    override fun save(accessToken: String, refreshToken: String) {
        prefs.edit()
            .putString(KEY_ACCESS, encrypt(accessToken))
            .putString(KEY_REFRESH, encrypt(refreshToken))
            .apply()
    }

    @Synchronized
    override fun clear() {
        prefs.edit().clear().apply()
    }

    @Synchronized
    private fun read(key: String): String? {
        val stored = prefs.getString(key, null) ?: return null
        return runCatching { decrypt(stored) }.getOrElse {
            clear() // clé du Keystore perdue (réinstallation, changement de verrouillage) : on repart de zéro
            null
        }
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val bytes = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, bytes, 0, IV_SIZE))
        return String(cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE), Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEY_ALIAS = "careerpilot_tokens"
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
    }
}
