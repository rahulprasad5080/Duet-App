package com.karaoke.poc.ui

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karaoke.poc.audio.AudioPlaybackManager
import com.karaoke.poc.lyrics.LyricsParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class RecordingState {
    IDLE,
    RECORDING,
    STOPPING,
    ERROR
}

class StudioViewModel : ViewModel() {

    private val _recordingState = MutableLiveData<RecordingState>(RecordingState.IDLE)
    val recordingState: LiveData<RecordingState> = _recordingState

    private val _lyricsText = MutableLiveData<String>()
    val lyricsText: LiveData<String> = _lyricsText

    var audioPlaybackManager: AudioPlaybackManager? = null
        private set

    fun initAudioPlaybackManager(context: Context) {
        if (audioPlaybackManager == null) {
            audioPlaybackManager = AudioPlaybackManager(context.applicationContext)
        }
    }

    fun prepareAudio(filePath: String) {
        audioPlaybackManager?.prepare(filePath)
    }

    fun setRecordingState(state: RecordingState) {
        _recordingState.value = state
    }

    fun loadLyrics(context: Context, lyricsFilePath: String) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                try {
                    val parser = LyricsParser()
                    val lines = parser.parse(context, lyricsFilePath)
                    if (lines.isNotEmpty()) {
                        lines.joinToString(separator = "\n") { it.text }
                    } else {
                        "____\nReady to record"
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    "Error loading lyrics: ${e.localizedMessage}"
                }
            }
            _lyricsText.postValue(text)
        }
    }

    fun releaseManagers() {
        audioPlaybackManager?.release()
        audioPlaybackManager = null
    }

    override fun onCleared() {
        super.onCleared()
        releaseManagers()
    }
}
