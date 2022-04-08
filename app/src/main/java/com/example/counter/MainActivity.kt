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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.counter.ui.theme.CounterTheme
import kotlin.math.roundToInt

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
    //private var debugMessage = mutableStateOf("")
    private var debugMessage = mutableListOf<String>()
    private var location = mutableStateOf(Location(LocationManager.GPS_PROVIDER))

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val droneBinder = service as DroneService.DroneBinder
            droneService = droneBinder.getService()
            droneService?.setUsbStatus { b -> isUsbConnected.value = b }
            droneService?.setPitch { b -> pitch.value = b }
            droneService?.setRoll { b -> roll.value = b }
            droneService?.setYaw { b -> yaw.value = b }
            droneService?.setDroneStatus { b -> droneStatus.value = b }
            droneService?.setMode { b -> mode.value = b }
            droneService?.setGpsFix { b -> gpsFix.value = b }
            droneService?.setSatellites { b -> satellites.value = b }
            droneService?.writeToDebugSpace { b -> debugMessage.add(0, b) }
            //droneService?.writeToDebugSpace { b -> debugMessage.value = b + "\n" + debugMessage.value }
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
            locationService?.writeToDebugSpace { b -> debugMessage.add(0, b) }
            //locationService?.writeToDebugSpace { b -> debugMessage.value = b + "\n" + debugMessage.value }
            locationService?.setLatitude { b -> latitude.value = b }
            locationService?.setLongitude { b -> longitude.value = b }
            locationService?.setAltitude { b -> altitude.value = b}
            locationService?.setHAccMts { b -> hAcc.value = b }
            locationService?.setLocation { b -> location.value = b }
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
                    location.value
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
        var filter = IntentFilter()
        filter.addAction(droneService?.STATUS_NO_FLIGHTMODE)
        filter.addAction(droneService?.STATUS_NO_GPS)
        filter.addAction(droneService?.STATUS_NO_EKF)
        registerReceiver(usbReceiver, filter)
    }

}

@Composable
fun MainScreen(
    isBound: Boolean, isConnected: Boolean, pitch: String
    , roll: String, yaw: String, droneService: DroneService?
    , droneStatus: String, mode: String, gpxFix: String, satellites: String
    , debugMessage: List<String>, locationPermissionRequest: ActivityResultLauncher<Array<String>>
    , locationService: LocationService?, latitude: String
    , longitude: String, altitude: String, hAcc: String, location: Location?
)
{
    val scroll = rememberScrollState()
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
                    Text(text = "Alt: $altitude")
                    Text(text = "hAcc: $hAcc meters")
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
        Card(shape = RoundedCornerShape(8.dp,)
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
                        Text(text = debugMessage[it])
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

            var sliderAltitude by remember {
                mutableStateOf(5f)
            }
            Column() {
                Row(horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                ){
                    Column() {
                        Text(text = "Altitude: ${sliderAltitude.roundToInt()}")
                        Slider(
                            value = sliderAltitude,
                            onValueChange = { sliderAltitude = it },
                            steps = 7,
                            valueRange = 4f..20f
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
                        else if(armButtonLabel == "Disarm"){
                            droneService?.disarm()
                        }
                    }) {
                        Text(text = armButtonLabel)
                    }
                    Button(enabled = controlsEnabled,
                        onClick = { droneService?.takeoff(sliderAltitude) }) {
                        Text(text = "Takeoff")
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
                        onClick = { droneService?.land() }) {
                        Text(text = "Land")
                    }
                    Button(
                        enabled = controlsEnabled,
                        onClick = {
                            if (location != null) {
                                location.altitude = sliderAltitude.toDouble()
                                droneService?.gotoLocation2(location)
                            }
                        }
                    ) {
                        Text(text = "Come")
                    }
                }
            }
        }
    }
}

enum class Status {
    Offline, Unarmed, Armed, InFlight, Landing
}

@Composable
fun LaunchedEffectMainScreen(
    isUsbConnected: MutableState<Boolean>,
    droneService: DroneService?
){
    var pitch = remember {
        mutableStateOf("")
    }
    if(isUsbConnected.value){
        LaunchedEffect(true){
            Log.d("lunch", "in launched effect")
            pitch.value = droneService?.ReadThread()?.getPitch().toString()
        }
    }
    Column() {
        Text(text = "USB Connected : ${isUsbConnected.value}")
        Text(text = "Pitch : ${pitch.value}")
    }
}




