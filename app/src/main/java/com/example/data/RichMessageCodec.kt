package com.example.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Locale

enum class RichMessageType {
    TEXT,
    EMOJI,
    STICKER,
    GIF,
    IMAGE,
    VIDEO,
    DOCUMENT,
    LOCATION,
    POLL,
    STORY_MENTION,
    CALL_LOG,
    VOICE;

    companion object {
        fun fromRaw(raw: String): RichMessageType =
            entries.firstOrNull { it.name == raw } ?: TEXT
    }
}

@Serializable
data class RichMessagePayload(
    val type: String = RichMessageType.TEXT.name,
    val text: String = "",
    val mediaBase64: String? = null,
    val thumbBase64: String? = null,
    val durationMs: Long = 0L,
    val docName: String = "",
    val docMimeType: String = "",
    val docSizeBytes: Long = 0L,
    val stickerId: String = "",
    val gifId: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationLabel: String = "",
    val pollQuestion: String = "",
    val pollOptions: List<String> = emptyList(),
    val storyMentionId: String = "",
    val storyMentionAuthor: String = "",
    val storyMentionCaption: String = "",
    val storyMentionType: String = StoryType.TEXT.name,
    val replyToMessageId: String = "",
    val replyToSenderName: String = "",
    val replyToPreview: String = "",
    val forwarded: Boolean = false
) {
    val typeEnum: RichMessageType
        get() = RichMessageType.fromRaw(type)
}

object RichMessageCodec {
    private const val PREFIX = "CT1:"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    fun encode(payload: RichMessagePayload): String =
        if (payload.typeEnum == RichMessageType.TEXT &&
            payload.mediaBase64 == null &&
            payload.latitude == null &&
            payload.pollOptions.isEmpty() &&
            payload.storyMentionId.isBlank() &&
            payload.docName.isBlank() &&
            payload.replyToMessageId.isBlank() &&
            !payload.forwarded
        ) {
            payload.text
        } else {
            PREFIX + json.encodeToString(payload)
        }

    fun decode(plaintext: String, fallbackMessageType: String = RichMessageType.TEXT.name): RichMessagePayload {
        if (plaintext.startsWith(PREFIX)) {
            val rawJson = plaintext.removePrefix(PREFIX)
            val parsed = runCatching { json.decodeFromString<RichMessagePayload>(rawJson) }.getOrNull()
            if (parsed != null) return parsed
        }
        return RichMessagePayload(
            type = RichMessageType.fromRaw(fallbackMessageType).name,
            text = plaintext
        )
    }

    fun previewLabel(payload: RichMessagePayload): String {
        val base = when (payload.typeEnum) {
            RichMessageType.TEXT -> payload.text.ifBlank { "Encrypted message" }
            RichMessageType.EMOJI -> payload.text.ifBlank { "😊" }
            RichMessageType.STICKER -> "✨ ${payload.text.ifBlank { "Sticker" }}"
            RichMessageType.GIF -> "🎞️ ${payload.text.ifBlank { "GIF" }}"
            RichMessageType.IMAGE -> if (payload.text.isNotBlank()) "📷 ${payload.text}" else "📷 Photo"
            RichMessageType.VIDEO -> {
                val dur = formatDurationShort(payload.durationMs)
                if (payload.text.isNotBlank()) "🎬 ${payload.text} ($dur)" else "🎬 Video ($dur)"
            }
            RichMessageType.DOCUMENT -> {
                val name = payload.docName.ifBlank { "Document" }
                "📄 $name"
            }
            RichMessageType.LOCATION -> {
                val place = payload.locationLabel.ifBlank { "Live Location" }
                "📍 $place"
            }
            RichMessageType.POLL -> "📊 Poll: ${payload.pollQuestion.ifBlank { "Vote now" }}"
            RichMessageType.STORY_MENTION -> {
                val author = payload.storyMentionAuthor.ifBlank { "Contact" }
                "✨ Mentioned $author's story"
            }
            RichMessageType.CALL_LOG -> payload.text.ifBlank { "📞 Call" }
            RichMessageType.VOICE -> "🎤 Voice message (${formatDurationShort(payload.durationMs)})"
        }
        val prefixed = if (payload.forwarded) "↪️ $base" else base
        return prefixed.take(140)
    }

    fun formatDurationShort(durationMs: Long): String {
        val totalSecs = (durationMs / 1000L).coerceAtLeast(0L)
        val mins = totalSecs / 60L
        val secs = totalSecs % 60L
        return String.format(Locale.US, "%d:%02d", mins, secs)
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return "0 KB"
        return when {
            bytes < 1024L -> "$bytes B"
            bytes < 1024L * 1024L -> "${(bytes / 1024.0).let { String.format(Locale.US, "%.1f", it) }} KB"
            else -> "${(bytes / (1024.0 * 1024.0)).let { String.format(Locale.US, "%.2f", it) }} MB"
        }
    }
}
