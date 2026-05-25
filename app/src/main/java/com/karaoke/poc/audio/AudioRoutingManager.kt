package com.karaoke.poc.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log

class AudioRoutingManager(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun isBluetoothHeadsetConnected(): Boolean {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS) +
                      audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        return devices.any { device ->
            device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            device.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
            device.type == AudioDeviceInfo.TYPE_BLE_SPEAKER
        }
    }

    fun startBluetoothRouting() {
        Log.d("ROUTING", "startBluetoothRouting: called")
        if (!isBluetoothHeadsetConnected()) {
            Log.d("ROUTING", "startBluetoothRouting: No Bluetooth headset connected. Leaving audio routing default.")
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+ API: set communication device
                val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                val bluetoothDevice = devices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                }
                if (bluetoothDevice != null) {
                    val result = audioManager.setCommunicationDevice(bluetoothDevice)
                    Log.d("ROUTING", "startBluetoothRouting: setCommunicationDevice result=$result, deviceType=${bluetoothDevice.type}")
                    audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                } else {
                    Log.d("ROUTING", "startBluetoothRouting: No connected Bluetooth output device found for communication")
                }
            } else {
                // Legacy Android API: start SCO
                if (audioManager.isBluetoothScoAvailableOffCall) {
                    Log.d("ROUTING", "startBluetoothRouting: Starting legacy Bluetooth SCO")
                    audioManager.startBluetoothSco()
                    audioManager.isBluetoothScoOn = true
                    audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                } else {
                    Log.w("ROUTING", "startBluetoothRouting: Bluetooth SCO is NOT available off call")
                }
            }
        } catch (e: Exception) {
            Log.e("ROUTING", "startBluetoothRouting: Exception", e)
        }
    }

    fun stopBluetoothRouting() {
        Log.d("ROUTING", "stopBluetoothRouting: called")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
                audioManager.mode = AudioManager.MODE_NORMAL
                Log.d("ROUTING", "stopBluetoothRouting: Communication device cleared")
            } else {
                audioManager.stopBluetoothSco()
                audioManager.isBluetoothScoOn = false
                audioManager.mode = AudioManager.MODE_NORMAL
                Log.d("ROUTING", "stopBluetoothRouting: Legacy Bluetooth SCO stopped")
            }
        } catch (e: Exception) {
            Log.e("ROUTING", "stopBluetoothRouting: Exception", e)
        }
    }
}
