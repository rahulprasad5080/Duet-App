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

    private val requiredPermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )

    private val permissionRequestCode = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStudioBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[StudioViewModel::class.java]

        // Set up observers first
        setupObservers()

        // Bootstrap: Copy assets to filesDir asynchronously
        lifecycleScope.launch(Dispatchers.IO) {
            OutputComposer().copyAssetsToFilesDir(this@StudioActivity)
            withContext(Dispatchers.Main) {
                initManagers()
                
                if (allPermissionsGranted()) {
                    onPermissionsReady()
                } else {
                    ActivityCompat.requestPermissions(
                        this@StudioActivity,
                        requiredPermissions,
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
            binding.tvLyrics.text = text
        }

        viewModel.lyricsCount.observe(this) { count ->
            android.util.Log.d("LYRICS", "Observed lyricsCount: $count")
            Toast.makeText(this, "Loaded lyrics count: $count", Toast.LENGTH_LONG).show()
            if (count > 0 && (binding.tvLyrics.text.toString().trim() == "Ready to record" || binding.tvLyrics.text.isEmpty())) {
                android.util.Log.d("LYRICS", "Forcing render of lyrics text in UI")
                viewModel.lyricsText.value?.let { text ->
                    binding.tvLyrics.text = text
                }
            }
        }

        viewModel.recordingState.observe(this) { state ->
            when (state) {
                RecordingState.IDLE -> {
                    binding.btnRecord.visibility = View.VISIBLE
                    binding.btnStop.visibility = View.GONE
                    binding.btnRecord.isEnabled = true
                    binding.btnStop.isEnabled = true
                }
                RecordingState.RECORDING -> {
                    binding.btnRecord.visibility = View.GONE
                    binding.btnStop.visibility = View.VISIBLE
                    binding.btnRecord.isEnabled = true
                    binding.btnStop.isEnabled = true
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

        // Load lyrics file contents
        val lyricsFile = OutputComposer().getBackingLyricsFile(this)
        android.util.Log.d("LYRICS", "onPermissionsReady: lyricsFile=${lyricsFile.absolutePath}, exists=${lyricsFile.exists()}, size=${lyricsFile.length()} bytes")
        viewModel.loadLyrics(this, lyricsFile.absolutePath)

        // Pre-prepare backing audio
        val audioFile = OutputComposer().getBackingAudioFile(this)
        viewModel.prepareAudio(audioFile.absolutePath)
    }

    private fun startRecordingSession() {
        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(this, requiredPermissions, permissionRequestCode)
            return
        }

        // Reset sync engine parameters
        SyncEngine.reset()

        // Fail-safe: Set initial fallback timestamps to prevent offset errors if callbacks are delayed
        val now = SystemClock.elapsedRealtime()
        SyncEngine.audioPlayStartTimeMs = now
        SyncEngine.videoRecordStartTimeMs = now

        val backingAudio = OutputComposer().getBackingAudioFile(this)
        val recordedVideo = OutputComposer().getRecordedVideoFile(this)
        
        SyncEngine.backingAudioFile = backingAudio
        SyncEngine.recordedVideoFile = recordedVideo



        // Start playback and recording in parallel
        viewModel.audioPlaybackManager?.start(object : AudioPlaybackListener {
            override fun onPlaybackStarted(timestampMs: Long) {
                SyncEngine.audioPlayStartTimeMs = timestampMs
            }

            override fun onPlaybackEnded() {
                // Playback finished naturally
            }
        })

        cameraRecorder.startRecording(recordedVideo, object : CameraRecorderListener {
            override fun onRecordingStarted(timestampMs: Long) {
                SyncEngine.videoRecordStartTimeMs = timestampMs
                viewModel.setRecordingState(RecordingState.RECORDING)
            }

            override fun onRecordingStopped(outputFile: File) {
                // Stopped callback
                val duration = SystemClock.elapsedRealtime() - SyncEngine.videoRecordStartTimeMs
                SyncEngine.recordingDurationMs = duration

                // Navigate to MixerActivity, keeping StudioActivity in the back stack
                val intent = Intent(this@StudioActivity, MixerActivity::class.java).apply {
                    putExtra("EXTRA_VIDEO_PATH", outputFile.absolutePath)
                    putExtra("EXTRA_AUDIO_PATH", OutputComposer().getBackingAudioFile(this@StudioActivity).absolutePath)
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

    private fun allPermissionsGranted() = requiredPermissions.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionRequestCode) {
            if (allPermissionsGranted()) {
                onPermissionsReady()
            } else {
                Toast.makeText(this, "Camera & Audio permissions are required to use Studio.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Stop session if app goes to background
        if (viewModel.recordingState.value == RecordingState.RECORDING) {
            stopRecordingSession()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.releaseManagers()
    }
}
