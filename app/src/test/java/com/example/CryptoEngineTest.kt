package com.example

import com.example.crypto.CryptoEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CryptoEngineTest {

    @Test
    fun ecdhKeyAgreementAndAesGcmRoundTripSucceeds() {
        val engine = CryptoEngine()
        val alice = engine.generateEcKeyPair()
        val bob = engine.generateEcKeyPair()
        val alicePub = engine.encodePublicKey(alice.public)
        val bobPub = engine.encodePublicKey(bob.public)
        val convId = "alice_bob"

        val encrypted = engine.encryptMessage(
            plaintext = "Meet at Vault 7 at 19:30 UTC!",
            myPrivateKey = alice.private,
            peerPublicKeyBase64 = bobPub,
            conversationId = convId,
            senderId = "alice"
        )

        assertNotEquals("Meet at Vault 7 at 19:30 UTC!", encrypted.ciphertextBase64)

        val decryptedByBob = engine.decryptMessage(
            ciphertextBase64 = encrypted.ciphertextBase64,
            ivBase64 = encrypted.ivBase64,
            myPrivateKey = bob.private,
            peerPublicKeyBase64 = alicePub,
            conversationId = convId,
            senderId = "alice"
        )
        assertTrue(decryptedByBob.isSuccess)
        assertEquals("Meet at Vault 7 at 19:30 UTC!", decryptedByBob.getOrNull())

        // Sender can also decrypt their own sent message using peer's public key
        val decryptedByAlice = engine.decryptMessage(
            ciphertextBase64 = encrypted.ciphertextBase64,
            ivBase64 = encrypted.ivBase64,
            myPrivateKey = alice.private,
            peerPublicKeyBase64 = bobPub,
            conversationId = convId,
            senderId = "alice"
        )
        assertTrue(decryptedByAlice.isSuccess)
        assertEquals("Meet at Vault 7 at 19:30 UTC!", decryptedByAlice.getOrNull())
    }

    @Test
    fun tamperedSenderOrConversationAadFailsAuthenticationTag() {
        val engine = CryptoEngine()
        val alice = engine.generateEcKeyPair()
        val bob = engine.generateEcKeyPair()
        val alicePub = engine.encodePublicKey(alice.public)
        val bobPub = engine.encodePublicKey(bob.public)

        val encrypted = engine.encryptMessage(
            plaintext = "Authenticated message",
            myPrivateKey = alice.private,
            peerPublicKeyBase64 = bobPub,
            conversationId = "conv_1",
            senderId = "alice"
        )

        val tampered = engine.decryptMessage(
            ciphertextBase64 = encrypted.ciphertextBase64,
            ivBase64 = encrypted.ivBase64,
            myPrivateKey = bob.private,
            peerPublicKeyBase64 = alicePub,
            conversationId = "conv_1",
            senderId = "eve"
        )
        assertTrue(tampered.isFailure)
    }

    @Test
    fun safetyNumberIsSymmetricBetweenPeers() {
        val engine = CryptoEngine()
        val alicePub = engine.encodePublicKey(engine.generateEcKeyPair().public)
        val bobPub = engine.encodePublicKey(engine.generateEcKeyPair().public)

        val sn1 = engine.computeSafetyNumber(alicePub, bobPub)
        val sn2 = engine.computeSafetyNumber(bobPub, alicePub)
        assertEquals(sn1, sn2)
        assertEquals(60 + 11, sn1.length) // 12 blocks of 5 digits + 11 spaces
    }
}
