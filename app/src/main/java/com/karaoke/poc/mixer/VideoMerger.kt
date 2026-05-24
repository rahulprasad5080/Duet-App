package com.karaoke.poc.mixer

import android.content.Context
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File

interface MergeListener {
    fun onMergeStarted()
    fun onMergeSuccess(outputFile: File)
    fun onMergeFailed(errorMessage: String)
}

class VideoMerger(val context: Context) {

    fun merge(
        recordedVideo: File,
        backingAudio: File,
        offsetMs: Long,
        durationMs: Long,
        listener: MergeListener
    ) {
        val outputFile = OutputComposer().getMergedOutputFile(context)

        // Delete any existing output file to avoid issues
        if (outputFile.exists()) {
            outputFile.delete()
        }

        val command = if (offsetMs >= 0) {
            "ffmpeg -y -i \"${recordedVideo.absolutePath}\" -ss ${offsetMs / 1000.0} -t ${durationMs / 1000.0} -i \"${backingAudio.absolutePath}\" -filter_complex \"[0:a]volume=1.0[mic];[1:a]volume=1.0[backing];[mic][backing]amix=inputs=2:duration=first[a]\" -map 0:v:0 -map \"[a]\" -c:v copy -c:a aac \"${outputFile.absolutePath}\""
        } else {
            "ffmpeg -y -i \"${recordedVideo.absolutePath}\" -i \"${backingAudio.absolutePath}\" -filter_complex \"[1:a]adelay=${-offsetMs}|${-offsetMs}[backing];[0:a]volume=1.0[mic];[mic][backing]amix=inputs=2:duration=first[a]\" -map 0:v:0 -map \"[a]\" -c:v copy -c:a aac \"${outputFile.absolutePath}\""
        }

        listener.onMergeStarted()

        FFmpegKit.executeAsync(command) { session ->
            val returnCode = session.returnCode
            if (ReturnCode.isSuccess(returnCode)) {
                listener.onMergeSuccess(outputFile)
            } else {
                val failStackTrace = session.failStackTrace ?: "No stack trace"
                val errorMessage = "FFmpeg execution failed with return code: $returnCode. Stacktrace: $failStackTrace"
                listener.onMergeFailed(errorMessage)
            }
        }
    }
}
