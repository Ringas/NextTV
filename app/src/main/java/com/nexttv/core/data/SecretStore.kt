// SPDX-License-Identifier: GPL-3.0-only
// Next TV — at-rest protection for subscription URLs and credentials.
package com.nexttv.core.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts small preference values with an AES-256-GCM key held by Android Keystore.
 * Values written by older builds are plain text and are migrated on first background start.
 */
internal class SecretStore(@Suppress("UNUSED_PARAMETER") context: Context) {
    private val alias = "nexttv_prefs_v1"
    private val prefix = "enc:v1:"

    fun isEncrypted(value: String): Boolean = value.startsWith(prefix)

    internal sealed interface Read {
        data class Value(val text: String) : Read
        data object Empty : Read
        data class Failed(val error: Throwable) : Read
    }

    /** Distinguishes empty/legacy plain text from a ciphertext that cannot be opened. */
    fun read(value: String): Read {
        if (value.isEmpty()) return Read.Empty
        if (!isEncrypted(value)) return Read.Value(value)
        return runCatching {
            val payload = Base64.decode(value.removePrefix(prefix), Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, payload, 0, IV_BYTES))
            Read.Value(String(cipher.doFinal(payload, IV_BYTES, payload.size - IV_BYTES), Charsets.UTF_8))
        }.getOrElse { Read.Failed(it) }
    }

    /** Legacy helper for callers that only need the plain value. */
    fun open(value: String): String = (read(value) as? Read.Value)?.text ?: ""

    /** Encrypts a value. Null means the caller must keep the previous value; never store plaintext. */
    fun seal(value: String): String? = runCatching {
        if (value.isEmpty()) return ""
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        val packed = ByteArray(iv.size + encrypted.size)
        iv.copyInto(packed, 0)
        encrypted.copyInto(packed, iv.size)
        prefix + Base64.encodeToString(packed, Base64.NO_WRAP)
    }.getOrNull()

    /**
     * Writes [FILE_MAGIC] + a fresh IV to [raw], then returns a streaming AES-GCM
     * wrapper. Closing the wrapper finalizes the cipher but leaves [raw] open so
     * AtomicFile can still fsync/finish it.
     */
    fun encryptStream(raw: OutputStream): OutputStream? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        raw.write(FILE_MAGIC)
        raw.write(cipher.iv)
        val nonClosing = object : java.io.FilterOutputStream(raw) {
            override fun close() { flush() }
        }
        CipherOutputStream(nonClosing, cipher)
    }.getOrNull()

    /** Builds a decrypting stream after the caller has consumed magic and IV. */
    fun decryptStream(raw: InputStream, iv: ByteArray): InputStream? = runCatching {
        require(iv.size == IV_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        CipherInputStream(raw, cipher)
    }.getOrNull()

    @Synchronized
    private fun key(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    internal companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        val FILE_MAGIC = byteArrayOf('N'.code.toByte(), 'T'.code.toByte(), 'V'.code.toByte(), '1'.code.toByte())
    }
}
