package com.example.counter

import android.content.*
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
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

    private var droneService : DroneService? = null
    private var isBound = mutableStateOf(false)
    private var isUsbConnected = mutableStateOf(false)
    private var pitch = mutableStateOf("")
    private var roll = mutableStateOf("")
    private var yaw = mutableStateOf("")
    private var droneStatus = mutableStateOf(Status.Offline.name)
    private var debugMessage = mutableStateOf("")
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as DroneService.DroneBinder
            droneService = binder.getService()
            droneService?.setUsbStatus { b -> isUsbConnected.value = b }
            droneService?.setPitch { b -> pitch.value = b }
            droneService?.setRoll { b -> roll.value = b }
            droneService?.setYaw { b -> yaw.value = b }
            droneService?.setDroneStatus { b -> droneStatus.value = b }
            droneService?.writeToDebugSpace { b -> debugMessage.value = debugMessage.value + "\n" + b }
            isBound.value = true
            setFilters()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound.value = false
        }

    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CounterTheme {
                MainScreen(isBound.value, isUsbConnected.value, pitch.value,
                    roll.value, yaw.value, droneService, droneStatus.value, debugMessage.value)
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
    }

    override fun onStop() {
        super.onStop()
        unbindService(serviceConnection)
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
    , droneStatus: String, debugMessage: String
)
{
    //TODO: set armable & armed from the service
    val armable : Boolean = true
    val armed : Boolean = false
    /*val armed : Boolean = when(droneStatus){
        Status.Armed.name -> true
        else -> false
    }*/

    Box(modifier = Modifier.fillMaxSize()){
        Column() {
            Text(text = "Service Connected : $isBound")
            Text(text = "USB Connected : $isConnected")
            Text(text = "Pitch : $pitch")
            Text(text = "Roll : $roll")
            Text(text = "Yaw : $yaw")
            Text(text = "Drone Status : $droneStatus")
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Messages: $debugMessage")
        }

        Row(
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(15.dp)
                .align(Alignment.BottomCenter)
        ) {
            val armButtonEnabled = when(armable){
                true -> true
                else -> false
            }
            val armButtonLabel = when(armed){
                true -> "Disarm"
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
            Button(enabled = armButtonEnabled, onClick = {
                if(droneStatus == Status.Unarmed.name) {
                    droneService?.arm()
                }else if(droneStatus == Status.Armed.name){
                    droneService?.disarm()
                }
            }) {
                Text(text = armButtonLabel)
            }
            Button(enabled = takeoffButtonEnabled, onClick = {
                droneService?.takeoff(10F) }) {
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
    Offline, Unarmed, Armable, Armed, InFlight
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




