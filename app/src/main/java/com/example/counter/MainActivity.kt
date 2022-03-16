package com.example.counter

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material.Text
import androidx.compose.runtime.*
import com.example.counter.ui.theme.CounterTheme

class MainActivity : ComponentActivity() {

    private var droneService : DroneService? = null
    private var isBound = mutableStateOf(false)
    private var isConnected = mutableStateOf(false)
    private var pitch = mutableStateOf("")
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as DroneService.DroneBinder
            droneService = binder.getService()
            droneService?.setUsbStatus { b -> isConnected.value = b }
            droneService?.setPitch { b -> pitch.value = b }
            isBound.value = true
            if (droneService != null) {
                //isConnected.value = true
                //usbConnected = droneService!!.usbConnected
                Log.d("HAPTORK", "Service is Connected")
            } else {
                Log.d("HAPTORK", "Service not there")
            }
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
                MainScreen(isBound.value, isConnected.value, pitch.value)
            }
        }
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
}

@Composable
fun MainScreen(isBound : Boolean, isConnected : Boolean, pitch : String)
{
    Column() {
        Text(text = "Service Connected : $isBound")
        Text(text = "USB Connected : $isConnected")
        Text(text = "Pitch : $pitch")
    }
}


