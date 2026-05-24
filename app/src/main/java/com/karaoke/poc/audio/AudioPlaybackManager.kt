package com.karaoke.poc.audio

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

interface AudioPlaybackListener {
    fun onPlaybackStarted(timestampNs: Long)
    fun onPlaybackEnded()
}

class AudioPlaybackManager(val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var exoPlayer: ExoPlayer? = null
    private var listener: AudioPlaybackListener? = null
    private var isStartedCallbackTriggered = false

    init {
        runOnMainThread {
            exoPlayer = ExoPlayer.Builder(context).build().apply {
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        checkState()
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        checkState()
                    }
                })
            }
        }
    }

    private fun runOnMainThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

    private fun checkState() {
        val player = exoPlayer ?: return
        val state = player.playbackState
        val isPlaying = player.isPlaying

        if (state == Player.STATE_READY && isPlaying && !isStartedCallbackTriggered) {
            isStartedCallbackTriggered = true
            val timestampNs = SystemClock.elapsedRealtimeNanos()
            listener?.onPlaybackStarted(timestampNs)
        } else if (state == Player.STATE_ENDED) {
            listener?.onPlaybackEnded()
        }
    }

    fun prepare(filePath: String) {
        runOnMainThread {
            val player = exoPlayer ?: return@runOnMainThread
            val mediaItem = MediaItem.fromUri(filePath)
            player.setMediaItem(mediaItem)
            player.prepare()
        }
    }

    fun start(listener: AudioPlaybackListener) {
        runOnMainThread {
            this.listener = listener
            isStartedCallbackTriggered = false
            val player = exoPlayer ?: return@runOnMainThread
            player.playWhenReady = true
            player.play()
        }
    }

    fun stop() {
        runOnMainThread {
            isStartedCallbackTriggered = false
            exoPlayer?.stop()
        }
    }

    fun release() {
        runOnMainThread {
            exoPlayer?.release()
            exoPlayer = null
            listener = null
        }
    }
}
