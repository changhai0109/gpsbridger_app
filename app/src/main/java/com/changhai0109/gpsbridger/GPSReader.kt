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
    private val minInterval = 500L // 5 Hz updates

    private var lastLat = 0.0
    private var lastLon = 0.0

    init {
        try {
            lm.addTestProvider(
                LocationManager.GPS_PROVIDER,
                false, false, false, false,
                true, true, true,
                Criteria.POWER_LOW,
                Criteria.ACCURACY_FINE
            )
        } catch (_: Exception) {}
        lm.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
    }

    fun start() {
        scope.launch {
            var lastDate = ""
            for (line in provider.nmeaChannel) {
                when {
                    line.startsWith("\$GPRMC") -> {
//                        Log.d("GPSReader", "RMC: $line")
                        val date = parseRmcDate(line)
                        if (date != null) lastDate = date
                    }
                    line.startsWith("\$GPGGA") -> {
//                        Log.d("GPSReader", "GGA: $line")
                        val locationData = parseGpgga(line, lastDate)
                        if (locationData != null) {
                            val (lat, lon, accuracy, timeMillis) = locationData
                            updateMockLocation(lat, lon, accuracy, timeMillis)
                        }
                    }
                }
            }
        }
    }

    fun sendCommand(command: String) {
        scope.launch { provider.writeCommand(command) }
    }

    fun close() {
        scope.launch { provider.stop() }
        try { lm.removeTestProvider(LocationManager.GPS_PROVIDER) } catch (_: Exception) {}
    }

    /** Parse $GPRMC to extract date (ddmmyy) */
    private fun parseRmcDate(nmea: String): String? {
        val parts = nmea.split(",")
        return if (parts.size > 9) parts[9] else null
    }

    /** Parse $GPGGA to extract lat, lon, HDOP → accuracy, and UTC time */
    private fun parseGpgga(nmea: String, dateStr: String): Quadruple<Double, Double, Float, Long>? {
        val parts = nmea.split(",")
        if (parts.size < 9) return null
        val lat = convertNmeaToDecimal(parts[2], parts[3])
        val lon = convertNmeaToDecimal(parts[4], parts[5])
        val accuracy = parseHdopToAccuracy(parts[8])
        val timeMillis = parseUtcTime(dateStr, parts[1])
        return Quadruple(lat, lon, accuracy, timeMillis)
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

    /** Convert HDOP to rough accuracy in meters */
    private fun parseHdopToAccuracy(hdopStr: String): Float {
        return try {
            val hdop = hdopStr.toFloat()
            hdop * 5f
        } catch (_: Exception) {
            5f
        }
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

    /** Helper to return four values (like Kotlin Pair/Triple) */
    data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
