package com.asvorded.monidroid

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.DisplayMetrics
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.asvorded.monidroid.EchoClientKt.AutoDetectingOptions
import com.asvorded.monidroid.EchoClientKt.HostInfo
import com.asvorded.monidroid.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var service: ClientService? = null
    var isBound: Boolean = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = (binder as ClientService.ClientBinder).service
            isBound = true

            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    service?.clientState?.collect { event ->
                        when (event) {
                            is ClientEvent.New -> {
                                viewModel.onConnectionBegin()
                            }
                            is ClientEvent.ConnectionError -> {
                                onConnectionFailed(event.e)
                            }
                            is ClientEvent.Connected -> {
                                onConnected(event.hostInfo)
                            }
                            else -> Unit
                        }
                    }
                }
            }

            val intent = Intent(applicationContext, ClientService::class.java)
            ContextCompat.startForegroundService(applicationContext, intent)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
        }
    }

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainPage(
                        viewModel = viewModel,
                        onConnectClick = { device ->
                            onConnectClick(device)
                        },
                        onCancelClick = {
                            cancelConnection()
                            viewModel.onCancelConnection()
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.onPauseEcho()
    }

    override fun onStart() {
        super.onStart()
        viewModel.onResumeEcho()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) unbindService(connection)
    }

    private fun onConnectClick(device: HostInfo) {
        val intent = Intent(applicationContext, ClientService::class.java).apply {
            action = ClientService.ACTION_START

            putExtra("hostName", device.hostName)
            putExtra("address", device.address)
        }

        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels

        intent.putExtra("monitorMode", MonitorMode(width, height, 60))

        // Rebind if we previously returned from monitor activity
        if (isBound) {
            unbindService(connection)
        }
        bindService(intent, connection, BIND_AUTO_CREATE)
    }

    private fun cancelConnection() {
        service?.disconnect()
        unbindService(connection)
        isBound = false
    }

    private fun onConnectionFailed(e: Exception) {
        viewModel.onConnectionFailed(e)
        unbindService(connection)
        isBound = false
    }

    private fun onConnected(hostInfo: HostInfo) {
        // TODO: Keep reference to service in order to ensure that service
        // TODO: is alive between activity changes
//        unbindService(connection)

        viewModel.onConnected()

        val intent = Intent(applicationContext, MonitorActivity::class.java)
        intent.putExtra("address", hostInfo.address)
        startActivity(intent)
    }
}

@Composable
fun MainPage(
    modifier: Modifier = Modifier,
    onConnectClick: (HostInfo) -> Unit,
    onCancelClick: () -> Unit,
    viewModel: MainViewModel = viewModel()
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxSize()
    ) {
        Header()
        Spacer(modifier = Modifier.padding(10.dp))

        ManualConnectionForm(
            address = viewModel.address,
            onAddressChange = { value ->
                viewModel.address = value
            },
            onConnectClick = {
                viewModel.onManualConnectClick(onConnectClick)
            }
        )
        Spacer(modifier = Modifier.padding(10.dp))

        DetectedDevicesList(
            state = viewModel.autoDetecting,
            devices = viewModel.foundHosts,
            onRetryClick = {
                viewModel.startAutoDetecting()
            },
            onDeviceClick = onConnectClick
        )
    }

    if (viewModel.connecting) {
        ConnectionProgress(
            onCancelClick = onCancelClick
        )
    }
    if (viewModel.errorMessage != null) {
        ConnectionError(
            errorMessage = viewModel.errorMessage.toString(),
            onOkClick = {
                viewModel.closeErrorDialog()
            }
        )
    }
}

@Composable
fun Header(modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(
            text = stringResource(R.string.app_name_1),
            fontSize = 48.sp,
            textAlign = TextAlign.Center,
            lineHeight = 36.sp
        )
        Text(
            text = stringResource(R.string.app_name_2),
            fontSize = 36.sp,
            textAlign = TextAlign.Center,
    //            lineHeight = 48.sp
        )
    }
}

@Composable
fun ManualConnectionForm(
    address: String,
    onAddressChange: (String) -> Unit,
    onConnectClick: () -> Unit
) {
    IPAddressTextField(
        value = address,
        onValueChange = onAddressChange
    )
    Spacer(modifier = Modifier.padding(5.dp))
    Button(
        onClick = onConnectClick
    ) {
        Text(stringResource(R.string.connect_button))
    }
}

