package com.asvorded.monidroid

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import kotlin.time.Duration
import kotlin.time.DurationUnit

class MonitorViewModel : ViewModel() {
    enum class ConnectionStates {
        Init, Connected, DisplayOff, Connecting;
    }

    enum class FpsPosition {
        TopLeft, TopRight, BottomLeft, BottomRight;
    }

    var connectionState: ConnectionStates by mutableStateOf(ConnectionStates.Init)

    var hostname: String? by mutableStateOf(null)

    var fps: Int by mutableIntStateOf(0)
    var fpsPosition: FpsPosition by mutableStateOf(FpsPosition.TopLeft)
    var latency: Duration by mutableStateOf(Duration.ZERO)

    var touchEnabled by mutableStateOf(true)

    var currentFrame: ImageBitmap by mutableStateOf(ImageBitmap(1, 1))

    var disconnectRequested by mutableStateOf(false)

    fun onConnected() {
        connectionState = ConnectionStates.DisplayOff
    }

    fun onConnectionLost() {
        connectionState = ConnectionStates.Connecting
    }

    fun onDisconnectRequest() {
        disconnectRequested = true
    }

    fun onCancelDisconnect() {
        disconnectRequested = false
    }



    fun onNewFrame(event: FrameEvent2?) {
        if (event != null) {
            connectionState = ConnectionStates.Connected
            currentFrame = event.bitmap.asImageBitmap()
            fps = (1.0 / event.frameDuration.toDouble(DurationUnit.SECONDS)).toInt()
            latency = event.latency
        } else {
            connectionState = ConnectionStates.DisplayOff
            currentFrame = ImageBitmap(1, 1)
            fps = 0
        }
    }
}