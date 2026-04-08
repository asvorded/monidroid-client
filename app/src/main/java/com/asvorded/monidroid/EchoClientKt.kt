package com.asvorded.monidroid

import kotlinx.coroutines.sync.Mutex
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.Collections
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
//        get
        private set

    private val foundHosts = Collections.synchronizedSet(mutableSetOf<HostInfo>())

    private lateinit var echoSendThread: Thread
    private lateinit var echoReceiveThread: Thread
    private val stateLock: Mutex = Mutex()

    private lateinit var echoSocket: DatagramSocket

    var onFailed: (() -> Unit)? = null
    var onDeviceDetected: ((HostInfo?) -> Unit)? = null
    var onDevicesDetected: ((Set<HostInfo>) -> Unit)? = null

    fun start() {
        resume()
    }

    fun pause() {
        stop()
    }

    fun resume() {
        if (started) return

        echoSocket = DatagramSocket(MonidroidProtocolKt.MONITOR_PORT)
        echoSocket.broadcast = true

        echoSendThread = Thread { sendMain() }
        echoSendThread.start()

        echoReceiveThread = Thread { receiveMain() }
        echoReceiveThread.start()

        started = true
    }


    fun stop() {
        if (!started) return

        started = false
        // now threads see updated `started`
        echoSendThread.interrupt()
        echoReceiveThread.interrupt()
        echoSocket.close()
    }


    private fun sendMain() {
        val sendBuf = MonidroidProtocolKt.CLIENT_ECHO_WORD.toByteArray(StandardCharsets.US_ASCII)

        while (true) {
            val saved = foundHosts.toSet()
            foundHosts.clear()

            try {
                // send broadcast to all interfaces
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
                Thread.sleep(1000)

                // diff saved state and current
                onDevicesDetected?.invoke(foundHosts intersect saved)
            } catch (_: InterruptedException) {
                return
            } catch (_: IOException) {
                if (started) onFailed?.invoke()
                break
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
                if (dgram.address != echoSocket.inetAddress && dgram.length >= header.size + 4) {
                    val data = dgram.data

                    // Check header
                    val headerBuf = data.copyOfRange(0, header.size)
                    System.arraycopy(data, 0, headerBuf, 0, header.size)
                    if (header.contentEquals(headerBuf)) {
                        // Get host name length
                        val len = ByteBuffer.wrap(data.copyOfRange(header.size, header.size + 4))
                            .order(ByteOrder.LITTLE_ENDIAN)
                            .getInt()

                        if (header.size + 4 + len == dgram.length) {
                            val nameBuf = data.copyOfRange(header.size + 4, dgram.length)
                            val hostName = String(nameBuf, StandardCharsets.UTF_8)

                            val hostInfo = HostInfo(dgram.address, hostName)

                            foundHosts += hostInfo
//                            onDeviceDetected?.invoke(hostInfo)
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