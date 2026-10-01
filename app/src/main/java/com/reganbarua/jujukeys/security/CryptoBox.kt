package com.reganbarua.jujukeys.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts small private data (clipboard history, API key) with an AES-256-GCM key that lives
 * in the Android Keystore. The key never leaves the Keystore and cannot be copied out of the
 * phone — so even a copy of the app's files cannot be read elsewhere.
 *
 * Stored form: "v1:" + Base64(12-byte IV + ciphertext + 16-byte tag).
 * Any failure returns null — callers then keep the old data instead of losing it.
 */
object CryptoBox {
    private const val ALIAS = "jujukeys_data_v1"
    private const val PREFIX = "v1:"
    private const val TAG_BITS = 128

    @Volatile private var cached: SecretKey? = null

    private fun key(): SecretKey {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val existing = (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
            val k = existing ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
                init(
                    KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build()
                )
                generateKey()
            }
            cached = k
            return k
        }
    }

    fun isEncrypted(s: String?): Boolean = s != null && s.startsWith(PREFIX)

    /** Encrypts [plain]; null if the Keystore is not usable right now. */
    fun encrypt(plain: String): String? = runCatching {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val iv = c.iv
        val ct = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        PREFIX + Base64.encodeToString(iv + ct, Base64.NO_WRAP)
    }.getOrNull()

    /** Decrypts what [encrypt] made; null if it cannot be read (wrong form, key missing, tampered). */
    fun decrypt(stored: String): String? = runCatching {
        if (!stored.startsWith(PREFIX)) return null
        val all = Base64.decode(stored.substring(PREFIX.length), Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, all, 0, 12))
        String(c.doFinal(all, 12, all.size - 12), Charsets.UTF_8)
    }.getOrNull()

    /** Encrypt, then read back once — only a value that round-trips is trusted. */
    fun encryptVerified(plain: String): String? {
        val e = encrypt(plain) ?: return null
        return if (decrypt(e) == plain) e else null
    }
}
