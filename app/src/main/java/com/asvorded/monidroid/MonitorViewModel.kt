package com.asvorded.monidroid

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.lifecycle.ViewModel
import com.asvorded.monidroid.MonidroidProtocol.DEBUG_TAG

class MonitorViewModel : ViewModel() {
    enum class ConnectionStates {
        Init, Connected, DisplayOff, Connecting;
    }

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

    fun onRawInput(event: PointerEvent) {
        Log.d(DEBUG_TAG,
            "TOUCH: ${event.type}, ${event.changes
                .joinToString(prefix = "[", postfix = "]") {
                    "(pos=${it.position}, pressed=${it.pressed})"
                }}")

    }
}