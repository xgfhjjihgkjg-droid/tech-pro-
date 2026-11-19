package com.example.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.util.Collections
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private val Context.keyVaultDataStore: DataStore<Preferences> by preferencesDataStore(name = "ciphertalk_key_vault")

data class IdentityKeyBundle(
    val keyPair: KeyPair,
    val publicKeyBase64: String,
    val keyFingerprint: String
)

data class EncryptedPayload(
    val ciphertextBase64: String,
    val ivBase64: String
)

class CryptoEngine(private val context: Context? = null) {

    private val secureRandom = SecureRandom()
    private val sessionKeyCache: MutableMap<String, SecretKey> = Collections.synchronizedMap(
        object : LinkedHashMap<String, SecretKey>(128, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, SecretKey>?): Boolean = size > 128
        }
    )

    suspend fun getOrCreateIdentityKeys(uid: String): IdentityKeyBundle {
        require(uid.isNotBlank()) { "UID cannot be blank for key vault lookup" }
        if (context != null) {
            val prefs = context.keyVaultDataStore.data.first()
            val storedPriv = prefs[stringPreferencesKey("priv_$uid")]
            val storedPub = prefs[stringPreferencesKey("pub_$uid")]
            if (!storedPriv.isNullOrBlank() && !storedPub.isNullOrBlank()) {
                val unwrappedPriv = unwrapPrivateKey(uid, storedPriv)
                if (unwrappedPriv != null) {
                    val pubKey = decodePublicKey(storedPub)
                    val bundle = IdentityKeyBundle(
                        keyPair = KeyPair(pubKey, unwrappedPriv),
                        publicKeyBase64 = storedPub,
                        keyFingerprint = computeKeyFingerprint(storedPub)
                    )
                    return bundle
                }
            }
        }
        return rotateIdentityKeys(uid)
    }

    suspend fun rotateIdentityKeys(uid: String): IdentityKeyBundle {
        require(uid.isNotBlank()) { "UID cannot be blank for key rotation" }
        val keyPair = generateEcKeyPair()
        val pubBase64 = encodePublicKey(keyPair.public)
        val wrappedPriv = wrapPrivateKey(uid, keyPair.private)
        if (context != null) {
            context.keyVaultDataStore.edit { prefs ->
                prefs[stringPreferencesKey("priv_$uid")] = wrappedPriv
                prefs[stringPreferencesKey("pub_$uid")] = pubBase64
            }
        }
        sessionKeyCache.clear()
        return IdentityKeyBundle(
            keyPair = keyPair,
            publicKeyBase64 = pubBase64,
            keyFingerprint = computeKeyFingerprint(pubBase64)
        )
    }

    fun generateEcKeyPair(): KeyPair {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"), secureRandom)
        return generator.generateKeyPair()
    }

    fun encodePublicKey(publicKey: PublicKey): String =
        Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)

    fun decodePublicKey(publicKeyBase64: String): PublicKey {
        val bytes = Base64.decode(publicKeyBase64, Base64.NO_WRAP)
        return KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(bytes))
    }

    fun deriveSessionAesKey(
        myPrivateKey: PrivateKey,
        peerPublicKeyBase64: String,
        conversationId: String
    ): SecretKey {
        val cacheKey = "${System.identityHashCode(myPrivateKey)}|$peerPublicKeyBase64|$conversationId"
        sessionKeyCache[cacheKey]?.let { return it }

        val peerPublicKey = decodePublicKey(peerPublicKeyBase64)
        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(myPrivateKey)
        keyAgreement.doPhase(peerPublicKey, true)
        val sharedSecret = keyAgreement.generateSecret()

        // HKDF-SHA256 Extract + Expand
        val hmacExtract = Mac.getInstance("HmacSHA256")
        val salt = conversationId.toByteArray(Charsets.UTF_8)
        hmacExtract.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = hmacExtract.doFinal(sharedSecret)

        val hmacExpand = Mac.getInstance("HmacSHA256")
        hmacExpand.init(SecretKeySpec(prk, "HmacSHA256"))
        val info = "CipherTalk-E2EE-v1|$conversationId".toByteArray(Charsets.UTF_8) + byteArrayOf(0x01)
        val okm = hmacExpand.doFinal(info)
        val derived = SecretKeySpec(okm.copyOf(32), "AES")
        sessionKeyCache[cacheKey] = derived
        return derived
    }

    fun encryptMessage(
        plaintext: String,
        myPrivateKey: PrivateKey,
        peerPublicKeyBase64: String,
        conversationId: String,
        senderId: String
    ): EncryptedPayload {
        val sessionKey = deriveSessionAesKey(myPrivateKey, peerPublicKeyBase64, conversationId)
        val iv = ByteArray(12).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, sessionKey, GCMParameterSpec(128, iv))
        cipher.updateAAD("$conversationId|$senderId".toByteArray(Charsets.UTF_8))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return EncryptedPayload(
            ciphertextBase64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP),
            ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        )
    }

    fun decryptMessage(
        ciphertextBase64: String,
        ivBase64: String,
        myPrivateKey: PrivateKey,
        peerPublicKeyBase64: String,
        conversationId: String,
        senderId: String
    ): Result<String> = runCatching {
        val sessionKey = deriveSessionAesKey(myPrivateKey, peerPublicKeyBase64, conversationId)
        val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
        val ciphertext = Base64.decode(ciphertextBase64, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, sessionKey, GCMParameterSpec(128, iv))
        cipher.updateAAD("$conversationId|$senderId".toByteArray(Charsets.UTF_8))
        val plaintextBytes = cipher.doFinal(ciphertext)
        String(plaintextBytes, Charsets.UTF_8)
    }

    fun computeKeyFingerprint(publicKeyBase64: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(publicKeyBase64.toByteArray(Charsets.UTF_8))
        return digest.take(8).joinToString("") { "%02X".format(it) }
            .chunked(4)
            .joinToString(" ")
    }

    fun computeSafetyNumber(pubKeyABase64: String, pubKeyBBase64: String): String {
        val ordered = listOf(pubKeyABase64, pubKeyBBase64).sorted()
        val input = "${ordered[0]}|${ordered[1]}".toByteArray(Charsets.UTF_8)
        var hash = MessageDigest.getInstance("SHA-256").digest(input)
        // Iterative stretching for 60-digit human-verifiable safety number
        repeat(4) {
            hash = MessageDigest.getInstance("SHA-256").digest(hash + input)
        }
        val digits = StringBuilder(60)
        var idx = 0
        while (digits.length < 60) {
            val b0 = hash[idx % hash.size].toInt() and 0xFF
            val b1 = hash[(idx + 1) % hash.size].toInt() and 0xFF
            val b2 = hash[(idx + 2) % hash.size].toInt() and 0xFF
            val value = ((b0 shl 16) or (b1 shl 8) or b2) % 100000
            digits.append("%05d".format(value))
            idx += 3
        }
        return digits.toString().take(60).chunked(5).joinToString(" ")
    }

    fun computeCallVerificationCode(pubKeyABase64: String, pubKeyBBase64: String, callId: String): String {
        val ordered = listOf(pubKeyABase64, pubKeyBBase64).sorted()
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("${ordered[0]}|${ordered[1]}|$callId".toByteArray(Charsets.UTF_8))
        return digest.take(4).joinToString("") { "%02X".format(it) }
            .chunked(4)
            .joinToString("-")
    }

    fun generateGroupKeyBase64(): String {
        val keyBytes = ByteArray(32).also { secureRandom.nextBytes(it) }
        return Base64.encodeToString(keyBytes, Base64.NO_WRAP)
    }

    fun encryptWithGroupKey(
        plaintext: String,
        groupKeyBase64: String,
        groupId: String,
        senderId: String
    ): EncryptedPayload {
        val keyBytes = Base64.decode(groupKeyBase64, Base64.NO_WRAP)
        val key = SecretKeySpec(keyBytes, "AES")
        val iv = ByteArray(12).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        cipher.updateAAD("$groupId|$senderId".toByteArray(Charsets.UTF_8))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return EncryptedPayload(
            ciphertextBase64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP),
            ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        )
    }

    fun decryptWithGroupKey(
        ciphertextBase64: String,
        ivBase64: String,
        groupKeyBase64: String,
        groupId: String,
        senderId: String
    ): Result<String> = runCatching {
        val keyBytes = Base64.decode(groupKeyBase64, Base64.NO_WRAP)
        val key = SecretKeySpec(keyBytes, "AES")
        val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
        val ciphertext = Base64.decode(ciphertextBase64, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        cipher.updateAAD("$groupId|$senderId".toByteArray(Charsets.UTF_8))
        val plaintextBytes = cipher.doFinal(ciphertext)
        String(plaintextBytes, Charsets.UTF_8)
    }

    private fun wrapPrivateKey(uid: String, privateKey: PrivateKey): String {
        val raw = privateKey.encoded
        return try {
            val masterKey = getOrCreateAndroidKeyStoreKey(uid)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, masterKey)
            val iv = cipher.iv
            val encrypted = cipher.doFinal(raw)
            "KS1:${Base64.encodeToString(iv, Base64.NO_WRAP)}:${Base64.encodeToString(encrypted, Base64.NO_WRAP)}"
        } catch (_: Exception) {
            "RAW:${Base64.encodeToString(raw, Base64.NO_WRAP)}"
        }
    }

    private fun unwrapPrivateKey(uid: String, stored: String): PrivateKey? = runCatching {
        val pkcs8Bytes = when {
            stored.startsWith("KS1:") -> {
                val parts = stored.removePrefix("KS1:").split(":")
                val iv = Base64.decode(parts[0], Base64.NO_WRAP)
                val encrypted = Base64.decode(parts[1], Base64.NO_WRAP)
                val masterKey = getOrCreateAndroidKeyStoreKey(uid)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, masterKey, GCMParameterSpec(128, iv))
                cipher.doFinal(encrypted)
            }
            stored.startsWith("RAW:") -> Base64.decode(stored.removePrefix("RAW:"), Base64.NO_WRAP)
            else -> Base64.decode(stored, Base64.NO_WRAP)
        }
        KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(pkcs8Bytes))
    }.getOrNull()

    private fun getOrCreateAndroidKeyStoreKey(uid: String): SecretKey {
        val alias = "ciphertalk_master_$uid"
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }

        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGen.init(spec)
        return keyGen.generateKey()
    }

    companion object {
        const val ALG_NAME = "ECDH-P256-HKDF-AES256GCM"
        const val GROUP_ALG_NAME = "GROUP-AES256GCM"
    }
}
