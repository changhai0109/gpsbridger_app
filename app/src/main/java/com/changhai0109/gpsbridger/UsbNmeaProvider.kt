package com.changhai0109.gpsbridger

import android.content.Context
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel

class UsbNmeaProvider(private val context: Context, private val baudrate: Int = 115200) : NmeaProvider {
    private var port: UsbSerialPort? = null
    private val buffer = ByteArray(1024)
    private val scope = CoroutineScope(Dispatchers.IO)

    override val nmeaChannel = Channel<String>(Channel.UNLIMITED)
    override val writeChannel = Channel<String>(Channel.UNLIMITED)
    override var connected: Boolean = false

    fun startReading() {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val driverList = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        if (driverList.isEmpty()) return

        val driver = driverList[0]      // TODO: might not always first device
        val connection = usbManager.openDevice(driver.device) ?: return
        port = driver.ports[0]
        port?.open(connection)
        port?.setParameters(baudrate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
        connected = true

        scope.launch {
            while (true) {
                val len = port?.read(buffer, 1000) ?: 0
                if (len>0) {
                    val line = String(buffer, 0, len)
                    nmeaChannel.send(line)
                }
            }
        }
    }

    fun startWriting() {
        scope.launch {
            for (data in writeChannel) {
                try {
                    port?.write(data.toByteArray(), 1000)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun stop() {
        connected = false
        scope.launch {
            nmeaChannel.close()
            writeChannel.close()
        }
        port?.close()
    }

    override fun start() {
        this.startReading()
        this.startWriting()
    }

}