package com.example

import com.example.crypto.CryptoEngine
import com.example.data.Conversation
import com.example.data.GroupChat
import com.example.data.RichMessageCodec
import com.example.data.RichMessagePayload
import com.example.data.RichMessageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RichMessageCodecTest {

    @Test
    fun richPayloadsRoundTripAcrossImageVideoLocationPollAndStoryMention() {
        val engine = CryptoEngine()
        val alice = engine.generateEcKeyPair()
        val bob = engine.generateEcKeyPair()
        val alicePub = engine.encodePublicKey(alice.public)
        val bobPub = engine.encodePublicKey(bob.public)
        val convId = "alice_bob"

        val samples = listOf(
            RichMessagePayload(
                type = RichMessageType.EMOJI.name,
                text = "🔥"
            ),
            RichMessagePayload(
                type = RichMessageType.IMAGE.name,
                text = "Sunset view",
                mediaBase64 = "ZmFrZV9qcGVnX2RhdGE="
            ),
            RichMessagePayload(
                type = RichMessageType.VIDEO.name,
                text = "Quick clip",
                mediaBase64 = "ZmFrZV9tcDRfZGF0YQ==",
                thumbBase64 = "ZmFrZV90aHVtYg==",
                durationMs = 4500L
            ),
            RichMessagePayload(
                type = RichMessageType.LOCATION.name,
                text = "Central Park",
                latitude = 40.785091,
                longitude = -73.968285,
                locationLabel = "Central Park"
            ),
            RichMessagePayload(
                type = RichMessageType.POLL.name,
                text = "Dinner?",
                pollQuestion = "Dinner?",
                pollOptions = listOf("Tacos", "Sushi", "Ramen")
            ),
            RichMessagePayload(
                type = RichMessageType.STORY_MENTION.name,
                text = "Love this clip!",
                storyMentionId = "story_123",
                storyMentionAuthor = "Alice",
                storyMentionCaption = "Morning run",
                storyMentionType = "VIDEO"
            ),
            RichMessagePayload(
                type = RichMessageType.VOICE.name,
                mediaBase64 = "ZmFrZV9tNGFfZGF0YQ==",
                durationMs = 18_500L,
                replyToMessageId = "msg_prev_1",
                replyToSenderName = "Bob",
                replyToPreview = "Hey!"
            )
        )

        samples.forEach { payload ->
            val encoded = RichMessageCodec.encode(payload)
            val encrypted = engine.encryptMessage(
                plaintext = encoded,
                myPrivateKey = alice.private,
                peerPublicKeyBase64 = bobPub,
                conversationId = convId,
                senderId = "alice"
            )
            val decrypted = engine.decryptMessage(
                ciphertextBase64 = encrypted.ciphertextBase64,
                ivBase64 = encrypted.ivBase64,
                myPrivateKey = bob.private,
                peerPublicKeyBase64 = alicePub,
                conversationId = convId,
                senderId = "alice"
            )
            assertTrue("Decryption failed for ${payload.type}", decrypted.isSuccess)
            val decoded = RichMessageCodec.decode(decrypted.getOrThrow(), payload.type)
            assertEquals(payload.typeEnum, decoded.typeEnum)
            assertEquals(payload.text, decoded.text)
            assertEquals(payload.mediaBase64, decoded.mediaBase64)
            assertEquals(payload.latitude, decoded.latitude)
            assertEquals(payload.longitude, decoded.longitude)
            assertEquals(payload.pollQuestion, decoded.pollQuestion)
            assertEquals(payload.pollOptions, decoded.pollOptions)
            assertEquals(payload.storyMentionId, decoded.storyMentionId)
        }
    }

    @Test
    fun legacyPlaintextDecodesAsTextPayloadGracefully() {
        val decoded = RichMessageCodec.decode("Hello world legacy", RichMessageType.TEXT.name)
        assertEquals(RichMessageType.TEXT, decoded.typeEnum)
        assertEquals("Hello world legacy", decoded.text)
        assertEquals("Hello world legacy", RichMessageCodec.previewLabel(decoded))
    }

    @Test
    fun conversationCustomTitleResolvesPerUserNicknameOrNull() {
        val conv = Conversation(
            id = "alice_bob",
            peerAId = "alice",
            peerAName = "Alice",
            peerBId = "bob",
            peerBName = "Bob",
            nicknames = mapOf(
                "alice" to "  Work Partner  ",
                "bob" to "   "
            )
        )
        assertEquals("Work Partner", conv.customTitleFor("alice"))
        assertNull(conv.customTitleFor("bob"))
        assertNull(conv.customTitleFor("charlie"))
    }

    @Test
    fun editedGroupPayloadPreservesMediaAndOptionsAfterReEncryption() {
        val engine = CryptoEngine()
        val groupKey = engine.generateGroupKeyBase64()
        val originalPoll = RichMessagePayload(
            type = RichMessageType.POLL.name,
            text = "Lunch?",
            pollQuestion = "Lunch?",
            pollOptions = listOf("Pizza", "Salad")
        )
        val editedPoll = originalPoll.copy(
            text = "Team Lunch Spot?",
            pollQuestion = "Team Lunch Spot?"
        )
        val enc = engine.encryptWithGroupKey(
            plaintext = RichMessageCodec.encode(editedPoll),
            groupKeyBase64 = groupKey,
            groupId = "grp_test_1",
            senderId = "alice"
        )
        val dec = engine.decryptWithGroupKey(
            ciphertextBase64 = enc.ciphertextBase64,
            ivBase64 = enc.ivBase64,
            groupKeyBase64 = groupKey,
            groupId = "grp_test_1",
            senderId = "alice"
        )
        assertTrue(dec.isSuccess)
        val decoded = RichMessageCodec.decode(dec.getOrThrow(), RichMessageType.POLL.name)
        assertEquals("Team Lunch Spot?", decoded.pollQuestion)
        assertEquals(listOf("Pizza", "Salad"), decoded.pollOptions)
    }

    @Test
    fun voiceMessagePreviewFormatsDurationAndGroupTracksJoinTimestamps() {
        val voice = RichMessagePayload(
            type = RichMessageType.VOICE.name,
            mediaBase64 = "ZmFrZV92b2ljZQ==",
            durationMs = 62_000L
        )
        assertEquals("🎤 Voice message (1:02)", RichMessageCodec.previewLabel(voice))

        val group = GroupChat(
            id = "grp_1",
            name = "ToKer Squad",
            createdBy = "alice",
            memberIds = listOf("alice", "bob"),
            memberJoinedAt = mapOf("alice" to 1000L, "bob" to 5000L)
        )
        assertEquals(5000L, group.memberJoinedAt["bob"])
        assertNull(group.memberJoinedAt["charlie"])
    }
}
