package com.karaoke.poc.sync

import java.io.File

object SyncEngine {
    var audioPlayStartTimeMs: Long = 0L
    var videoRecordStartTimeMs: Long = 0L
    var recordingDurationMs: Long = 0L
    var recordedVideoFile: File? = null
    var backingAudioFile: File? = null

    fun getOffsetMs(): Long {
        return videoRecordStartTimeMs - audioPlayStartTimeMs
    }

    fun reset() {
        audioPlayStartTimeMs = 0L
        videoRecordStartTimeMs = 0L
        recordingDurationMs = 0L
        recordedVideoFile = null
        backingAudioFile = null
    }
}
