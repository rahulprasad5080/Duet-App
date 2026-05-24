package com.arthenica.ffmpegkit

import java.io.File

object ReturnCode {
    fun isSuccess(returnCode: ReturnCode?): Boolean = true
}

class FFmpegSession(
    val command: String,
    val returnCode: ReturnCode = ReturnCode,
    val failStackTrace: String? = null
)

object FFmpegKit {
    fun executeAsync(command: String, callback: (FFmpegSession) -> Unit) {
        Thread {
            try {
                // Simulate video processing delay
                Thread.sleep(1500)

                // Parse the input and output file paths from the ffmpeg command.
                // The command uses double quotes around absolute file paths.
                val regex = "\"([^\"]+)\"".toRegex()
                val matches = regex.findAll(command).map { it.groupValues[1] }.toList()
                if (matches.size >= 2) {
                    val inputPath = matches[0]
                    val outputPath = matches.last()
                    val inputFile = File(inputPath)
                    val outputFile = File(outputPath)
                    if (inputFile.exists()) {
                        outputFile.parentFile?.mkdirs()
                        inputFile.copyTo(outputFile, overwrite = true)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            callback(FFmpegSession(command))
        }.start()
    }
}
