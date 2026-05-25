package com.karaoke.poc.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.karaoke.poc.databinding.ActivityMixerBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MixerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMixerBinding
    private lateinit var viewModel: MixerViewModel

    private var exoPlayer: ExoPlayer? = null
    private var progressDialog: AlertDialog? = null
    private var playbackTrackerJob: Job? = null
    private var finalVideoPath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMixerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[MixerViewModel::class.java]

        // Retrieve intent extras
        val videoPath = intent.getStringExtra("EXTRA_VIDEO_PATH") ?: ""
        val audioPath = intent.getStringExtra("EXTRA_AUDIO_PATH") ?: ""
        val offsetMs = intent.getLongExtra("EXTRA_OFFSET_MS", 0L)
        val durationMs = intent.getLongExtra("EXTRA_DURATION_MS", 0L)

        // Display sync offset details
        binding.tvSyncOffset.text = "Video offset from audio: $offsetMs ms\nDuration: $durationMs ms"

        // Setup SeekBars default labels
        binding.tvVoiceVolumeLabel.text = "Voice Volume: ${binding.sbVoiceVolume.progress}%"
        binding.tvMusicVolumeLabel.text = "Music Volume: ${binding.sbMusicVolume.progress}%"

        // Seekbar listeners
        binding.sbVoiceVolume.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                binding.tvVoiceVolumeLabel.text = "Voice Volume: ${progress}%"
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })

        binding.sbMusicVolume.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                binding.tvMusicVolumeLabel.text = "Music Volume: ${progress}%"
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })

        // Remix button listener
        binding.btnRemix.setOnClickListener {
            val micVol = binding.sbVoiceVolume.progress / 100.0
            val musicVol = binding.sbMusicVolume.progress / 100.0
            releasePlayer()
            viewModel.startMerge(this, videoPath, audioPath, offsetMs, durationMs, micVol, musicVol)
        }

        // Setup LiveData observers
        setupObservers()

        // Trigger merge if not already in progress
        if (savedInstanceState == null) {
            viewModel.startMerge(this, videoPath, audioPath, offsetMs, durationMs)
        }
    }

    private fun setupObservers() {
        viewModel.mergeState.observe(this) { state ->
            when (state) {
                MergeState.IDLE -> {
                    dismissProgressDialog()
                }
                MergeState.MERGING -> {
                    val msg = viewModel.mergeMessage.value ?: "Processing audio & video..."
                    showProgressDialog(msg)
                }
                MergeState.SUCCESS -> {
                    dismissProgressDialog()
                    Toast.makeText(this, "Video rendering finished!", Toast.LENGTH_SHORT).show()
                }
                MergeState.ERROR -> {
                    dismissProgressDialog()
                    val errorMsg = viewModel.mergeMessage.value ?: "An error occurred during merge"
                    showErrorDialog(errorMsg)
                }
            }
        }

        viewModel.outputVideoPath.observe(this) { path ->
            if (path != null) {
                finalVideoPath = path
                initializePlayer(path)
            }
        }
    }

    private fun showProgressDialog(message: String) {
        if (progressDialog == null) {
            val progressBar = ProgressBar(this).apply {
                isIndeterminate = true
                setPadding(48, 48, 48, 48)
            }
            progressDialog = AlertDialog.Builder(this)
                .setTitle("Merging Tracks")
                .setMessage(message)
                .setView(progressBar)
                .setCancelable(false)
                .create()
        } else {
            progressDialog?.setMessage(message)
        }
        if (!isFinishing && !progressDialog!!.isShowing) {
            progressDialog?.show()
        }
    }

    private fun dismissProgressDialog() {
        progressDialog?.dismiss()
        progressDialog = null
    }

    private fun showErrorDialog(errorMsg: String) {
        AlertDialog.Builder(this)
            .setTitle("Processing Failed")
            .setMessage("Failed to merge the video and audio tracks.\n\nDetails: $errorMsg")
            .setPositiveButton("Retry / Record Again") { dialog, _ ->
                dialog.dismiss()
                finish() // returns to StudioActivity
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun initializePlayer(videoPath: String) {
        releasePlayer()

        exoPlayer = ExoPlayer.Builder(this).build().apply {
            val mediaItem = MediaItem.fromUri(Uri.fromFile(java.io.File(videoPath)))
            setMediaItem(mediaItem)
            repeatMode = Player.REPEAT_MODE_ALL
            prepare()
            playWhenReady = true
            
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) {
                        startPlaybackProgressTracker()
                    } else {
                        playbackTrackerJob?.cancel()
                    }
                }
            })
        }
        binding.playerView.player = exoPlayer
    }

    private fun startPlaybackProgressTracker() {
        playbackTrackerJob?.cancel()
        playbackTrackerJob = lifecycleScope.launch {
            while (true) {
                exoPlayer?.let { player ->
                    val currentPos = player.currentPosition
                    val duration = player.duration
                    if (duration > 0) {
                        binding.progressBar.max = duration.toInt()
                        binding.progressBar.progress = currentPos.toInt()
                        binding.tvDuration.text = String.format(
                            "%s / %s",
                            formatTime(currentPos),
                            formatTime(duration)
                        )
                    }
                }
                delay(200)
            }
        }
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = (ms / 1000).toInt()
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun releasePlayer() {
        playbackTrackerJob?.cancel()
        playbackTrackerJob = null
        exoPlayer?.let { player ->
            player.release()
            exoPlayer = null
        }
        binding.playerView.player = null
    }

    override fun onStart() {
        super.onStart()
        finalVideoPath?.let { path ->
            if (exoPlayer == null) {
                initializePlayer(path)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }

    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
    }
}
