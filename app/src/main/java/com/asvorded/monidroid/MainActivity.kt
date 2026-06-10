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
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
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
import com.asvorded.monidroid.EchoClient.AutoDetectingOptions
import com.asvorded.monidroid.EchoClient.HostInfo
import com.asvorded.monidroid.MonidroidProtocol.OsId
import com.asvorded.monidroid.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch
import java.net.InetAddress

class MainActivity : ComponentActivity() {
    private var service: ClientService? = null
    var isBound = false

    var isMonitorOpened = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = (binder as ClientService.ClientBinder).service
            isBound = true

            launchLifecycle()

            val intent = Intent(applicationContext, ClientService::class.java)
            ContextCompat.startForegroundService(applicationContext, intent)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
        }
    }

    private val viewModel: MainViewModel by viewModels()

    private val launcher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isMonitorOpened = false
        unbind()
        if (result.data != null) {
            val code = result.data!!.getIntExtra("code", -1)
            val message = result.data!!.getStringExtra("message")
            viewModel.onSessionEndedWithError(code, message)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainPage(
                        viewModel = viewModel,
                        onWifiConnectClick = { device ->
                            onWifiConnectClick(device)
                        },
                        onCancelClick = {
                            cancelConnection()
                            viewModel.onCancelConnection()
                        },
                        onUsbClick = {
                            onUsbClick()
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

    private fun onWifiConnectClick(device: HostInfo) {
        val intent = Intent(applicationContext, ClientService::class.java).apply {
            action = ClientService.ACTION_START_WIFI

            putExtra("hostName", device.hostName)
            putExtra("address", device.address)
        }

        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels

        intent.putExtra("monitorMode", MonitorMode(width, height, 60))

        // Rebind if we previously returned from monitor activity
        unbind()
        bindService(intent, connection, BIND_AUTO_CREATE)
    }

    private fun onUsbClick() {
        val intent = Intent(applicationContext, ClientService::class.java).apply {
            action = ClientService.ACTION_START_USB
        }

        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels

        intent.putExtra("monitorMode", MonitorMode(width, height, 60))

        // TODO: No unbind() need?
        bindService(intent, connection, BIND_AUTO_CREATE)
    }

    private fun cancelConnection() {
        service?.disconnect()
        unbind()
    }

    private fun launchLifecycle() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                service?.stateFirst?.collect { event ->
                    when (event) {
                        is FirstConnectionState.Connecting -> {
                            viewModel.onConnectionBegin()
                        }
                        is FirstConnectionState.Error -> {
                            viewModel.onConnectionFailed(event.e)
                            unbind()
                        }
                        is FirstConnectionState.Connected -> {
                            // TODO: Keep reference to service in order to ensure that service
                            // TODO: is alive between activity changes
//                            unbindService(connection)

                            viewModel.onConnected()

                            val intent = Intent(applicationContext, MonitorActivity::class.java)
                            intent.putExtra("address", event.hostInfo.address)

                            if (!isMonitorOpened) {
                                isMonitorOpened = true
                                launcher.launch(intent)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun unbind() {
        if (isBound) {
            unbindService(connection)
            service = null
            isBound = false
        }
    }
}

@Composable
fun MainPage(
    modifier: Modifier = Modifier,
    onWifiConnectClick: (HostInfo) -> Unit,
    onUsbClick: () -> Unit,
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
        DetectedDevicesList(
            state = viewModel.autoDetecting,
            devices = viewModel.foundHosts,
            onRetryClick = {
                viewModel.startAutoDetecting()
            },
            onDeviceClick = onWifiConnectClick
        )
        Spacer(modifier = Modifier.padding(10.dp))
        ManualConnectionForm(
            address = viewModel.address,
            onAddressChange = { value ->
                viewModel.address = value
            },
            onWifiConnectClick = {
                viewModel.onManualConnectClick(onWifiConnectClick)
            },
            onUsbClick = onUsbClick
        )
        Spacer(modifier = Modifier.padding(10.dp))

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
    if (viewModel.serverError != null) {
        SessionAbortedMessage(
            code = viewModel.serverError!!.code,
            message = viewModel.serverError!!.message,
            onOkClick = {
                viewModel.closeSessionMessage()
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
    onWifiConnectClick: () -> Unit,
    onUsbClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 35.dp)
    ) {
        IPAddressTextField(
            value = address,
            onValueChange = onAddressChange
        )
        Spacer(modifier = Modifier.padding(5.dp))
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(
                onClick = onWifiConnectClick,
                contentPadding = PaddingValues(
                    start = 2.dp,
                    top = ButtonDefaults.ContentPadding.calculateTopPadding(),
                    end = 2.dp,
                    bottom = ButtonDefaults.ContentPadding.calculateBottomPadding()
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.connect_button))
            }
            Spacer(modifier = Modifier.padding(horizontal = 5.dp))
            OutlinedButton(
                onClick = onUsbClick,
                modifier = Modifier.weight(1f)
            ) {
                Image(
                    Icons.Default.Usb,
                    contentDescription = "USB",
                    modifier = Modifier.height(24.dp).padding(end = 8.dp)
                )
                Text(stringResource(R.string.connect_usb))
            }
        }
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
                Icons.Default.Wifi,
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
        shape = RoundedCornerShape(percent = 30),
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
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
            .padding(10.dp)
            .background(
                Color(235, 235, 253, 255), RoundedCornerShape(10.dp)
            )
            .clip(RoundedCornerShape(10.dp))
    ) {
        Text(
            text = stringResource(R.string.detected_devices),
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(198, 198, 252, 255))
                .padding(start = 6.dp, top = 5.dp, bottom = 5.dp),
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
    val osIcons = remember {
        mapOf(
            OsId.WINDOWS to R.drawable.os_windows,
            OsId.GENERIC_LINUX to R.drawable.os_generic_linux,
            OsId.UBUNTU to R.drawable.os_ubuntu,
            OsId.KUBUNTU to R.drawable.os_kubuntu,
            OsId.ARCH_LINUX to R.drawable.os_arch_linux,
            OsId.CACHYOS to R.drawable.os_cachyos,
        )
    }

    Row(
        modifier = Modifier
            .padding(2.dp)
            .height(45.dp)
            .fillMaxWidth()
            .background(
                Color(145, 145, 145, 44),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onDeviceClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = osIcons[hostInfo.osId]?.let { painterResource(it) }
                ?: rememberVectorPainter(Icons.Default.Computer),
            contentDescription = null,
            modifier = Modifier.padding(start = 6.dp).size(28.dp)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = stringResource(R.string.detected_device_pattern,
            hostInfo.hostName!!, hostInfo.address.hostAddress),
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
                    Spacer(modifier = Modifier.width(40.dp))
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
                modifier = Modifier.padding(25.dp)
            ) {
                Icon(
                    Icons.Default.Dangerous,
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

@Composable
fun SessionAbortedMessage(
    code: Int,
    message: String?,
    onOkClick: () -> Unit,
) {
    Dialog(
        onDismissRequest = onOkClick
    ) {
        Card(
            shape = RoundedCornerShape(15.dp)
        ) {
            Column(
                modifier = Modifier.padding(25.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    stringResource(R.string.session_aborted),
                    fontSize = 24.sp
                )
                Spacer(modifier = Modifier.height(15.dp))
                if (message != null) {
                    Text(
                        stringResource(R.string.session_aborted_error_text, message),
                    )
//                    Text(
//                        stringResource(R.string.session_aborted_error_text_suffix),
//                        modifier = Modifier.padding(top = 10.dp)
//                    )
                } else {
                    Text(
                        stringResource(R.string.session_aborted_error_code, code),
                    )
                }
                Spacer(modifier = Modifier.height(15.dp))
                TextButton(
                    onClick = onOkClick
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
    DetectedDevicesList(AutoDetectingOptions.Enabled,
        listOf(
            HostInfo(InetAddress.getByName("1.1.1.1"), OsId.WINDOWS, "DESKTOP-11DH4S"),
            HostInfo(InetAddress.getByName("1.1.1.2"), OsId.UBUNTU, "ahahah"),
            HostInfo(InetAddress.getByName("1.1.1.3"), OsId.KUBUNTU, "eeee-os"),
            HostInfo(InetAddress.getByName("1.1.1.4"), OsId.ARCH_LINUX, "lol-os"),
            HostInfo(InetAddress.getByName("1.2.1.4"), OsId.CACHYOS, "user-vm"),
        ),
        {},
        {}
    )
}