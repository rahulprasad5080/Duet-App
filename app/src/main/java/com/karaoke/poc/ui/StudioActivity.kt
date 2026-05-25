package com.karaoke.poc.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModelProvider
import com.karaoke.poc.audio.AudioPlaybackListener
import com.karaoke.poc.lyrics.LyricLine
import com.karaoke.poc.camera.CameraRecorder
import com.karaoke.poc.camera.CameraRecorderListener
import com.karaoke.poc.databinding.ActivityStudioBinding
import com.karaoke.poc.mixer.OutputComposer
import com.karaoke.poc.sync.SyncEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class StudioActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStudioBinding
    private lateinit var viewModel: StudioViewModel
    private lateinit var cameraRecorder: CameraRecorder
    private lateinit var backingAudioFile: File
    private lateinit var recordedVideoFile: File

    private val spanInfos = mutableListOf<LyricSpanInfo>()
    private var fullLyricsText: String = ""
    private var lyricsJob: kotlinx.coroutines.Job? = null

    private val permissionRequestCode = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStudioBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[StudioViewModel::class.java]
        binding.btnRecord.isEnabled = false

        // Set up observers first
        setupObservers()

        // Bootstrap: Copy assets to filesDir asynchronously
        lifecycleScope.launch(Dispatchers.IO) {
            OutputComposer().copyAssetsToFilesDir(this@StudioActivity)
            withContext(Dispatchers.Main) {
                initManagers()
                
                if (cameraPermissionGranted()) {
                    onPermissionsReady()
                } else {
                    ActivityCompat.requestPermissions(
                        this@StudioActivity,
                        arrayOf(Manifest.permission.CAMERA),
                        permissionRequestCode
                    )
                }
            }
        }

        binding.btnRecord.setOnClickListener {
            startRecordingSession()
        }

        binding.btnStop.setOnClickListener {
            stopRecordingSession()
        }
    }

    private fun setupObservers() {
        viewModel.lyricsText.observe(this) { text ->
            android.util.Log.d("LYRICS", "Observed lyricsText: length=${text?.length ?: 0}, preview=${text?.take(30)?.replace("\n", " ") ?: "null"}")
            if (spanInfos.isEmpty()) {
                binding.tvLyrics.text = text
            }
        }

        viewModel.lyricLines.observe(this) { lines ->
            spanInfos.clear()
            var currentOffset = 0
            val sb = java.lang.StringBuilder()
            for (i in lines.indices) {
                val text = lines[i].text
                sb.append(text)
                if (i < lines.size - 1) {
                    sb.append("\n")
                }
                val start = currentOffset
                val end = currentOffset + text.length
                spanInfos.add(LyricSpanInfo(i, start, end, lines[i]))
                currentOffset += text.length + 1
            }
            fullLyricsText = sb.toString()
            updateLyricsHighlight(0L)
        }

        viewModel.lyricsCount.observe(this) { count ->
            android.util.Log.d("LYRICS", "Observed lyricsCount: $count")
        }

        viewModel.recordingState.observe(this) { state ->
            when (state) {
                RecordingState.IDLE -> {
                    binding.btnRecord.visibility = View.VISIBLE
                    binding.btnStop.visibility = View.GONE
                    binding.btnRecord.isEnabled = true
                    binding.btnStop.isEnabled = true
                    stopLyricsScrolling()
                }
                RecordingState.RECORDING -> {
                    binding.btnRecord.visibility = View.GONE
                    binding.btnStop.visibility = View.VISIBLE
                    binding.btnRecord.isEnabled = true
                    binding.btnStop.isEnabled = true
                    startLyricsScrolling()
                }
                RecordingState.STOPPING -> {
                    binding.btnRecord.visibility = View.GONE
                    binding.btnStop.visibility = View.VISIBLE
                    binding.btnRecord.isEnabled = false
                    binding.btnStop.isEnabled = false
                }
                RecordingState.ERROR -> {
                    binding.btnRecord.visibility = View.VISIBLE
                    binding.btnStop.visibility = View.GONE
                    binding.btnRecord.isEnabled = true
                    binding.btnStop.isEnabled = true
                    viewModel.setRecordingState(RecordingState.IDLE)
                }
            }
        }
    }

    private fun initManagers() {
        cameraRecorder = CameraRecorder(this)
        viewModel.initAudioPlaybackManager(this)
    }

    private fun onPermissionsReady() {
        // Start front camera preview
        cameraRecorder.setUpCamera(this, binding.previewView)

        // Cache files to avoid lazy I/O during recording
        backingAudioFile = OutputComposer().getBackingAudioFile(this)
        recordedVideoFile = OutputComposer().getRecordedVideoFile(this)

        // Load lyrics file contents
        val lyricsFile = OutputComposer().getBackingLyricsFile(this)
        android.util.Log.d("LYRICS", "onPermissionsReady: lyricsFile=${lyricsFile.absolutePath}, exists=${lyricsFile.exists()}, size=${lyricsFile.length()} bytes")
        viewModel.loadLyrics(this, lyricsFile.absolutePath)

        // Pre-prepare backing audio
        viewModel.prepareAudio(backingAudioFile.absolutePath)
        binding.btnRecord.isEnabled = true
    }

    private fun startRecordingSession() {
        val enableMic = binding.switchMic.isChecked
        if (!recordingPermissionsGranted(enableMic)) {
            ActivityCompat.requestPermissions(this, permissionsForRecording(enableMic), permissionRequestCode)
            return
        }

        // Reset sync engine parameters
        SyncEngine.reset()

        SyncEngine.backingAudioFile = backingAudioFile
        SyncEngine.recordedVideoFile = recordedVideoFile

        // Sequential start: Start video recording first, and start audio playback inside onRecordingStarted
        cameraRecorder.startRecording(recordedVideoFile, enableMic, object : CameraRecorderListener {
            override fun onRecordingStarted(videoTimestampNs: Long) {
                SyncEngine.videoRecordStartTimeNs = videoTimestampNs
                android.util.Log.d("SYNC", "videoStartNs=$videoTimestampNs")

                // Immediately start ExoPlayer playback now that the video is recording
                viewModel.audioPlaybackManager?.start(object : AudioPlaybackListener {
                    override fun onPlaybackStarted(audioTimestampNs: Long) {
                        SyncEngine.audioPlayStartTimeNs = audioTimestampNs
                        val offsetMs = SyncEngine.getOffsetMs()
                        
                        android.util.Log.d("SYNC", "audioStartNs=$audioTimestampNs")
                        android.util.Log.d("SYNC", "offsetMs=$offsetMs")
                        
                        // Update UI recording state only after playback starts
                        viewModel.setRecordingState(RecordingState.RECORDING)
                    }

                    override fun onPlaybackEnded() {
                        // Playback finished naturally
                    }
                })
            }

            override fun onRecordingStopped(outputFile: File) {
                // Stopped callback
                val duration = (SystemClock.elapsedRealtimeNanos() - SyncEngine.videoRecordStartTimeNs) / 1_000_000
                SyncEngine.recordingDurationMs = duration

                // Navigate to MixerActivity, keeping StudioActivity in the back stack
                val intent = Intent(this@StudioActivity, MixerActivity::class.java).apply {
                    putExtra("EXTRA_VIDEO_PATH", outputFile.absolutePath)
                    putExtra("EXTRA_AUDIO_PATH", backingAudioFile.absolutePath)
                    putExtra("EXTRA_OFFSET_MS", SyncEngine.getOffsetMs())
                    putExtra("EXTRA_DURATION_MS", SyncEngine.recordingDurationMs)
                }
                startActivity(intent)

                // Return UI to IDLE state
                viewModel.setRecordingState(RecordingState.IDLE)
            }

            override fun onRecordingError(exception: Exception) {
                exception.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this@StudioActivity, "Recording Error: ${exception.message}", Toast.LENGTH_LONG).show()
                }
                viewModel.setRecordingState(RecordingState.ERROR)
            }
        })
    }

    private fun stopRecordingSession() {
        viewModel.setRecordingState(RecordingState.STOPPING)
        
        // Stop audio and video
        viewModel.audioPlaybackManager?.stop()
        cameraRecorder.stopRecording()
    }

    private fun cameraPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(baseContext, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun audioPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(baseContext, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun recordingPermissionsGranted(enableMic: Boolean): Boolean {
        return cameraPermissionGranted() && (!enableMic || audioPermissionGranted())
    }

    private fun permissionsForRecording(enableMic: Boolean): Array<String> {
        return buildList {
            if (!cameraPermissionGranted()) add(Manifest.permission.CAMERA)
            if (enableMic && !audioPermissionGranted()) add(Manifest.permission.RECORD_AUDIO)
        }.toTypedArray()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionRequestCode) {
            if (cameraPermissionGranted()) {
                onPermissionsReady()
                if (permissions.contains(Manifest.permission.RECORD_AUDIO) && !audioPermissionGranted()) {
                    Toast.makeText(this, "Microphone permission is needed only when Mic is on.", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(this, "Camera permission is required to use Studio.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startLyricsScrolling() {
        lyricsJob?.cancel()
        lyricsJob = lifecycleScope.launch {
            while (viewModel.recordingState.value == RecordingState.RECORDING) {
                val currentPosMs = viewModel.audioPlaybackManager?.getCurrentPosition() ?: 0L
                updateLyricsHighlight(currentPosMs)
                kotlinx.coroutines.delay(50)
            }
        }
    }

    private fun stopLyricsScrolling() {
        lyricsJob?.cancel()
        lyricsJob = null
    }

    private fun updateLyricsHighlight(currentPosMs: Long) {
        if (spanInfos.isEmpty()) return

        val activeInfo = spanInfos.firstOrNull { currentPosMs >= it.lyricLine.startTimeMs && currentPosMs <= it.lyricLine.endTimeMs }
            ?: spanInfos.firstOrNull { currentPosMs < it.lyricLine.startTimeMs }
            ?: spanInfos.lastOrNull()
            ?: return

        val activeLineIndex = activeInfo.lineIndex

        val spannable = android.text.SpannableString(fullLyricsText)
        spannable.setSpan(
            android.text.style.ForegroundColorSpan(android.graphics.Color.parseColor("#FFEB3B")),
            activeInfo.startChar,
            activeInfo.endChar,
            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        spannable.setSpan(
            android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
            activeInfo.startChar,
            activeInfo.endChar,
            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        spannable.setSpan(
            android.text.style.RelativeSizeSpan(1.15f),
            activeInfo.startChar,
            activeInfo.endChar,
            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        binding.tvLyrics.text = spannable

        binding.tvLyrics.post {
            val layout = binding.tvLyrics.layout
            if (layout != null && activeLineIndex >= 0 && activeLineIndex < layout.lineCount) {
                val lineTop = layout.getLineTop(activeLineIndex)
                val lineBottom = layout.getLineBottom(activeLineIndex)
                val lineCenter = (lineTop + lineBottom) / 2
                val scrollViewHeight = binding.svLyrics.height
                val scrollY = lineCenter - scrollViewHeight / 2
                binding.svLyrics.smoothScrollTo(0, Math.max(0, scrollY))
            }
        }
    }

    override fun onStop() {
        super.onStop()
        stopLyricsScrolling()
        // Stop session if app goes to background
        if (viewModel.recordingState.value == RecordingState.RECORDING) {
            stopRecordingSession()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLyricsScrolling()
        viewModel.releaseManagers()
    }
}

private data class LyricSpanInfo(
    val lineIndex: Int,
    val startChar: Int,
    val endChar: Int,
    val lyricLine: com.karaoke.poc.lyrics.LyricLine
)
