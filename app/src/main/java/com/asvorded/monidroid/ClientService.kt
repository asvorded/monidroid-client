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
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.asvorded.monidroid.EchoClient.HostInfo
import com.asvorded.monidroid.MonidroidProtocol.ErrorCode
import com.asvorded.monidroid.MonidroidProtocol.OsId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.TimeSource

sealed class FirstConnectionState {
    class Connecting : FirstConnectionState()
    data class Error(val e: IOException) : FirstConnectionState()
    data class Connected(val hostInfo: HostInfo, val at: TimeMark) : FirstConnectionState()
}

sealed class ConnectionState {
    class Initialized : ConnectionState()
    class Streaming : ConnectionState()
    data class ConnectionLost(val e: IOException) : ConnectionState()
    data class ServerError(val code: Int, val message: String? = null) : ConnectionState() {
        init {
            if (code == ErrorCode.MessageEncoded.code)
                require(message != null)
            else
                require(message == null)
        }
    }
    class USBDisconnected() : ConnectionState()
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

data class FrameEvent(
    val bitmap: Bitmap,
    val frameTime: Duration
)

class ClientService : Service() {
    companion object {
        const val CHANNEL_ID = "ClientServiceChannel"
        const val NOTIFICATION_ID = 100
        const val CONNECT_TIMEOUT = 5000

        const val ACTION_START_WIFI = "ACTION_START_WIFI"
        const val ACTION_START_USB = "ACTION_START_USB"
        const val ACTION_JOIN = "ACTION_JOIN"
    }

    inner class ClientBinder : Binder() {
        val service: ClientService = this@ClientService
    }

    val _stateFirst = MutableStateFlow<FirstConnectionState>(
        FirstConnectionState.Connecting())
    val stateFirst = _stateFirst.asStateFlow()

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Initialized())
    val state = _state.asStateFlow()

    private val _event = MutableSharedFlow<FrameEvent?>()
    val frameEvent = _event.asSharedFlow()

    private lateinit var hostInfo: HostInfo
    private var port: Int = 0
    private var isUsb = false
    private lateinit var preferredMode: MonitorMode
    private var lastFrameTime = TimeSource.Monotonic.markNow()

    private var binder: ClientBinder = ClientBinder()

