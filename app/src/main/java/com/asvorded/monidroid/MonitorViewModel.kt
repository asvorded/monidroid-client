package com.asvorded.monidroid

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import com.asvorded.monidroid.MonidroidClient.ConnectionStates
import java.net.InetAddress

class MonitorViewModel : ViewModel() {
    private var started: Boolean = false

    var connectionState: ConnectionStates by mutableStateOf(ConnectionStates.Init)

    var hostname: String? by mutableStateOf(null)

    var currentFrame: ImageBitmap by mutableStateOf(ImageBitmap(1, 1))

    fun onConnected() {
        connectionState = ConnectionStates.DisplayOff
    }

    fun onConnectionLost() {
        connectionState = ConnectionStates.Connecting
    }

    fun onNewFrame(bitmap: Bitmap?) {
        if (bitmap != null) {
            connectionState = ConnectionStates.Connected
            currentFrame = bitmap.asImageBitmap()
        } else {
            connectionState = ConnectionStates.DisplayOff
            currentFrame = ImageBitmap(1, 1)
        }
    }
}