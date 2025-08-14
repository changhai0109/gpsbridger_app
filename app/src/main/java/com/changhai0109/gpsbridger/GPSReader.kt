package com.changhai0109.gpsbridger

import android.annotation.SuppressLint
import android.content.Context
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import kotlinx.coroutines.*
import android.util.Log

class GPSReader(private val context: Context, private val provider: NmeaProvider) {

    private val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val scope = CoroutineScope(Dispatchers.Default)

    private var lastUpdateTime = 0L
    private val minInterval = 100L // 5 Hz updates

    private var running = false

    private var lastLat = 0.0
    private var lastLon = 0.0

    init {
        runLoop()
    }

    fun start() {
        try {
            lm.addTestProvider(
                LocationManager.GPS_PROVIDER,
                false, false, false, false,
                true, true, true,
                Criteria.POWER_LOW,
                Criteria.ACCURACY_FINE
            )
        } catch (_: Exception) {
            return
        }
        lm.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
        running = true
    }

    fun stop() {
        running = false
        scope.launch { provider.stop() }
        try { lm.removeTestProvider(LocationManager.GPS_PROVIDER) } catch (_: Exception) {}
    }

    suspend fun run() {
        var lastDate = ""
        for (line in provider.nmeaChannel) {
            Log.d("GPSReader", line)

            when {
                line.startsWith("\$GPRMC") -> {
                    lastDate = parseRmcDate(line) ?: lastDate
                    val (lat, lon, speed, course) = parseRmc(line)
                    // Optionally update mock location from RMC if GGA missing
                }
                line.startsWith("\$GPGGA") -> {
                    val locationData = parseGpgga(line, lastDate)
                    if (locationData != null) {
                        val (lat, lon, accuracy, timeMillis) = locationData
                        updateMockLocation(lat, lon, accuracy, timeMillis)
                    }
                }
                line.startsWith("\$GPGSA") -> {
                    val gsaData = parseGpgsa(line)
                    Log.d("GPSReader", "GSA fix: $gsaData")
                }
                line.startsWith("\$GPGSV") -> {
                    val satellites = parseGpgsv(line)
                    Log.d("GPSReader", "Satellites: $satellites")
                }
                line.startsWith("\$GPGLL") -> {
                    val gllData = parseGpgll(line)
                    gllData?.let { (lat, lon, timeMillis) ->
                        updateMockLocation(lat, lon, 5f, timeMillis)
                    }
                }
                line.startsWith("\$GPVTG") -> {
                    val (course, speedKts, speedKmh) = parseGpvtg(line)
                    Log.d("GPSReader", "Course=$course, Speed=$speedKts kts/$speedKmh km/h")
                }
            }

            if (!running) break
        }
    }

    fun runLoop() {
        scope.launch {
            while (true) {
                if (running)
                    run()
                delay(2000)
            }
        }
    }

    fun sendCommand(command: String) {
        scope.launch { provider.writeCommand(command) }
    }

    private fun parseRmc(nmea: String): RmcData {
        val parts = nmea.split(",")
        val lat = convertNmeaToDecimal(parts[3], parts[4])
        val lon = convertNmeaToDecimal(parts[5], parts[6])
        val speed = parts[7].toFloatOrNull() ?: 0f
        val course = parts[8].toFloatOrNull() ?: 0f
        return RmcData(lat, lon, speed, course)
    }

    private fun parseGpgsa(nmea: String): GsaData {
        val parts = nmea.split(",")
        val fixType = parts[2]
        val pdop = parts.getOrNull(15)?.toFloatOrNull() ?: 0f
        val hdop = parts.getOrNull(16)?.toFloatOrNull() ?: 0f
        val vdop = parts.getOrNull(17)?.toFloatOrNull() ?: 0f
        return GsaData(fixType, pdop, hdop, vdop)
    }

