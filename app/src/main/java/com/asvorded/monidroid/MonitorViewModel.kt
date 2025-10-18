package com.asvorded.monidroid

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import com.asvorded.monidroid.MonidroidClient.ConnectionStates
import java.net.InetAddress

class MonitorViewModel : ViewModel() {
    private val client = MonidroidClient()
    private var started: Boolean = false

    var connectionState: ConnectionStates by mutableStateOf(ConnectionStates.Init)

    var hostname: String? by mutableStateOf(null)

    var currentFrame: ImageBitmap by mutableStateOf(ImageBitmap(1, 1))

    init {
        client.setConnectionCallbacks(
            // onConnected
            {
                connectionState = ConnectionStates.DisplayOff
            },
            // onDisconnected
            {
                connectionState = ConnectionStates.Connecting
            })
            .setNewFrameCallback { bitmap ->
                if (bitmap != null) {
                    connectionState = ConnectionStates.Connected
                    currentFrame = bitmap.asImageBitmap()
                } else {
                    connectionState = ConnectionStates.DisplayOff
                    currentFrame = ImageBitmap(1, 1)
                }
            }
    }

    fun start(address: InetAddress, width: Int, height: Int, hertz: Int) {
        if (!started) {
            hostname = address.hostAddress
            client.setServerAddress(address)
                .setDisplaySettings(width, height, hertz)
            client.start()
            started = true
        }
    }

    override fun onCleared() {
        super.onCleared()
        client.stop()
    }
}