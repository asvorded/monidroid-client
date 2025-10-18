package com.asvorded.monidroid

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.util.DisplayMetrics
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.asvorded.monidroid.MonidroidClient.ConnectionStates
import com.asvorded.monidroid.ui.theme.MyApplicationTheme
import java.net.InetAddress

class MonitorActivity : ComponentActivity() {

    private val viewModel: MonitorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())

        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MonitorScreen(viewModel)
            }
        }

        val address = intent.getSerializableExtra("address") as InetAddress
        viewModel.start(address, width, height, 60)
    }
}

@Composable
fun MonitorScreen(
    viewModel: MonitorViewModel
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize().background(Color.Black),
    ) {
        if (viewModel.connectionState != ConnectionStates.DisplayOff) {
            Image(
                contentDescription = "Monitor screen",
                painter = BitmapPainter(viewModel.currentFrame),
                modifier = if (viewModel.connectionState == ConnectionStates.Init ||
                    viewModel.connectionState == ConnectionStates.Connecting
                )
                    Modifier.fillMaxSize().blur(5.dp)
                        .graphicsLayer(alpha = 0.6f)
                else
                    Modifier.fillMaxSize()
            )
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
            ConnectionStates.Connected -> { }
        }
    }
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

@Preview(name = "haha", showSystemUi = false, device = "spec:parent=pixel_7_pro,orientation=landscape", )
@Composable
fun ScrPreview() {
    val viewModel = MonitorViewModel()
    viewModel.connectionState = ConnectionStates.DisplayOff
    viewModel.hostname = "192.168.1.4"
    MonitorScreen(viewModel)
}