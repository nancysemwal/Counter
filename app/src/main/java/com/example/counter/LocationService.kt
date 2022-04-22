package com.example.counter

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlin.math.*

class LocationService : Service(), LocationListener {

    private var _setBoundStatus : (Boolean) -> Unit = {_ -> {}}
    private var _writeToDebugSpace : (String) -> Unit = {b -> {}}
    private var _setLocation : (Location) -> Unit = {b -> {}}

    fun writeToDebugSpace(_fn: (String) -> Unit){
        _writeToDebugSpace = _fn
    }
    fun setLocation(_fn: (Location) -> Unit){
        _setLocation = _fn
    }
    private val binder = LocationBinder()

    var isGPSEnabled = false
    private var location : Location? = null

    private val locationManager : LocationManager by lazy {
        getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    override fun onCreate(){
        super.onCreate()
        _setBoundStatus(true)
        getLocation()
    }
    private val provider = LocationManager.GPS_PROVIDER
    fun getLocation(): Location? {
        isGPSEnabled = locationManager.isProviderEnabled(provider)
        if(!isGPSEnabled){
            _writeToDebugSpace("No provider enabled")
        }else{
            if(isGPSEnabled) {
                if(ActivityCompat.checkSelfPermission(this,
                        Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                    &&
                    ActivityCompat.checkSelfPermission(this,
                        Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED){
                    _writeToDebugSpace("No permissions")
                }else{
                    locationManager.requestLocationUpdates(
                        provider,
                        1000,
                        1F,
                        this
                    )
                    location = locationManager.getLastKnownLocation(provider)
                    location?.let { onLocationChanged(it) }
                }
            }
            else{
                _writeToDebugSpace("GPS not enabled")
            }
        }
        return location
    }


    inner class LocationBinder : Binder(){
        fun getService() : LocationService = this@LocationService
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        /*if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0F, this)
        }*/
        return START_NOT_STICKY
    }

    override fun onLocationChanged(location: Location) {
        if (location.provider != provider) return;
        _setLocation(location)
        _writeToDebugSpace("Location updated by ${location.provider} Acc: ${location.accuracy}")
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        super.onStatusChanged(provider, status, extras)
    }

    fun distanceInMetersEuclid(location1: Location, location2: Location): Float{
        val latDiff = (location2.latitude - location1.latitude) * 1e5
        val lonDiff = (location2.longitude - location1.longitude) * 1e5
        val x = latDiff.pow(2)
        val y = lonDiff.pow(2)
        return sqrt(x + y).toFloat()
    }

    fun getNewCoordsEuclidean(
        location: Location,
        heading: Double,
        distanceInMts: Double
    ): Location{
        val newLatitude = (location.latitude * 1e5) + (distanceInMts * cos(heading))
        val newLongitude = (location.longitude * 1e5) + (distanceInMts * sin(heading))
        val newLocation : Location = Location("dummyprovider")
        newLocation.latitude = newLatitude / 1e5
        newLocation.longitude = newLongitude / 1e5
        Log.d("eatery","euclidean new coords $newLocation")
        return newLocation
    }

    fun calculateHeadingEuclid(
        location1: Location,
        location2: Location,
    ): Double {
        val deltaY = location2.longitude - location1.longitude
        val deltaX = location2.latitude - location1.latitude
        return atan(deltaY / deltaX)
    }

}