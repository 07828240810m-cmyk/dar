package com.example.utils

import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object BackupCryptoHelper {
    private const val PBKDF2_ITERATIONS = 12000
    private const val KEY_LENGTH_BITS = 256
    private const val GCM_IV_BYTES = 12
    private const val GCM_TAG_BITS = 128

    fun normalizeUsernameKey(username: String): String {
        val cleaned = username.trim().lowercase(Locale.ROOT)
        require(cleaned.length >= 2) { "اسم المستخدم قصير جداً" }
        return sha256Hex("rafiq_user::$cleaned").take(40)
    }

    fun generateSaltHex(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.toHex()
    }

    fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.toHex()
    }

    fun deriveDeterministicReferralCode(uid: String): String {
        val hex = sha256Hex("rafiq_ref::$uid").uppercase(Locale.US)
        return "RF${hex.take(6)}"
    }

    fun generateReportCode(seed: String): String {
        val randomBytes = ByteArray(8)
        SecureRandom().nextBytes(randomBytes)
        val combined = sha256Hex("$seed::${randomBytes.toHex()}::${System.nanoTime()}")
            .uppercase(Locale.US)
        return "PR${combined.take(8)}"
    }

    fun generateAutoFourLetterUsername(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ"
        val rnd = SecureRandom()
        return buildString(4) {
            repeat(4) {
                append(alphabet[rnd.nextInt(alphabet.length)])
            }
        }
    }

    fun generateActivationCode(prefix: String = "RF"): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val rnd = SecureRandom()
        val body = buildString(6) {
            repeat(6) { append(chars[rnd.nextInt(chars.length)]) }
        }
        return "${prefix.uppercase(Locale.US)}-$body"
    }

    fun generateComparisonCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val rnd = SecureRandom()
        val body = buildString(6) {
            repeat(6) { append(chars[rnd.nextInt(chars.length)]) }
        }
        return "CMP-$body"
    }

    fun hashPassword(password: String, saltHex: String): String {
        val keyBytes = deriveAesKeyBytes(password, saltHex, purpose = "auth_verify")
        return sha256Hex("pwd_hash::${keyBytes.toHex()}")
    }

    fun verifyPassword(password: String, saltHex: String, expectedHashHex: String): Boolean {
        if (password.isEmpty() || saltHex.isBlank() || expectedHashHex.isBlank()) return false
        val actual = hashPassword(password, saltHex)
        return MessageDigest.isEqual(
            actual.toByteArray(Charsets.UTF_8),
            expectedHashHex.toByteArray(Charsets.UTF_8)
        )
    }

    /**
     * Compresses [plainJson] with GZIP and encrypts using AES-256-GCM.
     * Returns Pair(base64IvPlusCiphertext, sha256OfPlainJson).
     */
    fun encryptAndCompress(
        plainJson: String,
        password: String,
        saltHex: String
    ): Pair<String, String> {
        val plainBytes = plainJson.toByteArray(Charsets.UTF_8)
        val compressedOut = ByteArrayOutputStream()
        GZIPOutputStream(compressedOut).use { gz ->
            gz.write(plainBytes)
        }
        val compressedBytes = compressedOut.toByteArray()

        val keyBytes = deriveAesKeyBytes(password, saltHex, purpose = "backup_aes")
        val iv = ByteArray(GCM_IV_BYTES)
        SecureRandom().nextBytes(iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), spec)
        val cipherBytes = cipher.doFinal(compressedBytes)

        val combined = ByteArray(iv.size + cipherBytes.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(cipherBytes, 0, combined, iv.size, cipherBytes.size)

        val base64Blob = Base64.encodeToString(combined, Base64.NO_WRAP)
        val checksum = sha256Hex(plainJson)
        return base64Blob to checksum
    }

    fun decryptAndDecompress(
        encryptedBase64: String,
        password: String,
        saltHex: String,
        expectedChecksum: String? = null
    ): String? {
        return try {
            val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            if (combined.size <= GCM_IV_BYTES) return null
            val iv = combined.copyOfRange(0, GCM_IV_BYTES)
            val cipherBytes = combined.copyOfRange(GCM_IV_BYTES, combined.size)

            val keyBytes = deriveAesKeyBytes(password, saltHex, purpose = "backup_aes")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(GCM_TAG_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), spec)
            val compressedBytes = cipher.doFinal(cipherBytes)

            val plainJson = GZIPInputStream(ByteArrayInputStream(compressedBytes)).use { gz ->
                gz.readBytes().toString(Charsets.UTF_8)
            }
            if (!expectedChecksum.isNullOrBlank()) {
                val actualSum = sha256Hex(plainJson)
                if (!actualSum.equals(expectedChecksum, ignoreCase = true)) return null
            }
            plainJson
        } catch (_: Exception) {
            null
        }
    }

    fun countWords(text: String): Int {
        return text.trim().split(Regex("\\s+")).count { it.isNotBlank() }
    }

    private fun deriveAesKeyBytes(password: String, saltHex: String, purpose: String): ByteArray {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val combinedSalt = "$purpose::$saltHex".toByteArray(Charsets.UTF_8)
        val spec = PBEKeySpec(password.toCharArray(), combinedSalt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        return factory.generateSecret(spec).encoded
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