@Composable
fun IPAddressTextField(
    value: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        label = {
            Text(stringResource(R.string.ip_address))
        },
        leadingIcon = {
            Image(
                painter = painterResource(R.drawable.wifi_48px),
                contentDescription = "",
                modifier = Modifier.width(30.dp))
        },
        value = value,
        onValueChange = onValueChange,
        maxLines = 1,
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.None,
            keyboardType = KeyboardType.Number
        ),
        modifier = modifier
    )
}

@Composable
fun DetectedDevicesList(
    state: AutoDetectingOptions,
    devices: List<HostInfo>,
    onRetryClick: () -> Unit,
    onDeviceClick: (HostInfo) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .height(250.dp)
            .padding(10.dp)
            .background(Color(235, 235, 253, 255))
    ) {
        Text(
            text = stringResource(R.string.detected_devices),
            modifier = Modifier.fillMaxWidth()
                .background(colorResource(R.color.purple_200))
                .padding(start = 6.dp, top = 3.dp, bottom = 3.dp),
        )
        when (state) {
            AutoDetectingOptions.Enabled -> {
                if (devices.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier.padding(5.dp)
                    ) {
                        items(devices) { device ->
                            DetectedDeviceInfo(
                                hostInfo = device,
                                onDeviceClick = {
                                    onDeviceClick(device)
                                })
                        }
                    }
                } else {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text(
                            text = stringResource(R.string.searching_devices),
                            color = Color.Gray
                        )
                    }
                }
            }
            AutoDetectingOptions.Disabled, AutoDetectingOptions.Error -> {
                Column(
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text(
                        text = stringResource(R.string.detect_error),
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = onRetryClick
                    ) {
                        Text(text = stringResource(R.string.retry_detect))
                    }
                }
            }
        }
    }
}

@Composable
fun DetectedDeviceInfo(
    hostInfo: HostInfo,
    onDeviceClick: () -> Unit
) {
    Row(
        modifier = Modifier.padding(2.dp)
            .height(45.dp).fillMaxWidth()
            .background(Color(145, 145, 145, 44))
            .clickable(onClick = onDeviceClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.computer),
            contentDescription = null,
            modifier = Modifier.padding(start = 6.dp)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(text = stringResource(R.string.detected_device_pattern,
            hostInfo.hostName!!, hostInfo.address.hostAddress!!),
            )
    }
}

@Composable
fun ConnectionProgress(
    onCancelClick: () -> Unit
) {
    Dialog(
        onDismissRequest = onCancelClick
    ) {
        Card (
            shape = RoundedCornerShape(15.dp)
        ) {
            Column(
                modifier = Modifier.padding(25.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.width(50.dp))
                    Text(stringResource(R.string.connecting))
                }
                Spacer(modifier = Modifier.height(30.dp))
                TextButton(
                    onClick = onCancelClick,
                    modifier = Modifier
                        .height(20.dp)
                        .align(Alignment.End),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = stringResource(R.string.cancel)
                    )
                }
            }
        }
    }
}

@Composable
fun ConnectionError(
    errorMessage: String,
    onOkClick: () -> Unit,
) {
    Dialog(
        onDismissRequest = onOkClick
    ) {
        Card (
            shape = RoundedCornerShape(15.dp)
        ) {
            Column(
                modifier = Modifier.padding(15.dp)
            ) {
                Icon(
                    painterResource(R.drawable.error),
                    contentDescription = null,
                    modifier = Modifier
                        .height(48.dp)
                        .padding(bottom = 10.dp)
                        .align(Alignment.CenterHorizontally)
                )
                Text(text = stringResource(R.string.connection_error_1))
                Text(text = stringResource(R.string.connection_error_2))
                Text(text = stringResource(R.string.connection_error_3))
                Spacer(modifier = Modifier.height(15.dp))
                Text(text = stringResource(R.string.connection_error_4, errorMessage))
                TextButton(
                    onClick = onOkClick,
                    modifier = Modifier
                        .padding(0.dp, 10.dp, 0.dp, 0.dp)
                        .align(Alignment.CenterHorizontally)
                ) {
                    Text("OK")
                }
            }
        }

    }
}

@Preview(
    showBackground = true,
    )
@Composable
fun GreetingPreview() {
    ConnectionError("ddddddd") { }
}