package com.changhai0109.gpsbridger

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.changhai0109.gpsbridger.ui.theme.MiniGPSBridgerTheme
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.compose.foundation.background
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp


class MainActivity : ComponentActivity() {
    private val binderState = mutableStateOf<GpsForegroundService.LocalBinder?>(null)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            binderState.value = service as GpsForegroundService.LocalBinder
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            binderState.value = null
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Start the foreground service to keep GPS running in background
        val intent = Intent(this, GpsForegroundService::class.java)
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 0)
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), 0)
        }
        if (checkSelfPermission(Manifest.permission.FOREGROUND_SERVICE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.FOREGROUND_SERVICE), 0)
        }
        if (checkSelfPermission(Manifest.permission.FOREGROUND_SERVICE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.FOREGROUND_SERVICE_LOCATION), 0)
        }

        startForegroundService(intent)

        // Compose UI
        setContent {
            val gpsReader = binderState.value?.getGpsReader()

            MiniGPSBridgerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(modifier = Modifier.padding(innerPadding)) {
                        Greeting("Mini GPS Bridger")
                        gpsReader?.let {
                            LocationFlashIndicator(it)
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Intent(this, GpsForegroundService::class.java).also {
            bindService(it, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        unbindService(connection)
    }
}

@Composable
fun Location() {
    var text by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            text = if (LocationRepository.lat.isNaN())
                "Waiting for GPS data"
            else
                "Lat: %.6f, Lon: %.6f, Speed: %.6f".format(
                    LocationRepository.lat,
                    LocationRepository.lon,
                    LocationRepository.speed
                )
            kotlinx.coroutines.delay(1000) // update every second
        }
    }

    Text(text)
}

@Composable
fun LocationFlashIndicator(gpsReader: GPSReader) {
    var text by remember { mutableStateOf("Waiting for GPS data") }
    var flashColor by remember { mutableStateOf(false) }

    val subscriber = remember {
        object : LocationSubscriber {
            override fun onLocationUpdate(lat: Double, lon: Double, speed: Double) {
                text = "Lat: %.6f, Lon: %.6f, Speed: %.6f".format(lat, lon, speed)
                flashColor = true
            }
        }
    }

    LaunchedEffect(gpsReader) {
        gpsReader.subscribe(subscriber)
    }

    Column(
        modifier = Modifier
            .padding(16.dp)
            .background(if (flashColor) Color.Green else Color.Transparent)
    ) {
        Text(text)
    }

    // Reset flash
    LaunchedEffect(flashColor) {
        if (flashColor) {
            kotlinx.coroutines.delay(300)
            flashColor = false
        }
    }
}


@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MiniGPSBridgerTheme {
        Greeting("Mini GPS Bridger")
    }
}
