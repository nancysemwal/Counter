package com.example.counter

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.ActivityCompat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

class LocationService : Service(), LocationListener, SensorEventListener {

    private var _setBoundStatus : (Boolean) -> Unit = {_ -> {}}
    private var _writeToDebugSpace : (String) -> Unit = { b -> {} }
    private var _setLocation : (Location) -> Unit = {b -> {}}
    private var _setYawSensor : (Double) -> Unit = {b -> {}}

    fun writeToDebugSpace(_fn: (String) -> Unit){
        _writeToDebugSpace = _fn
    }
    fun setLocation(_fn: (Location) -> Unit){
        _setLocation = _fn
    }

    fun setYawSensor(_fn: (Double) -> Unit){
        _setYawSensor = _fn
    }
    private val binder = LocationBinder()

    var isGPSEnabled = false
    private var location : Location? = null

    private val locationManager : LocationManager by lazy {
        getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    private lateinit var sensorManager: SensorManager
    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    var currPhoneLocation = mutableStateOf(Location(LocationManager.GPS_PROVIDER))
    var prevSentLocation =  mutableStateOf(Location(LocationManager.GPS_PROVIDER))

    override fun onCreate(){
        super.onCreate()
        _setBoundStatus(true)
        getLocation()
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.also {
                accelerometer ->
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_UI)
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.also {
                magneticField ->
            sensorManager.registerListener(this, magneticField, SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_UI)
        }
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
        currPhoneLocation.value = location
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
        //_writeToDebugSpace("Calculated location $distanceInMts meters apart")
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

    override fun onSensorChanged(event: SensorEvent?) {
        if(event == null){
            return
        }
        if(event.sensor.type == Sensor.TYPE_ACCELEROMETER){
            System.arraycopy(event.values, 0, accelerometerReading, 0,
                accelerometerReading.size)
        }
        else if(event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD){
            System.arraycopy(event.values, 0, magnetometerReading, 0,
                magnetometerReading.size)
        }
        updateOrientationAngles()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.d("accry","accuracy changed")
    }

    fun updateOrientationAngles(): Double{
        SensorManager.getRotationMatrix(rotationMatrix, null,
            accelerometerReading, magnetometerReading)

        val orientation = SensorManager.getOrientation(rotationMatrix, orientationAngles)

        val degrees = (Math.toDegrees(orientation[0].toDouble()) + 360.0) % 360.0

        val angle = round(degrees * 100) / 100

        _setYawSensor(angle)
        return angle
    }

}