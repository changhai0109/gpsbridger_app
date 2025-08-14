package com.changhai0109.gpsbridger
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Socket
import android.util.Log
import kotlinx.coroutines.delay
import java.net.InetSocketAddress
import java.net.SocketTimeoutException


class TcpNmeaProvider(private val host: String, private val port: Int) : NmeaProvider {
    override val nmeaChannel = Channel<String>(Channel.UNLIMITED)
    override val writeChannel = Channel<String>(Channel.UNLIMITED)
    override var running = false

    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        runLoop()
    }

    suspend fun loop() {
        var socket: Socket? = null
        try {
            socket = Socket()
            socket.connect(InetSocketAddress(host, port), 3000)
            socket.soTimeout = 5000 // optional read timeout
            Log.d("TcpNmeaProvider", "socket connected: ${socket.isConnected}")

            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            while (running) {
                val line = reader.readLine()
                Log.d("TcpNmeaProvider", "recv: $line")
                nmeaChannel.send(line)
            }
        } catch (e: Exception) {
            Log.e("TcpNmeaProvider", "Socket loop failed", e)
        } finally {
            try { socket?.close() } catch (_: Exception) {}
            Log.d("TcpNmeaProvider", "Socket closed, will retry")
        }
    }

    fun runLoop() {
        scope.launch {
            while (true) {
                if (!running) {
                    delay(1000)
                    continue
                }

                try {
                    loop() // will return on failure
                } catch (e: Exception) {
                    Log.e("TcpNmeaProvider", "Loop exited: ${e.message}")
                }

                delay(1000) // backoff before reconnecting
            }
        }
    }
}
