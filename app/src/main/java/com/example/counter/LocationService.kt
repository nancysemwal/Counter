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
    private var _setSource : (String) -> Unit = {_ -> {}}
    private var _writeToDebugSpace : (String) -> Unit = {_ -> {}}

    fun setBoundStatus(_fn : (Boolean) -> Unit){
        _setBoundStatus = _fn
    }

    fun setLatitude(_fn : (String) -> Unit){
        _setLatitude = _fn
    }
    fun setLongitude(_fn : (String) -> Unit){
        _setLongitude = _fn
    }
    fun setSource(_fn: (String) -> Unit){
        _setSource = _fn
    }
    fun writeToDebugSpace(_fn: (String) -> Unit){
        _writeToDebugSpace = _fn
    }
    private val binder = LocationBinder()

    var isGPSEnabled = false
    var isNetworkEnabled = false
    private var location : Location? = null
    private var latitude = 0.0
    private var longitude = 0.0

    private val locationManager : LocationManager by lazy {
        getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    override fun onCreate(){
        super.onCreate()
        _setBoundStatus(true)
    }

    fun getLocation() : Location? {
        isGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        if(!isGPSEnabled && !isNetworkEnabled){
            Log.d("lctn","No provider enabled")
        }else{
            if(isNetworkEnabled){
                if(ActivityCompat.checkSelfPermission(this,
                        Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                    &&
                    ActivityCompat.checkSelfPermission(this,
                        Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED){
                }else{
                    locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        5000,
                        1F,
                        this
                    )
                    location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    if(location != null){
                        latitude = location!!.latitude
                        longitude = location!!.longitude
                        _setLatitude(latitude.toString())
                        _setLongitude(longitude.toString())
                        Log.d("lctn", "Going to ${location?.latitude}, ${location?.longitude}")
                        _writeToDebugSpace("Going to ${location?.latitude}, ${location?.longitude}")
                    }
                    Log.d("lctn","From network $location")
                }
                return location
            }
            if(isGPSEnabled) {
                if(ActivityCompat.checkSelfPermission(this,
                        Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                    &&
                    ActivityCompat.checkSelfPermission(this,
                        Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED){
                }else{
                    if(location != null){
                        latitude = location!!.latitude
                        longitude = location!!.longitude
                        _setLatitude(latitude.toString())
                        _setLongitude(longitude.toString())
                        Log.d("lctn", "Going to ${location?.latitude}, ${location?.longitude}")
                        _writeToDebugSpace("Going to ${location?.latitude}, ${location?.longitude}")
                    }
                    location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    Log.d("lctn","from GPS $location")
                    return location
                }
            }
        }
        //TODO: write to debug space only once
        return location
    }


    inner class LocationBinder : Binder(){
        fun getService() : LocationService = this@LocationService
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0F, this)
        }
        return START_NOT_STICKY
    }

    override fun onLocationChanged(location: Location) {
        latitude = location.latitude
        longitude = location.longitude
        _setLatitude(latitude.toString())
        _setLongitude(longitude.toString())
        Log.d("lctn", "from onLocationChanged $location")
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