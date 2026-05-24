package com.karaoke.poc.sync

import java.io.File

object SyncEngine {
    var audioPlayStartTimeNs: Long = 0L
    var videoRecordStartTimeNs: Long = 0L
    var recordingDurationMs: Long = 0L
    var recordedVideoFile: File? = null
    var backingAudioFile: File? = null

    fun getOffsetMs(): Long {
        if (videoRecordStartTimeNs == 0L || audioPlayStartTimeNs == 0L) return 0L
        return (videoRecordStartTimeNs - audioPlayStartTimeNs) / 1_000_000
    }

    fun reset() {
        audioPlayStartTimeNs = 0L
        videoRecordStartTimeNs = 0L
        recordingDurationMs = 0L
        recordedVideoFile = null
        backingAudioFile = null
    }
}
