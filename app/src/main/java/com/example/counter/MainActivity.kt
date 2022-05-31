package com.example.counter

import android.content.*
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.counter.ui.theme.CounterTheme
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {

    private val usbReceiver = object : BroadcastReceiver(){
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent != null) {
                when(intent.action){
                    droneService?.STATUS_NO_FLIGHTMODE -> Toast.makeText(context, "No flight mode", Toast.LENGTH_SHORT).show()
                    droneService?.STATUS_NO_GPS -> Toast.makeText(context, "No GPS fix", Toast.LENGTH_SHORT).show()
                    droneService?.STATUS_NO_EKF -> Toast.makeText(context, "Bad horizontal position", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permissions ->
        when {
            permissions.getOrDefault(android.Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                Log.d("lctn", "Precise granted")
                //locationService?.getLocation()
            }
            permissions.getOrDefault(android.Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                Log.d("lctn", "Approximate granted")
            } else ->{
                Log.d("lctn", "No location granted")
            }
        }
    }


    private var droneService : DroneService? = null
    private var locationService : LocationService? = null
    private var isBound = mutableStateOf(false)
    private var isUsbConnected = mutableStateOf(false)
    private var pitch = mutableStateOf("")
    private var roll = mutableStateOf("")
    private var yaw = mutableStateOf("")
    private var droneStatus = mutableStateOf(Status.Offline.name)
    private var mode = mutableStateOf("")
    private var gpsFix = mutableStateOf("")
    private var satellites = mutableStateOf("")
    private var latitude = mutableStateOf("")
    private var longitude = mutableStateOf("")
    private var altitude = mutableStateOf("")
    private var hAcc = mutableStateOf("")
    private var debugMessage = mutableStateListOf<String>()
    private var phoneLocation = mutableStateOf(Location(LocationManager.GPS_PROVIDER))
    private var sliderAltitude = mutableStateOf(5f)
    private var sliderDistance = mutableStateOf(0f)
    private var heading = mutableStateOf(-0.0)
    private var yawSensorDegrees = mutableStateOf(-0.0)
    private var prevYawSensor = -0.0
    private val yawEps = 10

    //var prevLocation: Location = Location("dummyprovider")
    var prevLocation : Location? = null
    var isFollowMe : MutableState<Boolean> = mutableStateOf(false)
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val droneBinder = service as DroneService.DroneBinder
            droneService = droneBinder.getService()
            droneService?.setUsbStatus { b -> isUsbConnected.value = b }
            droneService?.setPitch { b -> pitch.value = b }
            droneService?.setRoll { b -> roll.value = b }
            droneService?.setYaw { b -> yaw.value = b }
            droneService?.setDroneStatus { b -> droneStatus.value = b
            if(droneStatus.value == Status.Landing.name){
                isFollowMe.value = false
                debugMessage.add(0, "FOLLOW ME disabled")
            }}
            droneService?.setMode { b -> mode.value = b }
            droneService?.setGpsFix { b -> gpsFix.value = b }
            droneService?.setSatellites { b -> satellites.value = b }
            droneService?.setWriteToDebugSpace { b ->
                kotlin.run{debugMessage.add(0, mdformat.format(calendar.time) + " " + b) }
            }
            isBound.value = true
            setFilters()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound.value = false
        }

    }

    private val locationServiceConnection = object : ServiceConnection{
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val locationBinder = service as LocationService.LocationBinder
            locationService = locationBinder.getService()
            locationService?.writeToDebugSpace { b -> debugMessage.add(0, mdformat.format(calendar.time) + " " + b) }
            locationService?.setYawSensor { b ->
                yawSensorDegrees.value = b
                if(abs(prevYawSensor - yawSensorDegrees.value) > yawEps){
                    val (check, loc, yw) = goToLocationWithPrevUpdate(isFollowMe.value,
                        sliderDistance.value.toDouble(),
                        locationService,
                        droneService,
                        phoneLocation.value,
                        prevLocation!!,
                        sliderAltitude.value.toDouble()
                    )
                    if(check){
                        prevLocation = loc
                        prevYawSensor = yw
                    }
                }
            }
            locationService?.setLocation { b ->
                if(prevLocation == null){
                    prevLocation = b
                    phoneLocation.value = b
                } else {
                    phoneLocation.value = b
                    hAcc.value = b.accuracy.toString()
                    altitude.value = b.altitude.toString()
                    latitude.value = b.latitude.toString()
                    longitude.value = b.longitude.toString()
                    goToLocationWithPrevUpdate(isFollowMe.value,
                        sliderDistance.value.toDouble(),
                        locationService,
                        droneService,
                        phoneLocation.value,
                        prevLocation!!,
                        sliderAltitude.value.toDouble()
                    )
                }
            }
            locationPermissionRequest.launch(arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ))
            locationService?.getLocation()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d("srvc","location service disconnected")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CounterTheme {
                MainScreen(
                    isBound.value,
                    isUsbConnected.value,
                    pitch.value,
                    roll.value,
                    yaw.value,
                    droneService,
                    droneStatus.value,
                    mode.value,
                    gpsFix.value,
                    satellites.value,
                    debugMessage,
                    locationPermissionRequest,
                    locationService,
                    latitude.value,
                    longitude.value,
                    altitude.value,
                    hAcc.value,
                    phoneLocation.value,
                    isFollowMe,
                    sliderAltitude,
                    sliderDistance,
                    heading,
                    yawSensorDegrees,
                    {b -> prevYawSensor = b},
                    prevLocation
                )

                /*LaunchedEffectMainScreen(isUsbConnected = isUsbConnected
                    , droneService = droneService)*/
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Intent(this, DroneService::class.java).also { intent ->
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
        Intent(this, LocationService::class.java).also { intent ->
            bindService(intent, locationServiceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        unbindService(serviceConnection)
        unbindService(locationServiceConnection)
        droneService?.onDestroy()
        isBound.value = false
        droneService?.setBoundStatus { b -> isBound.value = false }
    }

    private fun setFilters() {
        val filter = IntentFilter()
        filter.addAction(droneService?.STATUS_NO_FLIGHTMODE)
        filter.addAction(droneService?.STATUS_NO_GPS)
        filter.addAction(droneService?.STATUS_NO_EKF)
        registerReceiver(usbReceiver, filter)
    }

}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun MainScreen(
    isBound: Boolean, isConnected: Boolean, pitch: String
    , roll: String, yaw: String, droneService: DroneService?
    , droneStatus: String, mode: String, gpxFix: String, satellites: String
    , debugMessage: SnapshotStateList<String>, locationPermissionRequest: ActivityResultLauncher<Array<String>>
    , locationService: LocationService?, latitude: String
    , longitude: String, altitude: String, hAcc: String, location: Location?
    , isFollowMe: MutableState<Boolean>, sliderAltitude: MutableState<Float>
    , sliderDistance: MutableState<Float>, heading: MutableState<Double>, yawSensor: MutableState<Double>
    , setPrevYawSensor:(Double) -> Unit, prevLocation: Location?
)
{
    Column() {
        Card(shape = RoundedCornerShape(8.dp)
            , backgroundColor = Color.LightGray
            , modifier = Modifier
                .weight(0.3F)
                .padding(2.dp)){
            Column() {
                Row(
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(2.dp)
                        .fillMaxWidth()
                ){
                    Text(text = "Service : $isBound")
                    Text(text = "USB : $isConnected")
                }
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(2.dp)
                        .fillMaxWidth()
                ){
                    Text(text = "Drone Status : ${droneStatus.uppercase()}")
                    Text(text = "Mode : $mode")
                }
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(2.dp)
                        .fillMaxWidth()
                ){
                    Text(text = "GPS Fix : $gpxFix")
                    Text(text = "Satellites Visible : $satellites")
                }
                Row(
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(0.33f)
                    ){
                        Text(text = "Pitch: $pitch",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(0.33f)
                    ){
                        Text(text = "Roll: $roll",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(0.33f)
                    ){
                        Text(text = "Yaw: $yaw",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                    }
                }
                Text(text = "Lat: $latitude")
                Text(text = "Lon: $longitude")
                Row(
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    /*Text(text = "Alt: $altitude")
                    Text(text = "hAcc: $hAcc meters")*/
                    Text(text = "Yaw: ${heading.value}")
                    Text(text = "Yaw sensor: ${yawSensor.value}")
                }
            }
        }
        Row(){
            Text(text = "MESSAGES ",
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth(),
                textAlign = TextAlign.Center)
        }
        val context = LocalContext.current
        Card(onClick = { saveLog(context, debugMessage[0]) }, shape = RoundedCornerShape(8.dp,)
            , backgroundColor = Color.LightGray
            , modifier = Modifier
                .padding(2.dp)
                .weight(0.5F)
        ) {

            LazyColumn(modifier = Modifier
                .padding(2.dp)
                .weight(0.2F)
                .fillMaxWidth()){
                items(debugMessage.size){
                        Text(text = "- " + debugMessage[it])
                }
            }
        }
        Card(shape = RoundedCornerShape(8.dp)
            , backgroundColor = Color.LightGray
            , modifier = Modifier
                //.weight(0.30F)
                .padding(2.dp)){
            val armed : Boolean = when(droneStatus){
                Status.Armed.name, Status.InFlight.name, Status.Landing.name -> true
                else -> false
            }
            val armButtonLabel = when(armed){
                true -> "Unarm"
                false -> "Arm"
            }
            val controlsEnabled = when(droneStatus){
                Status.Armed.name, Status.InFlight.name, Status.Landing.name -> true
                else -> false
            }
            val followMeEnabled = when(droneStatus){
                Status.Armed.name, Status.InFlight.name -> true
                else -> false
            }
            Column() {
                Row(horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                ){
                    Column() {
                        Text(text = "Altitude: ${sliderAltitude.value.roundToInt()} Meters")
                        Slider(
                            value = sliderAltitude.value,
                            onValueChange = { sliderAltitude.value = it },
                            onValueChangeFinished = {
                                if(isFollowMe.value){
                                    val newLocation = location?.let { it1 ->
                                        locationService?.getNewCoordsEuclidean(
                                            it1, Math.toRadians(yawSensor.value), sliderDistance.value.toDouble())
                                    }
                                if (newLocation != null) {
                                    setPrevYawSensor(yawSensor.value)
                                    droneService?.gotoLocation(newLocation, sliderAltitude.value.toDouble())
                                }
                            }},
                            steps = 7,
                            valueRange = 4f..20f
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                ){
                    Column() {
                        Text(text = "Distance: ${sliderDistance.value.roundToInt()} Meters")
                        val sliderDistanceOld = sliderDistance.value
                        Slider(
                            value = sliderDistance.value,
                            onValueChange = { sliderDistance.value = it },
                            onValueChangeFinished = {
                                if(abs(sliderDistance.value - sliderDistanceOld) > 0){
                                    if(isFollowMe.value && locationService != null){
                                        if (prevLocation != null) {
                                            goToLocationWithPrevUpdate(
                                                true,
                                                distanceFromOriginalPoint = sliderDistance.value.toDouble(),
                                                locationService = locationService,
                                                droneService = droneService,
                                                currLocation = locationService.currPhoneLocation.value,
                                                prevLocation = prevLocation,
                                                altitude = sliderAltitude.value.toDouble(),
                                                isForced = true
                                            )
                                        }
                                    }
                                }
                                },
                            steps = 4,
                            valueRange = 0f..25f
                        )

                    }
                }
                Row(
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp)
                ){
                    Button(onClick = {
                        if(armButtonLabel == "Arm"){
                            droneService?.arm()
                        }
                        else if(armButtonLabel == "Unarm"){
                            droneService?.disarm()
                        }
                    }) {
                        Text(text = armButtonLabel)
                    }
                    Button(enabled = controlsEnabled,
                        onClick = { droneService?.takeoff(sliderAltitude.value) }) {
                        Text(text = "Takeoff")
                    }
                    Button(enabled = controlsEnabled,
                        onClick = { droneService?.land() }) {
                        Text(text = "Land1")
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp)
                ) {
                    Button(enabled = controlsEnabled,
                        onClick = { droneService?.landUsingSetMode() }) {
                        Text(text = "Land2")
                    }
                    Button(
                        enabled = controlsEnabled,
                        onClick = {
                            if (location != null) {
                                droneService?.gotoLocation(location, sliderAltitude.value.toDouble())
                            }
                        }
                    ) {
                        Text(text = "Come")
                    }
                    Row(){
                        Text(text = "Follow Me")
                        Switch(
                            enabled = followMeEnabled,
                            checked = isFollowMe.value,
                            onCheckedChange = {
                                isFollowMe.value = it
                                val message: String = mdformat.format(calendar.time) + " Follow Me is ${isFollowMe.value}"
                                debugMessage.add(0, message)
                            }
                        )
                    }
                }
            }
        }
    }
}

enum class Status {
    Offline, Unarmed, Armed, InFlight, Landing
}

fun saveLog(context: Context, log: String){
    val path: File = context.filesDir
    val file = File(path, "log.txt")
    val stream = FileOutputStream(file)
    val outputStreamWriter = OutputStreamWriter(context.openFileOutput("log.txt", Context.MODE_PRIVATE))
    try{
        outputStreamWriter.write(log)
        Log.d("strm", "wrote $log to $path")
        Toast.makeText(context, "Log Saved", Toast.LENGTH_SHORT).show()
    }
    catch (e: IOException){
        Toast.makeText(context, "Error saving log", Toast.LENGTH_SHORT).show()
    }
}

private const val minAccuracy = 15.0F
private const val eps = 1e-15
private var calendar = Calendar.getInstance()
private var mdformat : SimpleDateFormat = SimpleDateFormat("HH:mm:ss")

fun isLocationDiff(prev: Location, cur: Location) : Boolean {
    if (cur.accuracy > minAccuracy) return false
    if (abs(cur.latitude - prev.latitude) > eps) return true
    if (abs(cur.longitude - prev.longitude) > eps) return true
    return false
}

fun goToLocationWithPrevUpdate(
    isFollowMe: Boolean,
    distanceFromOriginalPoint: Double,
    locationService: LocationService?,
    droneService: DroneService?,
    currLocation: Location,
    prevLocation: Location,
    altitude: Double,
    isForced: Boolean = false
) : Triple<Boolean, Location, Double> {
    if(isFollowMe) {
        val yawSensor = locationService?.updateOrientationAngles()
            ?.let { Math.toRadians(it) }
        val newLocation = yawSensor?.let {
            locationService.getNewCoordsEuclidean(currLocation, it, distanceFromOriginalPoint)
        }
        if (newLocation != null) {
            //prevLocation = newLocation
            if (isForced || isLocationDiff(prevLocation, newLocation)) {
                goToCalculatedLocation(newLocation, prevLocation,
                droneService, altitude)
            }
            return Triple(true, newLocation, yawSensor)
        }
    }
    return Triple(false, prevLocation, 0.0)
}

private fun goToCalculatedLocation(location: Location,
                                   prevLocation: Location,
                                   droneService: DroneService?,
                                   altitude: Double
    ){
    val dist = distanceInMetersEuclid(location, prevLocation)
    val airspeed = calculateAirspeed(dist)
    droneService?.gotoLocation(location,
        altitude,
        airspeed)
}

fun calculateAirspeed(distance: Float): Float{
    val speed : Float = 0.5F * distance
    if(speed <= 1F) return 1F
    if(speed >= 5F) return 5F
    return speed
}

fun distanceInMetersEuclid(location1: Location, location2: Location): Float{
    val latDiff = (location2.latitude - location1.latitude) * 1e5
    val lonDiff = (location2.longitude - location1.longitude) * 1e5
    val x = latDiff.pow(2)
    val y = lonDiff.pow(2)
    return sqrt(x + y).toFloat()
}


