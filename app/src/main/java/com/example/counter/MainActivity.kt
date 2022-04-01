package com.example.counter

import android.content.*
import android.location.Location
import android.location.LocationListener
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.counter.ui.theme.CounterTheme

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
    private var hAcc = mutableStateOf("")
    private var debugMessage = mutableStateOf("")

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
            droneService?.writeToDebugSpace { b -> debugMessage.value = b + "\n" + debugMessage.value }
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
            locationService?.writeToDebugSpace { b -> debugMessage.value = b + "\n" + debugMessage.value }
            locationService?.setLatitude { b -> latitude.value = b }
            locationService?.setLongitude { b -> longitude.value = b }
            locationService?.setHAccMts { b -> hAcc.value = b }
            Log.d("srvc","from main, location service connected")
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
                    debugMessage.value,
                    locationPermissionRequest,
                    locationService,
                    latitude.value,
                    longitude.value,
                    hAcc.value
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
    , debugMessage: String, locationPermissionRequest: ActivityResultLauncher<Array<String>>
    , locationService: LocationService?, latitude: String
    , longitude: String, hAcc: String
)
{
    val armed : Boolean = when(droneStatus){
        Status.Armed.name -> true
        else -> false
    }
    val scroll = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize())
    {
        Column() {
            Text(text = "Service Connected : $isBound")
            Text(text = "USB Connected : $isConnected")
            Text(text = "Pitch : $pitch")
            Text(text = "Roll : $roll")
            Text(text = "Yaw : $yaw")
            Text(text = "Drone Status : $droneStatus")
            Text(text = "Mode : $mode")
            Text(text = "GPS Fix : $gpxFix")
            Text(text = "Satellites Visible : $satellites")
            Text(text = "Latitude: $latitude")
            Text(text = "Longitude: $longitude")
            Text(text = "hAcc: $hAcc meters")
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = "Messages: ")
            Text(text = debugMessage, modifier = Modifier.verticalScroll(scroll))
        }

        //TODO: Position this button just above the drone control buttons
        val comeButtonEnabled : Boolean = when(droneStatus){
            Status.Offline.name -> false
            else -> true
        }
        Row(modifier = Modifier
            .padding(15.dp)
            .align(Alignment.CenterEnd)){
            Button(enabled = comeButtonEnabled, onClick = {
                locationPermissionRequest.launch(arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                ))
                val location = locationService?.getLocation()
                if (location != null) {
                    droneService?.gotoLocation2(location)
                }
            }) {
                Text(text = "Come")
            }
        }

        Row(
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(15.dp)
                .align(Alignment.BottomCenter)
        ) {
            val armButtonLabel = when(armed){
                true -> "Unarm"
                false -> "Arm"
            }
            val takeoffButtonEnabled = when(droneStatus){
                Status.Armed.name -> true
                else -> false
            }
            val landButtonEnabled = when(droneStatus){
                Status.InFlight.name -> true
                else -> false
            }
            Button(onClick = {
                if(droneStatus == Status.Unarmed.name) {
                    droneService?.arm()
                }else if(droneStatus == Status.Armed.name){
                    droneService?.disarm()
                }
            }) {
                Text(text = armButtonLabel)
            }
            Button(enabled = takeoffButtonEnabled, onClick = {
                droneService?.takeoff(5F) }) {
                Text(text = "Takeoff")
            }
            Button(enabled = landButtonEnabled, onClick = {
                droneService?.land()
            }) {
                Text(text = "Land")
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




