package com.example

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppFontStyle
import com.example.data.AppLanguage
import com.example.data.AppThemeMode
import com.example.data.BackupManager
import com.example.data.CallLinkHelper
import com.example.data.CallStatus
import com.example.data.CallType
import com.example.data.Conversation
import com.example.data.ConversationListItemUi
import com.example.data.DirectoryUser
import com.example.data.EncryptedMessage
import com.example.data.GroupChat
import com.example.data.GroupListItemUi
import com.example.data.GroupMessage
import com.example.data.PresenceStatus
import com.example.data.PrivacyAudience
import com.example.data.PrivateAccount
import com.example.data.RichMessageCodec
import com.example.data.RichMessagePayload
import com.example.data.RichMessageType
import com.example.data.ScheduledMessageItem
import com.example.data.ScheduledMessageSerializer
import com.example.data.ScheduledMessageStatus
import com.example.data.SecurityPinHasher
import com.example.data.SecurityPreferencesRepository
import com.example.data.SecuritySettings
import com.example.data.ThemeSettings
import com.example.data.stringsForLanguage
import com.example.notifications.ToKerNotificationHelper
import com.example.ui.components.formatDisappearingLabel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ToKerFeatureTests {

    @Test
    fun `rich message codec round-trips document reply and forwarded flags`() {
        val original = RichMessagePayload(
            type = RichMessageType.DOCUMENT.name,
            text = "Q4 Security Audit",
            mediaBase64 = "JVBERi0xLjQK",
            docName = "audit_report.pdf",
            docMimeType = "application/pdf",
            docSizeBytes = 2560L,
            replyToMessageId = "msg_123",
            replyToSenderName = "Aarav",
            replyToPreview = "Can you share the PDF?",
            forwarded = true
        )
        val encoded = RichMessageCodec.encode(original)
        assertTrue(encoded.startsWith("CT1:"))

        val decoded = RichMessageCodec.decode(encoded)
        assertEquals(RichMessageType.DOCUMENT, decoded.typeEnum)
        assertEquals("audit_report.pdf", decoded.docName)
        assertEquals("application/pdf", decoded.docMimeType)
        assertEquals(2560L, decoded.docSizeBytes)
        assertEquals("msg_123", decoded.replyToMessageId)
        assertEquals("Aarav", decoded.replyToSenderName)
        assertTrue(decoded.forwarded)
        assertEquals("↪️ 📄 audit_report.pdf", RichMessageCodec.previewLabel(decoded))
        assertEquals("2.5 KB", RichMessageCodec.formatFileSize(2560L))
    }

    @Test
    fun `security pin hasher verifies valid pin and rejects wrong pin`() {
        val salt = SecurityPinHasher.generateSalt()
        val hash = SecurityPinHasher.hashPin("246810", salt)

        assertTrue(SecurityPinHasher.verifyPin("246810", salt, hash))
        assertFalse(SecurityPinHasher.verifyPin("246811", salt, hash))
        assertFalse(SecurityPinHasher.verifyPin("", salt, hash))
        assertNotEquals(hash, SecurityPinHasher.hashPin("246810", SecurityPinHasher.generateSalt()))
    }

    @Test
    fun `scheduled message serializer preserves queue items`() {
        val item = ScheduledMessageItem(
            id = "sched_01",
            ownerUid = "user_a",
            targetType = "CONVERSATION",
            targetId = "conv_ab",
            peerId = "user_b",
            targetTitle = "Riya",
            text = "Happy Birthday! 🎉",
            scheduledAtMillis = 1_800_000_000_000L,
            createdAtMillis = 1_799_999_000_000L,
            status = ScheduledMessageStatus.PENDING.name
        )
        val encoded = ScheduledMessageSerializer.encodeList(listOf(item))
        val decoded = ScheduledMessageSerializer.decodeList(encoded)

        assertEquals(1, decoded.size)
        assertEquals("sched_01", decoded.first().id)
        assertEquals("Happy Birthday! 🎉", decoded.first().text)
        assertEquals(ScheduledMessageStatus.PENDING, decoded.first().statusEnum)
        assertEquals(emptyList<ScheduledMessageItem>(), ScheduledMessageSerializer.decodeList("not-json"))
    }

    @Test
    fun `call link helper builds and parses video and audio links`() {
        val message = CallLinkHelper.buildShareableMessage(
            hostUid = "uid_host_99",
            hostName = "Priya Sharma",
            callType = CallType.VIDEO
        )
        assertTrue(message.contains("toker://call?"))

        val parsed = CallLinkHelper.parseCallLink(message)
        assertNotNull(parsed)
        assertEquals("uid_host_99", parsed?.hostUid)
        assertEquals("Priya Sharma", parsed?.hostName)
        assertEquals(CallType.VIDEO, parsed?.callType)

        assertNull(CallLinkHelper.parseCallLink("https://example.com/not-toker"))
    }

    @Test
    fun `backup manager exports and parses contacts privacy and scheduled items`() {
        val profile = DirectoryUser(
            uid = "u1",
            displayName = "Kabir",
            handle = "@kabir",
            statusMessage = "Busy building",
            statusMood = "In Focus",
            discoverable = false,
            lastSeenVisibility = PrivacyAudience.CONTACTS.name,
            photoVisibility = PrivacyAudience.CONTACTS.name,
            statusVisibility = PrivacyAudience.NOBODY.name
        )
        val priv = PrivateAccount(
            userId = "u1",
            savedContactIds = listOf("u2", "u3"),
            blockedUserIds = listOf("u9"),
            verifiedPeerIds = listOf("u2"),
            readReceiptsEnabled = false,
            lastSeenVisibility = PrivacyAudience.CONTACTS.name,
            photoVisibility = PrivacyAudience.CONTACTS.name,
            statusVisibility = PrivacyAudience.NOBODY.name
        )
        val convUi = ConversationListItemUi(
            conversation = Conversation(id = "u1_u2", participantIds = listOf("u1", "u2")),
            peerId = "u2",
            peerName = "Neha",
            peerPhotoUrl = null,
            peerPresence = PresenceStatus.ONLINE,
            peerStatusMessage = "",
            peerPublicKey = "pk",
            lastMessagePreview = "Hello!",
            isLastMessageEncrypted = true
        )
        val groupUi = GroupListItemUi(
            group = GroupChat(id = "grp_1", name = "Family", memberIds = listOf("u1", "u2")),
            memberCount = 2,
            lastMessagePreview = "Dinner at 8"
        )
        val sched = ScheduledMessageItem(
            id = "sched_9",
            ownerUid = "u1",
            targetType = "GROUP",
            targetId = "grp_1",
            targetTitle = "Family",
            text = "Reminder",
            scheduledAtMillis = 1_800_000_000_000L,
            createdAtMillis = 1_799_999_000_000L
        )

        val json = BackupManager.buildBackupJson(profile, priv, listOf(convUi), listOf(groupUi), listOf(sched))
        val parsed = BackupManager.parseBackupJson(json).getOrThrow()

        assertEquals("ToKer", parsed.app)
        assertEquals("u1", parsed.uid)
        assertFalse(parsed.privacy.discoverable)
        assertFalse(parsed.privacy.readReceiptsEnabled)
        assertEquals(PrivacyAudience.CONTACTS.name, parsed.privacy.lastSeenVisibility)
        assertEquals(PrivacyAudience.NOBODY.name, parsed.privacy.statusVisibility)
        assertEquals(listOf("u2", "u3"), parsed.savedContactIds)
        assertEquals(listOf("u9"), parsed.blockedUserIds)
        assertEquals(1, parsed.conversations.size)
        assertEquals(1, parsed.groups.size)
        assertEquals(1, parsed.scheduledMessages.size)
    }

    @Test
    fun `notification helper creates all channels and masks locked chat previews`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ToKerNotificationHelper.ensureNotificationChannels(context)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelIds = nm.notificationChannels.map { it.id }.toSet()
        assertTrue(ToKerNotificationHelper.CHANNEL_MESSAGES in channelIds)
        assertTrue(ToKerNotificationHelper.CHANNEL_CALLS in channelIds)
        assertTrue(ToKerNotificationHelper.CHANNEL_SCHEDULED in channelIds)

        assertEquals(
            "New end-to-end encrypted message",
            ToKerNotificationHelper.buildDirectMessageContentText("Secret OTP 1234", maskContent = true)
        )
        assertEquals(
            "Hey, meet at 5?",
            ToKerNotificationHelper.buildDirectMessageContentText("Hey, meet at 5?", maskContent = false)
        )
        assertEquals(
            "New encrypted message",
            ToKerNotificationHelper.buildDirectMessageContentText("   ", maskContent = false)
        )
    }

    @Test
    fun `security preferences defaults notifications enabled and toggles cleanly`() = runTest {
        assertTrue(SecuritySettings().notificationsEnabled)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = SecurityPreferencesRepository(context)

        assertTrue(repo.securitySettings.first().notificationsEnabled)
        repo.setNotificationsEnabled(false)
        assertFalse(repo.securitySettings.first().notificationsEnabled)
        repo.setNotificationsEnabled(true)
        assertTrue(repo.securitySettings.first().notificationsEnabled)
    }

    @Test
    fun `font styles contain at least twenty distinct styles and call missed is terminal`() {
        assertTrue(AppFontStyle.entries.size >= 20)
        assertEquals(25, AppFontStyle.entries.size)
        assertEquals(AppFontStyle.PACIFICO, AppFontStyle.fromRaw("PACIFICO"))
        assertEquals(AppFontStyle.CLASSIC, AppFontStyle.fromRaw("UNKNOWN_STYLE"))
        assertEquals(AppFontStyle.CLASSIC, AppFontStyle.fromRaw(null))

        assertTrue(CallStatus.MISSED.isTerminal)
        assertTrue(CallStatus.ENDED.isTerminal)
        assertTrue(CallStatus.DECLINED.isTerminal)
        assertFalse(CallStatus.RINGING.isTerminal)
        assertFalse(CallStatus.CONNECTED.isTerminal)
    }

    @Test
    fun `theme defaults to light mode and supports all twenty two scheduled indian languages`() {
        assertEquals(AppThemeMode.LIGHT, ThemeSettings().themeMode)
        assertEquals(AppThemeMode.LIGHT, AppThemeMode.fromRaw(null))
        assertEquals(AppThemeMode.DARK, AppThemeMode.fromRaw("DARK"))

        // English + 22 Scheduled Indian Languages = 23 languages
        assertEquals(23, AppLanguage.entries.size)
        val expectedCodes = setOf(
            "en", "hi", "bn", "te", "mr", "ta", "ur", "gu", "kn", "or",
            "ml", "pa", "as", "mai", "sat", "ks", "ne", "kok", "sd", "doi",
            "mni", "brx", "sa"
        )
        assertEquals(expectedCodes, AppLanguage.entries.map { it.code }.toSet())
        assertEquals(AppLanguage.HINDI, AppLanguage.fromCode("hi"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromCode("unknown_code"))

        AppLanguage.entries.forEach { lang ->
            val strings = stringsForLanguage(lang.code)
            assertTrue(strings.tabChats.isNotBlank())
            assertTrue(strings.settingsTitle.isNotBlank())
            assertTrue(strings.onlyAdminsCanMessageBanner.isNotBlank())
        }
    }

    @Test
    fun `sticker and gif codec round trips and group admin or disappearing models have safe defaults`() {
        val stickerPayload = RichMessagePayload(
            type = RichMessageType.STICKER.name,
            text = "Namaste",
            stickerId = "namaste"
        )
        val encodedSticker = RichMessageCodec.encode(stickerPayload)
        val decodedSticker = RichMessageCodec.decode(encodedSticker)
        assertEquals(RichMessageType.STICKER, decodedSticker.typeEnum)
        assertEquals("namaste", decodedSticker.stickerId)
        assertEquals("✨ Namaste", RichMessageCodec.previewLabel(decodedSticker))

        val gifPayload = RichMessagePayload(
            type = RichMessageType.GIF.name,
            text = "Party Celebration",
            gifId = "party"
        )
        val encodedGif = RichMessageCodec.encode(gifPayload)
        val decodedGif = RichMessageCodec.decode(encodedGif)
        assertEquals(RichMessageType.GIF, decodedGif.typeEnum)
        assertEquals("party", decodedGif.gifId)
        assertEquals("🎞️ Party Celebration", RichMessageCodec.previewLabel(decodedGif))

        val group = GroupChat(
            id = "g1",
            name = "Admins Group",
            createdBy = "u_creator",
            adminIds = listOf("u_creator", "u_mod"),
            memberIds = listOf("u_creator", "u_mod", "u_member"),
            onlyAdminsCanMessage = true,
            disappearingSeconds = 3600L
        )
        assertTrue(group.isAdmin("u_creator"))
        assertTrue(group.isAdmin("u_mod"))
        assertFalse(group.isAdmin("u_member"))
        assertTrue(group.onlyAdminsCanMessage)
        assertEquals(3600L, group.disappearingSeconds)

        assertEquals(0L, Conversation().disappearingSeconds)
        assertNull(EncryptedMessage().expiresAtMillis)
        assertNull(GroupMessage().expiresAtMillis)
        assertEquals("Off", formatDisappearingLabel(0L))
        assertEquals("5m", formatDisappearingLabel(300L))
        assertEquals("1h", formatDisappearingLabel(3600L))
        assertEquals("7d", formatDisappearingLabel(604800L))
    }
}
