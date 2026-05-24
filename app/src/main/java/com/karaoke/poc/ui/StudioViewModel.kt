package com.karaoke.poc.ui

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karaoke.poc.audio.AudioPlaybackManager
import com.karaoke.poc.lyrics.LyricLine
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

    private val _lyricsCount = MutableLiveData<Int>()
    val lyricsCount: LiveData<Int> = _lyricsCount

    private val _lyricLines = MutableLiveData<List<LyricLine>>()
    val lyricLines: LiveData<List<LyricLine>> = _lyricLines

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
        android.util.Log.d("LYRICS", "loadLyrics: called with path: $lyricsFilePath")
        viewModelScope.launch {
            var count = 0
            var list: List<LyricLine> = emptyList()
            val text = withContext(Dispatchers.IO) {
                try {
                    val parser = LyricsParser()
                    val lines = parser.parse(context, lyricsFilePath)
                    count = lines.size
                    list = lines
                    android.util.Log.d("LYRICS", "loadLyrics: parse complete. items=$count")
                    if (lines.isNotEmpty()) {
                        lines.joinToString(separator = "\n") { it.text }
                    } else {
                        "____\nReady to record"
                    }
                } catch (e: Exception) {
                    android.util.Log.e("LYRICS", "loadLyrics: Exception inside IO coroutine", e)
                    "Error loading lyrics: ${e.localizedMessage}"
                }
            }
            android.util.Log.d("LYRICS", "loadLyrics: posting lyricsCount=$count, textLength=${text.length}")
            _lyricLines.postValue(list)
            _lyricsCount.postValue(count)
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
