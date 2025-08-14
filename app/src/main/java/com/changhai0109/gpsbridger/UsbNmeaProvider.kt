package com.changhai0109.gpsbridger

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

class UsbNmeaProvider(
    private val context: Context,
    private val baudrate: Int = 115200
) : NmeaProvider {

    companion object {
        private const val ACTION_USB_PERMISSION = "com.changhai0109.gpsbridger.USB_PERMISSION"
    }

    private var port: UsbSerialPort? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private var usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var pendingDevice: UsbDevice? = null

    override val nmeaChannel = Channel<String>(Channel.UNLIMITED)
    override val writeChannel = Channel<String>(Channel.UNLIMITED)
    override var running: Boolean = false

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_USB_PERMISSION) return
            val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            if (granted && device != null) {
                try {
                    openDevice(device)
                } catch (e: Exception) {
                    Log.e("UsbNmeaProvider", "Failed to open device: ${e.message}")
                }
            } else {
                Log.e("UsbNmeaProvider", "USB permission denied for device $device")
            }
        }
    }

    init {
        // register receiver once
        ContextCompat.registerReceiver(
            context,
            usbReceiver,
            IntentFilter(ACTION_USB_PERMISSION),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        runLoop()
    }

    fun runLoop() {
        scope.launch {
            while (true) {
                if (!running) {
                    delay(1000)
                    continue
                }

                try {
                    loop() // returns if a loop fails
                } catch (e: Exception) {
                    Log.e("UsbNmeaProvider", "Loop exited: ${e.message}")
                }

                delay(1000)
            }
        }
    }

    suspend fun loop() {
        while (port == null || !port!!.isOpen) {
            delay(1000)
            initialDevice()
        }

        coroutineScope {
            launch {
                val buffer = ByteArray(1024)
                try {
                    while (isActive) {
                        val len = port?.read(buffer, 1000) ?: throw Exception("Port closed")
                        if (len > 0) {
                            val line = String(buffer, 0, len)
                            nmeaChannel.send(line)
                        }
                        if (!running) break
                    }
                } catch (e: Exception) {
                    Log.e("UsbNmeaProvider", "Read loop failed: ${e.message}")
                } finally {
                    try { port?.close() } catch (_: Exception) {}
                    cancel()
                }
            }

            launch {
                try {
                    for (data in writeChannel) {
                        port?.write(data.toByteArray(), 1000)
                            ?: throw Exception("Port closed")
                        if (!running) break
                    }
                } catch (e: Exception) {
                    Log.e("UsbNmeaProvider", "Write loop failed: ${e.message}")
                } finally {
                    try { port?.close() } catch (_: Exception) {}
                    cancel()
                }
            }
        }
    }

    fun initialDevice() {
        val deviceList = usbManager.deviceList
        // pick first available device
        val device = deviceList.values.firstOrNull() ?: throw Exception("no device found")
        if (!usbManager.hasPermission(device)) {
            // Request permission asynchronously
            val permissionIntent = PendingIntent.getBroadcast(
                context, 0, Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE
            )
            pendingDevice = device
            usbManager.requestPermission(device, permissionIntent)
        } else {
            // Already have permission, open immediately
            openDevice(device)
        }
    }

    private fun openDevice(device: UsbDevice) {
        val driver = UsbSerialProber.getDefaultProber().probeDevice(device)
            ?: throw Exception("No driver for device")
        val connection = usbManager.openDevice(device) ?: throw Exception("Failed to open connection")
        port = driver.ports[0]
        port?.open(connection)
        port?.setParameters(baudrate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
        Log.d("UsbNmeaProvider", "Device opened: $device")
    }

    override fun stop() {
        running = false
        scope.launch {
            try { port?.close() } catch (_: Exception) {}
        }
        try { context.unregisterReceiver(usbReceiver) } catch (_: Exception) {}
    }
}
