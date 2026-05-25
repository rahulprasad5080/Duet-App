package com.karaoke.poc.mixer

import android.content.Context
import android.media.MediaMetadataRetriever
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File
import java.util.Locale

interface MergeListener {
    fun onMergeStarted()
    fun onMergeSuccess(outputFile: File)
    fun onMergeFailed(errorMessage: String)
}

class VideoMerger(val context: Context) {

    companion object {
        const val DEFAULT_MIC_VOLUME = 2.0
        const val DEFAULT_BACKING_VOLUME = 0.3
    }

    fun merge(
        recordedVideo: File,
        backingAudio: File,
        offsetMs: Long,
        durationMs: Long,
        micVolume: Double,
        backingVolume: Double,
        listener: MergeListener
    ) {
        // Clean old output files first to save storage
        OutputComposer().cleanOldMergedOutputs(context)

        // Generate unique fresh file path to prevent OS/ExoPlayer file-locking errors
        val outputFile = OutputComposer().getUniqueMergedOutputFile(context)

        val command = buildCommand(
            recordedVideo = recordedVideo,
            backingAudio = backingAudio,
            outputFile = outputFile,
            offsetMs = offsetMs,
            durationMs = durationMs,
            recordedVideoHasAudio = hasAudioTrack(recordedVideo),
            micVolume = micVolume,
            backingVolume = backingVolume
        )

        listener.onMergeStarted()

        FFmpegKit.executeAsync(command) { session ->
            val returnCode = session.returnCode
            if (ReturnCode.isSuccess(returnCode) && outputFile.exists() && outputFile.length() > 0L) {
                listener.onMergeSuccess(outputFile)
            } else {
                val failStackTrace = session.failStackTrace ?: "No stack trace"
                val errorMessage = "FFmpeg execution failed with return code: $returnCode. Stacktrace: $failStackTrace. Command: $command"
                listener.onMergeFailed(errorMessage)
            }
        }
    }

    private fun buildCommand(
        recordedVideo: File,
        backingAudio: File,
        outputFile: File,
        offsetMs: Long,
        durationMs: Long,
        recordedVideoHasAudio: Boolean,
        micVolume: Double,
        backingVolume: Double
    ): String {
        val durationSeconds = formatSeconds(durationMs.coerceAtLeast(1L) / 1000.0)
        val videoInput = "-i ${quote(recordedVideo.absolutePath)}"
        val backingInput = if (offsetMs >= 0) {
            "-ss ${formatSeconds(offsetMs / 1000.0)} -t $durationSeconds -i ${quote(backingAudio.absolutePath)}"
        } else {
            "-i ${quote(backingAudio.absolutePath)}"
        }
        val delayedBackingFilter = if (offsetMs < 0) {
            val delayMs = -offsetMs
            "[1:a]adelay=$delayMs:all=1,volume=$backingVolume[backing]"
        } else {
            "[1:a]volume=$backingVolume[backing]"
        }

        return if (recordedVideoHasAudio) {
            listOf(
                "-y",
                videoInput,
                backingInput,
                "-filter_complex",
                quote("[0:a]volume=$micVolume[mic];$delayedBackingFilter;[mic][backing]amix=inputs=2:duration=first:dropout_transition=0,alimiter=limit=0.95[a]"),
                "-map 0:v:0",
                "-map ${quote("[a]")}",
                "-c:v copy",
                "-c:a aac",
                "-shortest",
                quote(outputFile.absolutePath)
            ).joinToString(" ")
        } else {
            val audioMapping = if (offsetMs < 0) {
                listOf(
                    "-filter_complex",
                    quote("$delayedBackingFilter;[backing]alimiter=limit=0.95[a]"),
                    "-map 0:v:0",
                    "-map ${quote("[a]")}"
                )
            } else {
                listOf(
                    "-filter_complex",
                    quote("[1:a]volume=$backingVolume,alimiter=limit=0.95[a]"),
                    "-map 0:v:0",
                    "-map ${quote("[a]")}"
                )
            }

            (listOf("-y", videoInput, backingInput) + audioMapping + listOf(
                "-c:v copy",
                "-c:a aac",
                "-shortest",
                quote(outputFile.absolutePath)
            )).joinToString(" ")
        }
    }

    private fun hasAudioTrack(file: File): Boolean {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
        } catch (_: Exception) {
            true
        } finally {
            retriever.release()
        }
    }

    private fun formatSeconds(value: Double): String {
        return String.format(Locale.US, "%.3f", value)
    }

    private fun quote(value: String): String {
        return "\"${value.replace("\"", "\\\"")}\""
    }
}
