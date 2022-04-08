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

class LocationService : Service(), LocationListener {

    private var _setBoundStatus : (Boolean) -> Unit = {_ -> {}}
    private var _setLatitude : (String) -> Unit = {_ -> {}}
    private var _setLongitude : (String) -> Unit = {_ -> {}}
    private var _setAltitude: (String) -> Unit = {_ -> {}}
    private var _writeToDebugSpace : (String) -> Unit = {b -> {}}
    private var _setHAccMts : (String) -> Unit = {b -> {}}
    private var _setLocation : (Location) -> Unit = {b -> {}}

    fun setBoundStatus(_fn : (Boolean) -> Unit){
        _setBoundStatus = _fn
    }

    fun setLatitude(_fn : (String) -> Unit){
        _setLatitude = _fn
    }
    fun setLongitude(_fn : (String) -> Unit){
        _setLongitude = _fn
    }
    fun setAltitude(_fn: (String) -> Unit){
        _setAltitude = _fn
    }
    fun writeToDebugSpace(_fn: (String) -> Unit){
        _writeToDebugSpace = _fn
    }
    fun setHAccMts(_fn: (String) -> Unit){
        _setHAccMts = _fn
    }
    fun setLocation(_fn: (Location) -> Unit){
        _setLocation = _fn
    }
    private val binder = LocationBinder()

    var isGPSEnabled = false
    var isNetworkEnabled = false
    private var location : Location? = null
    private var latitude = 0.0
    private var longitude = 0.0
    private var altitude = -1.0
    private var hAccuracy = 10000F

    private val locationManager : LocationManager by lazy {
        getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    override fun onCreate(){
        super.onCreate()
        _setBoundStatus(true)
        getLocation()
    }
    //private val provider = LocationManager.NETWORK_PROVIDER
    private val provider = LocationManager.GPS_PROVIDER
    fun getLocation(): Location? {
        isGPSEnabled = locationManager.isProviderEnabled(provider)
        //isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        if(!isGPSEnabled){
            _writeToDebugSpace("No provider enabled")
        }else{
            if(isGPSEnabled) {
                //_writeToDebugSpace("GPS enabled")
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
                    /*
                    if(location != null && location!!.accuracy < hAccuracy){
                        latitude = location!!.latitude
                        longitude = location!!.longitude
                        altitude = location!!.altitude
                        _setLatitude("$latitude")
                        _setLongitude("$longitude")
                        _setAltitude("$altitude")
                        _setHAccMts(location!!.accuracy.toString())
                        _setLocation(location!!)
                        _writeToDebugSpace("Location provided by ${location!!.provider} Acc: ${location!!.accuracy}")
                        Log.d("lctn","from getLocation() $location")
                    }
                    */
                }
            }
            else{
                _writeToDebugSpace("GPS not enabled")
            }
            /*
            if(isNetworkEnabled){
                //_writeToDebugSpace("Network enabled")
                if(ActivityCompat.checkSelfPermission(this,
                        Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                    &&
                    ActivityCompat.checkSelfPermission(this,
                        Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED){
                    _writeToDebugSpace("No permissions")
                }else{
                    locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        2000,
                        10F,
                        this
                    )
                    location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    if(location != null && location!!.accuracy < hAccuracy){
                        latitude = location!!.latitude
                        longitude = location!!.longitude
                        _setLatitude("$latitude")
                        _setLongitude("$longitude")
                        _setHAccMts(location!!.accuracy.toString())
                        _writeToDebugSpace("Location provided by ${location!!.provider} Acc: ${location!!.accuracy}")
                    }
                }
            }
            else{
                _writeToDebugSpace("N/W not enabled")
            }
             */
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
        /*
        latitude = location.latitude
        longitude = location.longitude
        altitude = location.altitude
         */
        _setLocation(location)
        /*
        _setLatitude("$latitude")
        _setLongitude("$longitude")
        _setAltitude("$altitude")
        _setHAccMts(location.accuracy.toString())
         */
        _writeToDebugSpace("Location updated by ${location.provider} Acc: ${location.accuracy}")
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        super.onStatusChanged(provider, status, extras)
    }

    override fun onProviderEnabled(provider: String) {
        super.onProviderEnabled(provider)
    }

    override fun onProviderDisabled(provider: String) {
        super.onProviderDisabled(provider)
    }
}