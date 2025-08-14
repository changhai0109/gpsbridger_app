package com.changhai0109.gpsbridger
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Socket
import android.util.Log
import java.net.SocketException

class TcpNmeaProvider(private val host: String, private val port: Int) : NmeaProvider {
    override val nmeaChannel = Channel<String>(Channel.UNLIMITED)
    override val writeChannel = Channel<String>(Channel.UNLIMITED)
    override var connected: Boolean = false

    private val scope = CoroutineScope(Dispatchers.IO)

    init {

    }

    override fun start() {
        scope.launch {
            val socket = Socket(host, port)
            try {
                Log.d("TcpNmeaProvider", "socket connected: ${socket.isConnected}")
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                connected = true
                while (true) {
                    val line = reader.readLine()
//                    Log.d("TcpNmeaProvider", "recv: $line")
                    nmeaChannel.send(line)
                }
            } catch (e: SocketException) {
                Log.e("TcpNmeaProvider", "Socket closed", e)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                connected = false
                socket.close()
                nmeaChannel.close()
                writeChannel.close()
            }
        }
    }

    override fun stop() {
        scope.launch {
            nmeaChannel.close()
            writeChannel.close()
        }
    }
}