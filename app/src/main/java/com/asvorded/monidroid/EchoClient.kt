package com.asvorded.monidroid

import com.asvorded.monidroid.MonidroidProtocol.SV_ECHO_WORD
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.Collections

class EchoClient {
    data class HostInfo(val address: InetAddress, val hostName: String?)

    enum class AutoDetectingOptions {
        Enabled, Error, Disabled
    }

    var started = false
        private set

    private val foundHosts = Collections.synchronizedSet(mutableSetOf<HostInfo>())

    private lateinit var echoSendThread: Thread
    private lateinit var echoReceiveThread: Thread

    private lateinit var echoSocket: DatagramSocket

    var onFailed: (() -> Unit)? = null
    var onDevicesDetected: ((Set<HostInfo>) -> Unit)? = null

    fun start() {
        resume()
    }

    fun pause() {
        stop()
    }

    fun resume() {
        if (started) return

        echoSocket = DatagramSocket(MonidroidProtocol.PROTOCOL_PORT)
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
        val sendBuf = MonidroidProtocol.ECHO_WORD.toByteArray(StandardCharsets.US_ASCII)

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
                                MonidroidProtocol.PROTOCOL_PORT
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
        val buf = ByteArray(128)

        var accepting = true
        while (accepting) {
            try {
                val dgram = DatagramPacket(buf, buf.size)
                echoSocket.receive(dgram)
                if (dgram.address != echoSocket.inetAddress && dgram.length >= SV_ECHO_WORD.length + 4) {
                    val data = dgram.data

                    // Check header
                    if (data.copyOfRange(0, SV_ECHO_WORD.length)
                        .toString(StandardCharsets.UTF_8) == SV_ECHO_WORD)
                    {
                        // Get host name length
                        val len = ByteBuffer
                            .wrap(data.copyOfRange(SV_ECHO_WORD.length, SV_ECHO_WORD.length + 4))
                            .order(ByteOrder.LITTLE_ENDIAN)
                            .getInt()

                        if (SV_ECHO_WORD.length + 4 + len == dgram.length) {
                            val hostName = data.copyOfRange(SV_ECHO_WORD.length + 4, dgram.length)
                                .toString(StandardCharsets.UTF_8)

                            foundHosts += HostInfo(dgram.address, hostName)
                        }
                    }
                }
            } catch (_: IOException) {
                accepting = false
                if (started) onFailed?.invoke()
            }
        }
    }
}