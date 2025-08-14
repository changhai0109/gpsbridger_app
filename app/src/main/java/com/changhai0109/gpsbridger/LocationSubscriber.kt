package com.changhai0109.gpsbridger

interface LocationSubscriber {
    fun onLocationUpdate(lat: Double, lon: Double, speed: Double)
}
