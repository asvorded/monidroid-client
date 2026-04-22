package com.asvorded.monidroid

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.positionChange
import androidx.lifecycle.ViewModel
import com.asvorded.monidroid.MonidroidProtocol.DEBUG_TAG
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

    var currentFrame: ImageBitmap by mutableStateOf(ImageBitmap(1, 1))

    fun onConnected() {
        connectionState = ConnectionStates.DisplayOff
    }

    fun onConnectionLost() {
        connectionState = ConnectionStates.Connecting
    }

    fun onNewFrame(event: FrameEvent?) {
        if (event != null) {
            connectionState = ConnectionStates.Connected
            currentFrame = event.bitmap.asImageBitmap()
            fps = (1.0 / event.frameTime.toDouble(DurationUnit.SECONDS)).toInt()
        } else {
            connectionState = ConnectionStates.DisplayOff
            currentFrame = ImageBitmap(1, 1)
        }
    }

    fun onRawInput(event: PointerEvent) {
        Log.d(DEBUG_TAG,
            "TOUCH: ${event.type}, ${event.changes
                .joinToString(prefix = "[", postfix = "]") {
                    "(pos=${it.position}, dpos=${it.positionChange()}, pressed=${it.pressed})"
                }}")

    }

    fun onLButton(pressed: Boolean) {

    }

    fun onRButton(pressed: Boolean) {

    }
}