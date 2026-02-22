package com.asvorded.monidroid

import android.util.Log
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.Objects

class EchoClientKt {
    class HostInfo(val address: InetAddress, val hostName: String) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || javaClass != other.javaClass) return false
            val hostInfo = other as HostInfo
            return address == hostInfo.address && hostName == hostInfo.hostName
        }

        override fun hashCode(): Int {
            return Objects.hash(address, hostName)
        }
    }

    enum class AutoDetectingOptions {
        Enabled, Error, Disabled
    }

    var started = false
        get
        private set
    private lateinit var echoSendThread: Thread
    private lateinit var echoReceiveThread: Thread
    private val stateLock: Mutex = Mutex()

    private lateinit var echoSocket: DatagramSocket

    private var onFailed: (() -> Unit)? = null
    private var onDeviceDetected: ((HostInfo?) -> Unit)? = null

    fun startEcho(failCallback: () -> Unit, deviceDetectedCallback: (HostInfo?) -> Unit) {
        onDeviceDetected = deviceDetectedCallback
        onFailed = failCallback

        resumeEcho()
    }

    fun pauseEcho() {
        stopEcho()
    }

    fun resumeEcho() = runBlocking {
        stateLock.withLock {
            if (started) return@runBlocking

            echoSocket = DatagramSocket(MonidroidProtocolKt.MONITOR_PORT)
            echoSocket.broadcast = true

            echoSendThread = Thread { sendMain() }
            echoSendThread.start()

            echoReceiveThread = Thread { receiveMain() }
            echoReceiveThread.start()

            started = true
        }
    }

    fun stopEcho() = runBlocking {
        stateLock.withLock {
            if (!started) return@runBlocking

            started = false
            // now threads see updated `started`
            echoSendThread.interrupt()
            echoReceiveThread.interrupt()
            echoSocket.close()
        }
    }

    private fun sendMain() {
        val sendBuf = MonidroidProtocolKt.CLIENT_ECHO_WORD.toByteArray(StandardCharsets.US_ASCII)

        var sending = true
        while (sending) {
            try {
                onDeviceDetected?.invoke(null)

                for (ni in NetworkInterface.getNetworkInterfaces()) {
                    if (!ni.isUp || ni.isLoopback) continue
                    for (addr in ni.interfaceAddresses) {
                        val broadcast = addr.broadcast
                        if (broadcast != null) {
                            val dgram = DatagramPacket(
                                sendBuf, sendBuf.size, broadcast,
                                MonidroidProtocolKt.MONITOR_PORT
                            )
                            echoSocket.send(dgram)
                        }
                    }
                }
                Log.d(
                    MonidroidProtocolKt.DEBUG_TAG,
                    "Successfully sent datagrams to all interfaces, waiting 5 seconds"
                )
                Thread.sleep(5000)
            } catch (_: InterruptedException) {
                return
            } catch (_: IOException) {
                sending = false
                if (started) onFailed?.invoke()
            }
        }
    }

    private fun receiveMain() {
        val header = MonidroidProtocolKt.SERVER_ECHO_WORD.toByteArray(StandardCharsets.US_ASCII)
        val buf = ByteArray(128)

        var accepting = true
        while (accepting) {
            try {
                val dgram = DatagramPacket(buf, buf.size)
                echoSocket.receive(dgram)
                if (dgram.length >= header.size + 4) {
                    val data = dgram.data

                    // Check header
                    val headerBuf = data.copyOfRange(0, header.size)
                    System.arraycopy(data, 0, headerBuf, 0, header.size)
                    if (header.contentEquals(headerBuf)) {
                        // Get host name length
                        val len = ByteBuffer.wrap(data.copyOfRange(header.size, header.size + 4))
                            .order(ByteOrder.LITTLE_ENDIAN)
                            .getInt()

                        // BUG: hostName must be in UTF-8 encoding
                        // On Windows, hostName is presented as wchar_t array,
                        // while on Linux as char array
                        if (header.size + 4 + len == dgram.length) {
                            // Get host name
                            // BUG: UTF-16 is only for Windows
                            // TODO: (Windows specific) Convert hostName to UTF-8 string
                            val nameBuf = data.copyOfRange(header.size + 4, dgram.length)
                            val hostName = String(nameBuf, StandardCharsets.UTF_8)

                            // Trigger event
                            val hostInfo = HostInfo(dgram.address, hostName)
                            onDeviceDetected?.invoke(hostInfo)
                        }
                    }
                }
            } catch (_: InterruptedException) {
                return
            } catch (_: IOException) {
                accepting = false
                if (started) onFailed?.invoke()
            }
        }
    }
}