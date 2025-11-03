package com.asvorded.monidroid

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.asvorded.monidroid.EchoClientKt.AutoDetectingOptions
import com.asvorded.monidroid.EchoClientKt.HostInfo
import kotlinx.coroutines.flow.MutableStateFlow
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

    var connectedEvent = MutableStateFlow(false)

    private val echoClient = EchoClientKt()
    private val foundHostsSet: MutableSet<HostInfo> = mutableSetOf()
    private val synchronizedSet = Collections.synchronizedSet(foundHostsSet)

    private lateinit var checkThread: Thread

    private lateinit var serverAddress: InetAddress

    fun getServerAddress() = serverAddress

    init {
        startAutoDetecting()
    }

    // Auto detecting functions
    fun startAutoDetecting() {
        try {
            echoClient.startEcho(
                {
                    autoDetecting = AutoDetectingOptions.Error
                }
            ) { hostInfo ->
                if (hostInfo != null) {
                    synchronizedSet.add(hostInfo)
                } else {
                    synchronizedSet.clear()
                }
                foundHosts = synchronizedSet.toList()
            }
            autoDetecting = AutoDetectingOptions.Enabled
        } catch (_: IOException) {
            autoDetecting = AutoDetectingOptions.Error
        }
    }

    override fun onCleared() {
        super.onCleared()

        echoClient.stopEcho()
        synchronizedSet.clear()
        foundHosts = listOf()
        autoDetecting = AutoDetectingOptions.Disabled
    }

    fun onConnectClick() {
        if (address.isBlank())
            return

        connecting = true
        checkThread = Thread {
            try {
                checkAddress(address)
                connectedEvent.value = true
            } catch (e: IOException) {
                errorMessage = e.message
            }
            connecting = false
        }
        checkThread.start()
    }

    fun onDetectedConnectClick(device: HostInfo) {
        serverAddress = device.address
        connectedEvent.value = true
    }

    private fun checkAddress(address: String) {
        serverAddress = InetAddress.getByName(address)
    }

    fun resetEvent() {
        connectedEvent.value = false
    }

    fun cancelConnection() {
        checkThread.interrupt()
        connecting = false
    }

    fun closeDialog() {
        errorMessage = null
    }
}