    /** Update mock location smoothly in background */
    @SuppressLint("MissingPermission")
    private fun updateMockLocation(lat: Double, lon: Double, accuracy: Float, timeMillis: Long) {
        val now = System.currentTimeMillis()
        if (now - lastUpdateTime < minInterval) return
        lastUpdateTime = now

        if (distance(lastLat, lastLon, lat, lon) < 0.5) return

        val mockLocation = Location(LocationManager.GPS_PROVIDER).apply {
            this.latitude = lat
            this.longitude = lon
            this.accuracy = accuracy
            this.time = timeMillis
            this.elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }
        lastLat = lat
        lastLon = lon

        scope.launch { lm.setTestProviderLocation(LocationManager.GPS_PROVIDER, mockLocation) }
    }

    private fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371e3
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaPhi = Math.toRadians(lat2 - lat1)
        val deltaLambda = Math.toRadians(lon2 - lon1)
        val a = Math.sin(deltaPhi / 2) * Math.sin(deltaPhi / 2) +
                Math.cos(phi1) * Math.cos(phi2) *
                Math.sin(deltaLambda / 2) * Math.sin(deltaLambda / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }

    /** Convert NMEA lat/lon to decimal degrees */
    private fun convertNmeaToDecimal(value: String, direction: String): Double {
        if (value.isEmpty()) return 0.0
        val degLength = if (direction == "N" || direction == "S") 2 else 3
        val degrees = value.substring(0, degLength).toDouble()
        val minutes = value.substring(degLength).toDouble()
        var decimal = degrees + minutes / 60
        if (direction == "S" || direction == "W") decimal = -decimal
        return decimal
    }

    /** Convert UTC time + date to milliseconds */
    private fun parseUtcTime(dateStr: String, timeStr: String): Long {
        if (dateStr.length != 6 || timeStr.length < 6) return System.currentTimeMillis()

        val day = dateStr.substring(0, 2).toInt()
        val month = dateStr.substring(2, 4).toInt() - 1
        val year = 2000 + dateStr.substring(4, 6).toInt()
        val hour = timeStr.substring(0, 2).toInt()
        val minute = timeStr.substring(2, 4).toInt()
        val second = timeStr.substring(4, 6).toInt()

        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        cal.set(year, month, day, hour, minute, second)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun parseGpgsv(nmea: String): List<Satellite> {
        val parts = nmea.split(",")
        val sats = mutableListOf<Satellite>()
        var i = 4
        while (i + 3 < parts.size) {
            val prn = parts[i].toIntOrNull() ?: break
            val elevation = parts[i+1].toIntOrNull() ?: 0
            val azimuth = parts[i+2].toIntOrNull() ?: 0
            val snr = parts[i+3].toIntOrNull() ?: 0
            sats.add(Satellite(prn, elevation, azimuth, snr))
            i += 4
        }
        return sats
    }

    private fun parseGpgll(nmea: String): GllData? {
        val parts = nmea.split(",")
        if (parts.size < 7) return null
        val lat = convertNmeaToDecimal(parts[1], parts[2])
        val lon = convertNmeaToDecimal(parts[3], parts[4])
        val timeMillis = parseUtcTime("000000", parts[5])
        return GllData(lat, lon, timeMillis)
    }

    private fun parseGpvtg(nmea: String): VtgData {
        val parts = nmea.split(",")
        val course = parts.getOrNull(1)?.toFloatOrNull() ?: 0f
        val speedN = parts.getOrNull(5)?.toFloatOrNull() ?: 0f
        val speedK = parts.getOrNull(7)?.toFloatOrNull() ?: 0f
        return VtgData(course, speedN, speedK)
    }

    // --- Data classes ---
    data class RmcData(val lat: Double, val lon: Double, val speed: Float, val course: Float)
    data class GsaData(val fixType: String, val pdop: Float, val hdop: Float, val vdop: Float)
    data class Satellite(val prn: Int, val elevation: Int, val azimuth: Int, val snr: Int)
    data class GllData(val lat: Double, val lon: Double, val timeMillis: Long)
    data class VtgData(val course: Float, val speedN: Float, val speedK: Float)
}
