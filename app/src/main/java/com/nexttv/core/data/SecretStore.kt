// SPDX-License-Identifier: GPL-3.0-only
// Next TV — at-rest protection for subscription URLs and credentials.
package com.nexttv.core.data

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
 * Encrypts small preference values with an AES-256-GCM key held by Android Keystore.
 * Values written by older builds are plain text and are migrated on first background start.
 */
internal class SecretStore(@Suppress("UNUSED_PARAMETER") context: Context) {
    private val alias = "nexttv_prefs_v1"
    private val prefix = "enc:v1:"

    fun isEncrypted(value: String): Boolean = value.startsWith(prefix)

    /** Returns plain text for both encrypted and legacy values; unreadable ciphertext yields "". */
    fun open(value: String): String {
        if (value.isEmpty() || !isEncrypted(value)) return value
        return runCatching {
            val payload = Base64.decode(value.removePrefix(prefix), Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, payload, 0, IV_BYTES))
            String(cipher.doFinal(payload, IV_BYTES, payload.size - IV_BYTES), Charsets.UTF_8)
        }.getOrDefault("")
    }

    /** Encrypts a value. On keystore failure the caller keeps the plain value rather than losing it. */
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

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
    }
}
