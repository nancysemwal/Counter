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
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as DroneService.DroneBinder
            droneService = binder.getService()
            droneService?.setUsbStatus { b -> isUsbConnected.value = b }
            droneService?.setPitch { b -> pitch.value = b }
            droneService?.setRoll { b -> roll.value = b }
            droneService?.setYaw { b -> yaw.value = b }
            droneService?.setDroneStatus { b -> droneStatus.value = b }
            isBound.value = true
            if (droneService != null) {
                //isConnected.value = true
                //usbConnected = droneService!!.usbConnected
                Log.d("HAPTORK", "Service is Connected")
            } else {
                Log.d("HAPTORK", "Service not there")
            }
            setFilters()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            //droneService?.setBoundStatus { b -> isBound.value = false }
            isBound.value = false
        }

    }
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("HAPTORK", "HELLO")
        super.onCreate(savedInstanceState)
        setContent {
            CounterTheme {
                MainScreen(isBound.value, isUsbConnected.value, pitch.value,
                    roll.value, yaw.value, droneService, droneStatus.value)
                /*LaunchedEffectMainScreen(isUsbConnected = isUsbConnected
                    , droneService = droneService)*/
            }
        }
        //setFilters()
    }

    override fun onStart() {
        super.onStart()
        Intent(this, DroneService::class.java).also { intent ->
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }

        Log.d("HAPTORK", "onStart")
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
    , droneStatus: String
)
{
    Log.d("sttsAct","$droneStatus")
    val armable : Boolean? = true
    val armed : Boolean? = false

    Box(modifier = Modifier.fillMaxSize()){
        Column() {
            Text(text = "Service Connected : $isBound")
            Text(text = "USB Connected : $isConnected")
            Text(text = "Pitch : $pitch")
            Text(text = "Roll : $roll")
            Text(text = "Yaw : $yaw")
            Text(text = "Drone Status : $droneStatus")
        }
        val armButtonEnabled = when(armable){
            true -> true
            else -> false
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
                true -> "Disarm"
                false -> "Arm"
                null -> TODO()
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
                droneService?.arm() }) {
                Text(text = armButtonLabel)
            }
            Button(enabled = takeoffButtonEnabled, onClick = { /*TODO*/ }) {
                Text(text = "Takeoff")
            }
            Button(enabled = landButtonEnabled, onClick = { /*TODO*/ }) {
                Text(text = "Land")
            }
        }
    }
}

enum class Status {
    Offline, Online, Armable, Armed, InFlight
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




