package com.karaoke.poc.ui

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.karaoke.poc.mixer.MergeListener
import com.karaoke.poc.mixer.VideoMerger
import java.io.File

enum class MergeState {
    IDLE,
    MERGING,
    SUCCESS,
    ERROR
}

class MixerViewModel : ViewModel() {

    private val _mergeState = MutableLiveData<MergeState>(MergeState.IDLE)
    val mergeState: LiveData<MergeState> = _mergeState

    private val _mergeMessage = MutableLiveData<String>()
    val mergeMessage: LiveData<String> = _mergeMessage

    private val _outputVideoPath = MutableLiveData<String?>()
    val outputVideoPath: LiveData<String?> = _outputVideoPath

    fun startMerge(
        context: Context,
        recordedVideoPath: String,
        backingAudioPath: String,
        offsetMs: Long,
        durationMs: Long,
        micVolume: Double = VideoMerger.DEFAULT_MIC_VOLUME,
        backingVolume: Double = VideoMerger.DEFAULT_BACKING_VOLUME
    ) {
        val recordedVideo = File(recordedVideoPath)
        val backingAudio = File(backingAudioPath)

        if (!recordedVideo.exists()) {
            _mergeState.postValue(MergeState.ERROR)
            _mergeMessage.postValue("Recorded video file not found.")
            return
        }

        if (!backingAudio.exists()) {
            _mergeState.postValue(MergeState.ERROR)
            _mergeMessage.postValue("Backing audio file not found.")
            return
        }

        val videoMerger = VideoMerger(context.applicationContext)
        videoMerger.merge(
            recordedVideo,
            backingAudio,
            offsetMs,
            durationMs,
            micVolume,
            backingVolume,
            object : MergeListener {
                override fun onMergeStarted() {
                    _mergeState.postValue(MergeState.MERGING)
                    _mergeMessage.postValue("Processing sync offset and merging tracks...")
                }

                override fun onMergeSuccess(outputFile: File) {
                    _mergeState.postValue(MergeState.SUCCESS)
                    _mergeMessage.postValue("Render complete!")
                    _outputVideoPath.postValue(outputFile.absolutePath)
                }

                override fun onMergeFailed(errorMessage: String) {
                    _mergeState.postValue(MergeState.ERROR)
                    _mergeMessage.postValue(errorMessage)
                }
            }
        )
    }
}
