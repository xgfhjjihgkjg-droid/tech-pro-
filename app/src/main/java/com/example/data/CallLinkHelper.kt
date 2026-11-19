package com.example.data

import android.net.Uri
import java.util.UUID

data class ParsedCallLink(
    val hostUid: String,
    val hostName: String,
    val callType: CallType,
    val code: String
)

object CallLinkHelper {
    fun buildCallLink(
        hostUid: String,
        hostName: String,
        callType: CallType,
        code: String = UUID.randomUUID().toString().replace("-", "").take(8).uppercase()
    ): String {
        return Uri.Builder()
            .scheme("toker")
            .authority("call")
            .appendQueryParameter("host", hostUid)
            .appendQueryParameter("type", callType.name)
            .appendQueryParameter("name", hostName.take(40))
            .appendQueryParameter("code", code)
            .build()
            .toString()
    }

    fun buildShareableMessage(
        hostUid: String,
        hostName: String,
        callType: CallType
    ): String {
        val link = buildCallLink(hostUid, hostName, callType)
        val kind = if (callType == CallType.VIDEO) "video" else "audio"
        return "Join my encrypted ToKer $kind call: $link"
    }

    fun parseCallLink(rawInput: String): ParsedCallLink? {
        val trimmed = rawInput.trim()
        if (trimmed.isBlank()) return null
        val extracted = Regex("toker://call\\S+").find(trimmed)?.value ?: trimmed
        return runCatching {
            val uri = Uri.parse(extracted)
            if (uri.scheme != "toker" || uri.host != "call") return@runCatching null
            val hostUid = uri.getQueryParameter("host")?.trim().orEmpty()
            if (hostUid.isBlank()) return@runCatching null
            val type = CallType.fromRaw(uri.getQueryParameter("type") ?: CallType.VIDEO.name)
            val name = uri.getQueryParameter("name")?.trim().orEmpty()
            val code = uri.getQueryParameter("code")?.trim().orEmpty()
            ParsedCallLink(
                hostUid = hostUid,
                hostName = name,
                callType = type,
                code = code
            )
        }.getOrNull()
    }
}
