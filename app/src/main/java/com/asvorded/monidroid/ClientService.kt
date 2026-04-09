package com.asvorded.monidroid

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.asvorded.monidroid.MonidroidProtocolKt
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.IOException
import java.io.Serializable
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

sealed class ClientEvent {
    class New : ClientEvent()
    class Connected(val hostInfo: EchoClientKt.HostInfo) : ClientEvent()
    data class ConnectionError(val e: IOException) : ClientEvent()
    class Streaming : ClientEvent()
    class ConnectionLost(e: Exception) : ClientEvent()
    data class Error(val code: MonidroidProtocolKt.ErrorCode, val message: String?) : ClientEvent() {
        init {
            if (code == MonidroidProtocolKt.ErrorCode.MessageEncoded)
                require(message != null)
            else
                require(message == null)
        }
    }
}

data class MonitorMode(
    val width: Int,
    val height: Int,
    val refreshRate: Int
) : Serializable {
    companion object {
        const val serialVersionUID = 829712437615098L
    }
}

class ClientService : Service() {
    companion object {
        const val CHANNEL_ID = "ClientServiceChannel"
        const val NOTIFICATION_ID = 100
        const val CONNECT_TIMEOUT = 5000

        const val ACTION_START = "ACTION_START"
        const val ACTION_JOIN = "ACTION_JOIN"
    }

    inner class ClientBinder : Binder() {
        val service: ClientService = this@ClientService
    }

    private val _state = MutableStateFlow<ClientEvent>(ClientEvent.New())
    val clientState = _state.asStateFlow()

    private val _event = MutableSharedFlow<Bitmap?>()
    val frameEvent = _event.asSharedFlow()

    private lateinit var serverAddress: InetAddress
    private var hostName: String? = null

    private lateinit var monitorMode: MonitorMode

    private var binder: ClientBinder = ClientBinder()

    private lateinit var clientSocket: Socket
    private var running: Boolean = false
    private var sessionThread = Thread(this::threadMain)

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        createNotificationChannel()

        running = true
        sessionThread.start()

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        if (intent.action == ACTION_START) {
            serverAddress = intent.getSerializableExtra("address") as InetAddress
            hostName = intent.getStringExtra("hostName")
            monitorMode = intent.getSerializableExtra("monitorMode") as MonitorMode
        }

        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(MonidroidProtocolKt.DEBUG_TAG, "(For debug only) ###   Service destroyed   ###")
    }

    fun disconnect() {
        running = false
        try {
            sessionThread.interrupt()
            clientSocket.close()
        } catch (_: Exception) { }

        // Current version: do not control service lifecycle directly
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.notification_channel_name)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = getString(R.string.notification_channel_desc)
            }
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(contentText: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else {
                0
            }
        )
    }

    private fun threadMain() {
        updateNotification(getString(
            R.string.notification_connecting,
            hostName ?: serverAddress.hostAddress))

        try {
            clientSocket = Socket()
            clientSocket.connect(
                InetSocketAddress(serverAddress, MonidroidProtocolKt.MONITOR_PORT),
                CONNECT_TIMEOUT
            )
        } catch (e: IOException) {
            if (!clientSocket.isClosed) {
                _state.value = ClientEvent.ConnectionError(e)
            }

            // First-try connect failed, stop service
            stopSelf()
            return
        }

        while (running) {
            _state.value = ClientEvent.Connected(EchoClientKt.HostInfo(serverAddress, hostName))

            // Connection established
            updateNotification(getString(
                R.string.notification_connected_to,
                hostName ?: serverAddress.hostAddress))

            communicationMain()

            // If we are running and connection lost
            if (running) {
                updateNotification(getString(
                    R.string.notification_connecting,
                    hostName ?: serverAddress.hostAddress))

                try {
                    clientSocket = Socket()
                    clientSocket.connect(
                        InetSocketAddress(serverAddress, MonidroidProtocolKt.MONITOR_PORT),
                        CONNECT_TIMEOUT
                    )
                } catch (_: IOException) {
                    Log.w(MonidroidProtocolKt.DEBUG_TAG, "Connection failed, retrying in 5 seconds")
                    try {
                        Thread.sleep(5000)
                    } catch (_: InterruptedException) { }
                }
            }
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun communicationMain() {
        try {
            // Send WELCOME message to identify device
            sendWelcome()

            _state.value = ClientEvent.Streaming()

            val reader = DataInputStream(clientSocket.getInputStream())

            // Receive display frames
            val header = MonidroidProtocolKt.FRAME_WORD.toByteArray(StandardCharsets.US_ASCII)
            while (true) {
                val headerBuf = ByteArray(header.size)
                reader.readFully(headerBuf)
                if (headerBuf.contentEquals(header)) {
                    // Get image size
                    val sizeBuf = ByteArray(4)
                    reader.readFully(sizeBuf)
                    val imageSize = ByteBuffer.wrap(sizeBuf).order(ByteOrder.LITTLE_ENDIAN).getInt()

                    if (imageSize > 0) {
                        // Get image buffer
                        val imageBuf = ByteArray(imageSize)

                        reader.readFully(imageBuf)

                        val bitmap = BitmapFactory.decodeByteArray(
                            imageBuf, 0, imageSize, BitmapFactory.Options()
                        )

                        if (bitmap != null) {
                            runBlocking {
                                _event.emit(bitmap)
                            }
                        }
                    } else {
                        runBlocking {
                            _event.emit(null)
                        }
                    }
                }
            }
        } catch (e: IOException) {
            _state.value = ClientEvent.ConnectionLost(e)
            try {
                clientSocket.close()
            } catch (_: IOException) { }
        }
    }

    private fun sendWelcome() {
        val bs = ByteArrayOutputStream()

        // WELCOME
        val word = MonidroidProtocolKt.WELCOME_WORD.toByteArray(StandardCharsets.US_ASCII)
        bs.write(word)

        // model
        val model = String.format("%s %s", Build.BRAND, Build.MODEL)
        bs.write(
            ByteBuffer
                .allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(model.length).array()
        )
        bs.write(model.toByteArray(StandardCharsets.UTF_8))

        // screen options
        bs.write(
            ByteBuffer
                .allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(monitorMode.width).array()
        )
        bs.write(
            ByteBuffer
                .allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(monitorMode.height).array()
        )
        bs.write(
            ByteBuffer
                .allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(monitorMode.refreshRate).array()
        )

        // send
        val out = clientSocket.getOutputStream()
        out.write(bs.toByteArray())
    }
}
