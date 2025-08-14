package com.changhai0109.gpsbridger
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.Binder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GpsForegroundService : Service() {
    private lateinit var gpsReader: GPSReader
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getGpsReader(): GPSReader = gpsReader
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()

//        nmeaProvider = TcpNmeaProvider("z820.changhai0109.com", 5000)
        LocationRepository.locationProvider = UsbNmeaProvider(this)
        gpsReader = GPSReader(this)

        startForeground(1, createNotification("GPS Bridger running"))

        // Launch coroutine to start provider then GPSReader with a delay
        CoroutineScope(Dispatchers.IO).launch {
            LocationRepository.locationProvider.start()
            // Wait a short time (e.g., 500ms to 1s) for connection to establish
            kotlinx.coroutines.delay(1000)
            gpsReader.start()
        }
    }

    private fun createNotification(content: String): Notification {
        val channelId = "gps_foreground_service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "GPS Bridger Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Mini GPS Bridger")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
    }

    override fun onDestroy() {
        gpsReader.stop()
        LocationRepository.locationProvider.stop()
        super.onDestroy()
    }
}