    private lateinit var clientSocket: Socket
    private var running: Boolean = false
    private var sessionThread = Thread(this::threadMain)
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }

        running = true
        sessionThread.start()

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        if (intent.action == ACTION_START_WIFI) {
            val serverAddress = intent.getSerializableExtra("address") as InetAddress
            val hostName = intent.getStringExtra("hostName")
            hostInfo = HostInfo(serverAddress, OsId.UNKNOWN, hostName)
            port = MonidroidProtocol.PROTOCOL_PORT
            preferredMode = intent.getSerializableExtra("monitorMode") as MonitorMode
        } else if (intent.action == ACTION_START_USB) {
            hostInfo = HostInfo(InetAddress.getByName("127.0.0.1"), OsId.UNKNOWN, null)
            port = MonidroidProtocol.PROTOCOL_PORT
            preferredMode = intent.getSerializableExtra("monitorMode") as MonitorMode
            isUsb = true
        }

        return binder
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(MonidroidProtocol.DEBUG_TAG, "(Debug) $this created")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(MonidroidProtocol.DEBUG_TAG, "(Debug) $this destroyed")
    }

    fun sendButtons(flags: Int) {
        sendInput(
            MonidroidProtocol.InputType.MouseButtons,
            byteArrayOf(flags.toByte())
        )
    }

    fun sendMouseMove(dx: Int, dy: Int) {
        sendInput(
            MonidroidProtocol.InputType.MouseMove,
            ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(dx)
                .putInt(dy)
                .array()
        )
    }

    fun sendScroll(delta: Int) {
        sendInput(
            MonidroidProtocol.InputType.MouseScroll,
            ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(delta)
                .array()
        )
    }

    private fun sendInput(type: MonidroidProtocol.InputType, data: ByteArray) {
        val bs = ByteArrayOutputStream()

        bs.write(MonidroidProtocol.INPUT_WORD.toByteArray(Charsets.US_ASCII))
        bs.write(type.code)
        bs.write(data)

        serviceScope.launch {
            runCatching {
                val out = clientSocket.getOutputStream()
                out.write(bs.toByteArray())
            }
        }
    }

    fun disconnect() {
        running = false
        try {
            sessionThread.interrupt()
            clientSocket.shutdownInput()
            clientSocket.shutdownOutput()
        } catch (_: Exception) {
            clientSocket.close()
        }

        // Current version: do not control service lifecycle directly
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        val name = getString(R.string.notification_channel_name)
        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = getString(R.string.notification_channel_desc)
        }
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun updateNotification(contentText: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
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

    private fun tryConnect() {
        updateNotification(getString(
            R.string.notification_connecting,
            hostInfo.hostName ?: hostInfo.address.hostAddress))

        clientSocket = Socket()
        clientSocket.connect(
            InetSocketAddress(hostInfo.address, MonidroidProtocol.PROTOCOL_PORT),
            CONNECT_TIMEOUT
        )
    }

    private fun threadMain() {
        try {
            tryConnect()
        } catch (e: IOException) {
            // Only connection errors are need to show
            if (!clientSocket.isClosed) {
                _stateFirst.value = FirstConnectionState.Error(e)
                clientSocket.close()
            }

            // First-try connect failed, stop service
            stopSelf()
            return
        }

        // First-time connection established
        _stateFirst.value = FirstConnectionState.Connected(
            hostInfo, TimeSource.Monotonic.markNow())
        updateNotification(getString(
            R.string.notification_connected_to,
            hostInfo.hostName ?: hostInfo.address.hostAddress))

        if (isUsb) {
            // USB session start
            communicationMain()

            // USB session end
            _state.value = ConnectionState.USBDisconnected()

            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        // Wi-Fi session start
        while (running) {
            // Main loop
            communicationMain()

            // 1) Connection lost at this point
            // 2) ERROR received (running == false)
            var restoring = true
            while (running && restoring) {
                try {
                    tryConnect()

                    // Connection restored
                    updateNotification(getString(
                        R.string.notification_connected_to,
                        hostInfo.hostName ?: hostInfo.address.hostAddress))

                    restoring = false
                } catch (_: IOException) {
                    clientSocket.close()
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

            _state.value = ConnectionState.Streaming()
            val reader = DataInputStream(clientSocket.getInputStream())

            while (true) {
                lastFrameTime = TimeSource.Monotonic.markNow()

                val headerBuf = ByteArray(MonidroidProtocol.WORD_LEN)
                reader.readFully(headerBuf)
                when (headerBuf.toString(StandardCharsets.US_ASCII)) {
                    MonidroidProtocol.SV_FRAME_WORD -> {
                        receiveFrame(reader)
                    }

                    MonidroidProtocol.SV_ERROR_WORD -> {
                        receiveError(reader)
                        running = false
                        break
                    }
                }
            }
        } catch (e: IOException) {
            _state.value = ConnectionState.ConnectionLost(e)
            clientSocket.close()
        }
    }

    private fun sendWelcome() {
        val bs = ByteArrayOutputStream()

        // WELCOME
        val word = if (isUsb) MonidroidProtocol.USB_WELCOME_WORD else MonidroidProtocol.WELCOME_WORD
        bs.write(word.toByteArray())

        // model
        val modelBytes = "${Build.BRAND} ${Build.MODEL}".toByteArray(Charsets.UTF_8)
        bs.write(
            ByteBuffer
                .allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(modelBytes.size).array()
        )
        bs.write(modelBytes)

        // screen options
        bs.write(
            ByteBuffer
                .allocate(12).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(preferredMode.width)
                .putInt(preferredMode.height)
                .putInt(preferredMode.refreshRate)
                .array()
        )

        // send
        val out = clientSocket.getOutputStream()
        out.write(bs.toByteArray())
    }

    private fun receiveFrame(reader: DataInputStream) {
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

            val current = TimeSource.Monotonic.markNow()
            val duration = current - lastFrameTime
            lastFrameTime = TimeSource.Monotonic.markNow()
            runBlocking {
                _event.emit(FrameEvent(bitmap, duration))
            }
        } else {
            runBlocking {
                _event.emit(null)
            }
        }
    }

    private fun receiveError(reader: DataInputStream) {
        val intBuf = ByteArray(4)
        reader.readFully(intBuf)
        val code = ByteBuffer.wrap(intBuf).order(ByteOrder.LITTLE_ENDIAN)
            .getInt()
        var message: String? = null
        if (code == ErrorCode.MessageEncoded.code) {
            reader.readFully(intBuf)
            val len = ByteBuffer.wrap(intBuf).order(ByteOrder.LITTLE_ENDIAN)
                .getInt()

            val strBuf = ByteArray(len)
            reader.readFully(strBuf)
            message = strBuf.toString(StandardCharsets.UTF_8)
        }

        _state.value = ConnectionState.ServerError(code, message)
    }
}
