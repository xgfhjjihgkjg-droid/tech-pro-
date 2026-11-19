package com.example.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.roundToInt

data class EncodedVideoMedia(
    val videoBase64: String,
    val thumbBase64: String,
    val durationMs: Long
)

data class EncodedVoiceNote(
    val audioBase64: String,
    val durationMs: Long
)

class VoiceNoteRecorder {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAtMs: Long = 0L

    val isRecording: Boolean
        get() = recorder != null

    fun start(context: Context, maxRawBytes: Int = 360_000): Result<Unit> {
        cancel()
        return try {
            val file = File.createTempFile("toker_voice_", ".m4a", context.cacheDir)
            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mr.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioChannels(1)
                setAudioSamplingRate(16_000)
                setAudioEncodingBitRate(24_000)
                setMaxDuration(120_000)
                setMaxFileSize(maxRawBytes.toLong())
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = mr
            outputFile = file
            startedAtMs = System.currentTimeMillis()
            Result.success(Unit)
        } catch (e: Exception) {
            cancel()
            Result.failure(IllegalStateException(e.message ?: "Unable to start microphone recording."))
        }
    }

    suspend fun stopAndEncode(maxRawBytes: Int = 360_000): Result<EncodedVoiceNote> =
        withContext(Dispatchers.IO) {
            val mr = recorder
            val file = outputFile
            val elapsedMs = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(0L)
            recorder = null
            outputFile = null
            startedAtMs = 0L

            if (mr == null || file == null) {
                return@withContext Result.failure(IllegalStateException("No active voice recording."))
            }

            try {
                mr.stop()
                mr.release()
            } catch (_: Exception) {
                runCatching { mr.release() }
                runCatching { file.delete() }
                return@withContext Result.failure(
                    IllegalArgumentException("Voice note too short. Record for at least 1 second.")
                )
            }

            try {
                if (!file.exists() || file.length() <= 0L) {
                    return@withContext Result.failure(IllegalArgumentException("Voice recording was empty."))
                }
                if (file.length() > maxRawBytes) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Voice note exceeds encrypted size budget (${maxRawBytes / 1024} KB).")
                    )
                }
                val bytes = file.readBytes()
                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                Result.success(
                    EncodedVoiceNote(
                        audioBase64 = base64,
                        durationMs = elapsedMs.coerceIn(500L, 120_000L)
                    )
                )
            } catch (e: Exception) {
                Result.failure(IllegalStateException(e.message ?: "Failed to encode voice note."))
            } finally {
                runCatching { file.delete() }
            }
        }

    fun cancel() {
        val mr = recorder
        val file = outputFile
        recorder = null
        outputFile = null
        startedAtMs = 0L
        runCatching { mr?.stop() }
        runCatching { mr?.release() }
        runCatching { file?.delete() }
    }
}

object MediaUtils {

    suspend fun compressImageToBase64(
        context: Context,
        uri: Uri,
        maxDimension: Int = 900,
        targetBytes: Int = 240_000,
        maxBase64Chars: Int = 340_000
    ): String? = withContext(Dispatchers.IO) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

            var sampleSize = 1
            val rawMax = max(bounds.outWidth, bounds.outHeight)
            while (rawMax / sampleSize > maxDimension * 2) {
                sampleSize *= 2
            }

