package com.karaoke.poc.mixer

import android.content.Context
import java.io.File

class OutputComposer {

    fun copyAssetsToFilesDir(context: Context) {
        copyAssetIfNeeded(context, "ekkada.mp3")
        copyAssetIfNeeded(context, "ekkada.rzlrc")
    }

    private fun copyAssetIfNeeded(context: Context, filename: String) {
        val destFile = File(context.filesDir, filename)
        if (!destFile.exists()) {
            try {
                context.assets.open(filename).use { inputStream ->
                    destFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getBackingAudioFile(context: Context): File {
        return File(context.filesDir, "ekkada.mp3")
    }

    fun getBackingLyricsFile(context: Context): File {
        return File(context.filesDir, "ekkada.rzlrc")
    }

    fun getRecordedVideoFile(context: Context): File {
        return File(context.filesDir, "recorded_video.mp4")
    }

    fun getMergedOutputFile(context: Context): File {
        return File(context.filesDir, "final_output.mp4")
    }
}
