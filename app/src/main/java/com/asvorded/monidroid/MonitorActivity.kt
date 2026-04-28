package com.asvorded.monidroid

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.modifier.modifierLocalConsumer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.asvorded.monidroid.MonidroidProtocol.DEBUG_TAG
import com.asvorded.monidroid.MonitorViewModel.ConnectionStates
import com.asvorded.monidroid.MonitorViewModel.FpsPosition
import com.asvorded.monidroid.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch
import java.net.InetAddress

class MonitorActivity : ComponentActivity() {
    private val viewModel: MonitorViewModel by viewModels()

    private var service: ClientService? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = (binder as ClientService.ClientBinder).service

            launchLifecycle()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            //
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MonitorScreen(
                    viewModel,
                    onLButton = {  },
                    onRButton = {  },
                    onMouseMove = { offset ->
                        service?.sendMouseMove(offset.x.toInt(), offset.y.toInt())
                    },
                )
            }
        }

        // Too low API level
        @Suppress("DEPRECATION")
        val address = intent.getSerializableExtra("address") as InetAddress
        viewModel.hostname = address.hostAddress

        val intent = Intent(applicationContext, ClientService::class.java).apply {
            action = ClientService.ACTION_JOIN
        }
        bindService(intent, connection, BIND_AUTO_CREATE)
    }

    override fun onDestroy() {
        super.onDestroy()

        service?.disconnect()
        unbindService(connection)
    }

    private fun launchLifecycle() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                service?.state?.collect { state ->
                    when (state) {
                        is ConnectionState.Initialized -> Unit
                        is ConnectionState.Streaming -> {
                            viewModel.onConnected()
                        }
                        is ConnectionState.ConnectionLost -> {
                            viewModel.onConnectionLost()
                        }
                        is ConnectionState.ServerError -> {
                            val intent = Intent().apply {
                                putExtra("code", state.code)
                                putExtra("message", state.message)
                            }
                            setResult(RESULT_OK, intent)
                            finish()
                        }
                        is ConnectionState.USBDisconnected -> {
                            finish()
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // TODO: Will be changed to streaming
                service?.frameEvent?.collect { event ->
                    viewModel.onNewFrame(event)
                }
            }
        }
    }
}

@Composable
fun MonitorScreen(
    viewModel: MonitorViewModel,
    onLButton: () -> Unit,
    onRButton: () -> Unit,
    onMouseMove: (delta: Offset) -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize().background(Color.Black),
    ) {
        if (viewModel.connectionState == ConnectionStates.Connected
            || viewModel.connectionState == ConnectionStates.Connecting) {
            Image(
                contentDescription = "Monitor screen",
                painter = BitmapPainter(viewModel.currentFrame),
                modifier = Modifier
                    .fillMaxSize()
                    .blur(if (viewModel.connectionState == ConnectionStates.Connecting)
                        5.dp else 0.dp)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.type == PointerEventType.Move) {
                                    onMouseMove(event.changes.first().positionChange())
                                }
                            }
                        }
                    }
            )
        }
        if (viewModel.connectionState != ConnectionStates.Connected) {
            Box(modifier = Modifier
                .fillMaxSize()
                .blur(5.dp)
                .background(Color.Black.copy(alpha = 0.6f)))
        } else {
            FpsCounter(viewModel.fps, viewModel.fpsPosition)

            MouseButton(true, onLButton)
            MouseButton(false, onRButton)
        }
        when (viewModel.connectionState) {
            ConnectionStates.Init -> {
                ConnectingScreen(
                    header = stringResource(R.string.init_connecting),
                    message = stringResource(R.string.wait_connection),
                    hostname = viewModel.hostname
                )
            }
            ConnectionStates.Connecting -> {
                ConnectingScreen(
                    header = stringResource(R.string.connection_lost),
                    message = stringResource(R.string.trying_restore_connection),
                    hostname = viewModel.hostname
                )
            }
            ConnectionStates.DisplayOff -> {
                Text(
                    text = stringResource(R.string.display_off),
                    fontSize = 25.sp,
                    color = Color.Gray
                )
            }
            ConnectionStates.Connected -> Unit
        }
    }
}

@Composable
fun BoxScope.MouseButton(left: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Gray.copy(alpha = 0.5f),
            contentColor = Color.White
        ),
        border = BorderStroke(2.dp, Color.Gray),
        shape = CircleShape,
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier
            .align(if (left) Alignment.BottomStart else Alignment.BottomEnd)
            .padding(5.dp)
    ) {
        Image(
            painterResource(if (left) R.drawable.mouse_left_button else R.drawable.mouse_right_button),
            contentDescription = if (left) "L" else "R",
            modifier = Modifier.size(56.dp).padding(10.dp)
        )
    }
}

@Composable
fun BoxScope.FpsCounter(fps: Int, position: FpsPosition) {
    Text(
        text = stringResource(R.string.monitor_fps, fps),
        color = Color.Gray,
        modifier = Modifier
            .align(
                when (position) {
                    FpsPosition.TopLeft -> Alignment.TopStart
                    FpsPosition.TopRight -> Alignment.TopEnd
                    FpsPosition.BottomLeft -> Alignment.BottomStart
                    FpsPosition.BottomRight -> Alignment.BottomEnd
                }
            )
            .padding(5.dp)
            .background(Color.Gray.copy(alpha = 0.4f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 5.dp)
    )
}

@Composable
fun ConnectingScreen(
    header: String,
    message: String,
    hostname: String?
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            color = Color.White
        )
        Spacer(modifier = Modifier.height(15.dp))
        Text(
            text = header,
            fontSize = 35.sp,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(15.dp))
        Text(
            text = message,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(5.dp))
        Text(text = stringResource(R.string.host_name,
                if (hostname.isNullOrBlank())
                    stringResource(R.string.unknown_host)
                else
                    hostname
            ),
            color = Color.White
        )
    }
}

@Preview(showSystemUi = false, device = "spec:parent=pixel_7_pro,orientation=landscape")
@Composable
fun ScrPreview() {
    val viewModel = MonitorViewModel()
    viewModel.connectionState = ConnectionStates.Connected
    viewModel.hostname = "192.168.1.4"
    viewModel.currentFrame = ContextCompat
        .getDrawable(LocalContext.current, R.drawable.error)!!
        .toBitmap()
        .asImageBitmap()
    MonitorScreen(
        viewModel,
        {},
        {},
        {}
    )
}