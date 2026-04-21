package com.asvorded.monidroid

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.asvorded.monidroid.EchoClient.AutoDetectingOptions
import com.asvorded.monidroid.EchoClient.HostInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.net.InetAddress
import java.util.Collections

class MainViewModel : ViewModel() {

    var address: String by mutableStateOf("")

    var autoDetecting: AutoDetectingOptions by mutableStateOf(AutoDetectingOptions.Disabled)

    var foundHosts: List<HostInfo> by mutableStateOf(listOf())

    var errorMessage: String? by mutableStateOf(null)
        private set

    var connecting: Boolean by mutableStateOf(false)

    var serverError: ConnectionState.ServerError? by mutableStateOf(null)

    private val echoClient = EchoClient()
    private val foundHostsSet = Collections.synchronizedSet(mutableSetOf<HostInfo>())

    init {
        startAutoDetecting()
    }

    // Auto detecting functions
    fun startAutoDetecting() {
        try {
            echoClient.onFailed = {
                autoDetecting = AutoDetectingOptions.Error
            }
            echoClient.onDevicesDetected = { hosts ->
                foundHosts = hosts.toList()
            }
            echoClient.start()
            autoDetecting = AutoDetectingOptions.Enabled
        } catch (_: IOException) {
            autoDetecting = AutoDetectingOptions.Error
        }
    }

    fun onPauseEcho() {
        if (echoClient.started) echoClient.pause()
    }

    fun onResumeEcho() {
        if (!echoClient.started) echoClient.resume()
    }

    override fun onCleared() {
        super.onCleared()

        echoClient.stop()
        foundHostsSet.clear()
        foundHosts = listOf()
        autoDetecting = AutoDetectingOptions.Disabled
    }

    fun onConnectionBegin() {
        connecting = true
    }

    fun onManualConnectClick(successCallback: (HostInfo) -> Unit) {
        try {
            val serverAddress: InetAddress
            runBlocking(Dispatchers.IO) {
                serverAddress = InetAddress.getByName(address)
            }
            successCallback(HostInfo(serverAddress, null))
        } catch (e: Exception) {
            errorMessage = e.localizedMessage
        }
    }

    fun onConnectionFailed(e: Exception) {
        errorMessage = e.localizedMessage
        connecting = false
    }

    fun onConnected() {
        connecting = false
    }

    fun onCancelConnection() {
        connecting = false
    }

    fun closeErrorDialog() {
        errorMessage = null
    }

    fun closeSessionMessage() {
        serverError = null
    }

    fun onSessionEndedWithError(code: Int, message: String?) {
        this.serverError = ConnectionState.ServerError(code, message)
    }
}