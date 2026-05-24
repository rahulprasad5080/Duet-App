package com.karaoke.poc.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.karaoke.poc.databinding.ActivityStudioBinding

class StudioActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStudioBinding

    private val requiredPermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )

    private val permissionRequestCode = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStudioBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (allPermissionsGranted()) {
            Toast.makeText(this, "Permissions already granted", Toast.LENGTH_SHORT).show()
        } else {
            ActivityCompat.requestPermissions(this, requiredPermissions, permissionRequestCode)
        }
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
                Toast.makeText(this, "Permissions granted by user", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Permissions not granted by user", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
