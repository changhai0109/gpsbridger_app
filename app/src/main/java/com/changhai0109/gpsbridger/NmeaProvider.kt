package com.changhai0109.gpsbridger
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel

interface NmeaProvider {
    val nmeaChannel: ReceiveChannel<String>
    val writeChannel: SendChannel<String>

    var connected: Boolean

    fun start()
    fun stop()

    suspend fun writeCommand(command: String) {
        writeChannel.send(command)
    }
}