            val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, opts)
            } ?: return@withContext null

            val encoded = compressBitmapToJpegBase64(
                bitmap = decoded,
                maxDimension = maxDimension,
                targetBytes = targetBytes,
                maxBase64Chars = maxBase64Chars
            )
            decoded.recycle()
            encoded
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private fun compressBitmapToJpegBase64(
        bitmap: Bitmap,
        maxDimension: Int,
        targetBytes: Int,
        maxBase64Chars: Int
    ): String? {
        val scale = maxDimension.toFloat() / max(bitmap.width, bitmap.height).toFloat()
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).roundToInt().coerceAtLeast(1),
                (bitmap.height * scale).roundToInt().coerceAtLeast(1),
                true
            )
        } else {
            bitmap
        }

        val baos = ByteArrayOutputStream()
        var quality = 82
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        while (baos.size() > targetBytes && quality > 30) {
            baos.reset()
            quality -= 10
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        }
        if (scaled !== bitmap) {
            scaled.recycle()
        }
        val encoded = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        return if (encoded.length <= maxBase64Chars) encoded else null
    }

    suspend fun processPickedVideoToBase64(
        context: Context,
        uri: Uri,
        maxRawBytes: Int = 320_000
    ): Result<EncodedVideoMedia> = withContext(Dispatchers.IO) {
        var retriever: MediaMetadataRetriever? = null
        val tempFile = File.createTempFile("ct_trim_", ".mp4", context.cacheDir)
        try {
            retriever = MediaMetadataRetriever().apply {
                setDataSource(context, uri)
            }
            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 3_000L

            val posterBitmap = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
            val thumbBase64 = if (posterBitmap != null) {
                val b64 = compressBitmapToJpegBase64(
                    bitmap = posterBitmap,
                    maxDimension = 420,
                    targetBytes = 36_000,
                    maxBase64Chars = 52_000
                )
                posterBitmap.recycle()
                b64.orEmpty()
            } else {
                ""
            }

            val rawBytes = context.contentResolver.openInputStream(uri)?.use { stream ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(16_384)
                var total = 0
                while (true) {
                    val read = stream.read(buf)
                    if (read <= 0) break
                    total += read
                    if (total > maxRawBytes) {
                        return@use null
                    }
                    out.write(buf, 0, read)
                }
                out.toByteArray()
            }

            val finalBytes: ByteArray
            val effectiveDurationMs: Long
            if (rawBytes != null && rawBytes.isNotEmpty()) {
                finalBytes = rawBytes
                effectiveDurationMs = durationMs
            } else {
                val trimResult = trimVideoToBudget(context, uri, tempFile, maxRawBytes)
                if (trimResult == null) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Video exceeds inline budget (${maxRawBytes / 1024} KB). Pick a shorter/smaller clip.")
                    )
                }
                finalBytes = trimResult.first
                effectiveDurationMs = trimResult.second.coerceAtLeast(1000L)
            }

            val videoBase64 = Base64.encodeToString(finalBytes, Base64.NO_WRAP)
            Result.success(
                EncodedVideoMedia(
                    videoBase64 = videoBase64,
                    thumbBase64 = thumbBase64,
                    durationMs = effectiveDurationMs
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException(e.message ?: "Failed to process selected video."))
        } finally {
            runCatching { retriever?.release() }
            runCatching { tempFile.delete() }
        }
    }

    private fun trimVideoToBudget(
        context: Context,
        uri: Uri,
        outputFile: File,
        maxBytes: Int
    ): Pair<ByteArray, Long>? {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        return try {
            extractor.setDataSource(context, uri, null)
            val trackMap = mutableMapOf<Int, Int>()
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    extractor.selectTrack(i)
                    trackMap[i] = muxer.addTrack(format)
                }
            }
            if (trackMap.isEmpty()) return null

            muxer.start()
            val buffer = ByteBuffer.allocate(256 * 1024)
            val bufferInfo = MediaCodec.BufferInfo()
            var approxBytes = 2_048L
            val maxClipUs = 60_000_000L
            var firstPtsUs = -1L
            var lastPtsUs = 0L

            while (true) {
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                val trackIndex = extractor.sampleTrackIndex
                val muxTrack = trackMap[trackIndex]
                if (muxTrack == null) {
                    extractor.advance()
                    continue
                }
                val pts = extractor.sampleTime
                if (firstPtsUs < 0L) firstPtsUs = pts
                val elapsedUs = (pts - firstPtsUs).coerceAtLeast(0L)
                if (elapsedUs > maxClipUs || approxBytes + sampleSize > maxBytes - 4_096) {
                    if (approxBytes > 16_384L) break
                }

                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = pts
                @Suppress("DEPRECATION")
                bufferInfo.flags = extractor.sampleFlags
                muxer.writeSampleData(muxTrack, buffer, bufferInfo)
                approxBytes += sampleSize
                lastPtsUs = elapsedUs
                extractor.advance()
            }

            muxer.stop()
            muxer.release()
            muxer = null
            extractor.release()

            if (!outputFile.exists() || outputFile.length() <= 0L || outputFile.length() > maxBytes) {
                null
            } else {
                outputFile.readBytes() to (lastPtsUs / 1000L)
            }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { muxer?.release() }
            runCatching { extractor.release() }
        }
    }

    suspend fun writeVideoBase64ToCacheFile(
        context: Context,
        cacheKey: String,
        videoBase64: String
    ): File? = withContext(Dispatchers.IO) {
        try {
            val safeKey = cacheKey.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "clip" }
            val outFile = File(context.cacheDir, "ct_vid_$safeKey.mp4")
            if (!outFile.exists() || outFile.length() == 0L) {
                val decoded = Base64.decode(videoBase64, Base64.DEFAULT)
                outFile.writeBytes(decoded)
            }
            outFile
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    suspend fun writeAudioBase64ToCacheFile(
        context: Context,
        cacheKey: String,
        audioBase64: String
    ): File? = withContext(Dispatchers.IO) {
        try {
            val safeKey = cacheKey.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "voice" }
            val outFile = File(context.cacheDir, "ct_aud_$safeKey.m4a")
            if (!outFile.exists() || outFile.length() == 0L) {
                val decoded = Base64.decode(audioBase64, Base64.DEFAULT)
                outFile.writeBytes(decoded)
            }
            outFile
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    data class EncodedDocumentMedia(
        val fileName: String,
        val mimeType: String,
        val sizeBytes: Long,
        val base64: String
    )

    suspend fun readDocumentToBase64(
        context: Context,
        uri: Uri,
        maxRawBytes: Int = 400_000
    ): Result<EncodedDocumentMedia> = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            var displayName = "ToKer_Document"
            var reportedSize = -1L
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (nameIdx >= 0) {
                        displayName = cursor.getString(nameIdx)?.take(120) ?: displayName
                    }
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                        reportedSize = cursor.getLong(sizeIdx)
                    }
                }
            }
            if (reportedSize > maxRawBytes) {
                return@withContext Result.failure(
                    IllegalArgumentException("Document exceeds encrypted inline budget (${maxRawBytes / 1024} KB). Pick a smaller file.")
                )
            }
            val mimeType = resolver.getType(uri) ?: "application/octet-stream"
            val bytes = resolver.openInputStream(uri)?.use { stream ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(16_384)
                var total = 0
                while (true) {
                    val read = stream.read(buf)
                    if (read <= 0) break
                    total += read
                    if (total > maxRawBytes) {
                        return@withContext Result.failure(
                            IllegalArgumentException("Document exceeds encrypted inline budget (${maxRawBytes / 1024} KB).")
                        )
                    }
                    out.write(buf, 0, read)
                }
                out.toByteArray()
            } ?: return@withContext Result.failure(IllegalArgumentException("Could not open selected document."))

            if (bytes.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("Selected document is empty."))
            }

            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            Result.success(
                EncodedDocumentMedia(
                    fileName = displayName,
                    mimeType = mimeType,
                    sizeBytes = bytes.size.toLong(),
                    base64 = base64
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException(e.message ?: "Failed to read document."))
        }
    }

    suspend fun writeDocumentAndBuildUri(
        context: Context,
        cacheKey: String,
        fileName: String,
        base64: String
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "document.bin" }
            val safeKey = cacheKey.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(12).ifBlank { "doc" }
            val outFile = File(context.cacheDir, "${safeKey}_$safeName")
            val decoded = Base64.decode(base64, Base64.DEFAULT)
            outFile.writeBytes(decoded)
            androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outFile
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }
}

