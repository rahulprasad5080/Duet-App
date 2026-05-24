package com.karaoke.poc.camera

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File

interface CameraRecorderListener {
    fun onRecordingStarted(timestampMs: Long)
    fun onRecordingStopped(outputFile: File)
    fun onRecordingError(exception: Exception)
}

class CameraRecorder(val context: Context) {

    private var videoCapture: VideoCapture<Recorder>? = null
    private var currentRecording: Recording? = null

    fun setUpCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val recorder = Recorder.Builder()
                    .setQualitySelector(androidx.camera.video.QualitySelector.from(androidx.camera.video.Quality.HIGHEST))
                    .build()
                videoCapture = VideoCapture.withOutput(recorder)

                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner, cameraSelector, preview, videoCapture
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    @SuppressLint("MissingPermission")
    fun startRecording(outputFile: File, listener: CameraRecorderListener) {
        val vc = videoCapture
        if (vc == null) {
            listener.onRecordingError(IllegalStateException("VideoCapture is not initialized. Please call setUpCamera first."))
            return
        }

        try {
            val fileOutputOptions = FileOutputOptions.Builder(outputFile).build()
            val recordingBuilder = vc.output.prepareRecording(context, fileOutputOptions)

            currentRecording = recordingBuilder
                .withAudioEnabled()
                .start(ContextCompat.getMainExecutor(context)) { recordEvent ->
                    when (recordEvent) {
                        is VideoRecordEvent.Start -> {
                            val timestampMs = SystemClock.elapsedRealtime()
                            listener.onRecordingStarted(timestampMs)
                        }
                        is VideoRecordEvent.Finalize -> {
                            if (recordEvent.hasError()) {
                                val exception = recordEvent.cause?.let { Exception(it) }
                                    ?: RuntimeException("Video recording finalized with error code: ${recordEvent.error}")
                                listener.onRecordingError(exception)
                            } else {
                                listener.onRecordingStopped(outputFile)
                            }
                            currentRecording = null
                        }
                    }
                }
        } catch (e: Exception) {
            listener.onRecordingError(e)
        }
    }

    fun stopRecording() {
        currentRecording?.stop()
        currentRecording = null
    }
}
