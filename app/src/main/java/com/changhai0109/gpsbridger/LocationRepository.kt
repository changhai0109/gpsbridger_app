package com.changhai0109.gpsbridger

object LocationRepository {
    var lat: Double = Double.NaN
    var lon: Double = Double.NaN
    var speed : Double = Double.NaN

    lateinit var locationProvider: NmeaProvider
}