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
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.counter.ui.theme.CounterTheme

class MainActivity : ComponentActivity() {

    private var droneService : DroneService? = null
    private val bound = MutableLiveData<Boolean>(false)
    private var isConnected = mutableStateOf(false)
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as DroneService.DroneBinder
            droneService = binder.getService()
            droneService?.setSetterFn { b -> isConnected.value = b }
            //bound = true
            bound.value = true
            if (droneService != null) {
                //isConnected.value = true
                //usbConnected = droneService!!.usbConnected
                Log.d("HAPTORK", "Service is Connected")
            } else {
                Log.d("HAPTORK", "Service not there")
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            //bound = false
            bound.value = false
        }

    }
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("HAPTORK", "HELLO")
        super.onCreate(savedInstanceState)
        setContent {
            CounterTheme {
                MainScreen(isConnected.value)
            //MainScreen(counterViewModel = counterViewModel)
            }
        }
        //counterViewModel.updateCount()
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
        //bound = false
        bound.value = false
    }
}

@Composable
fun MainScreen(isConnected : Boolean)
{
    Column() {
        Text(text = isConnected.toString())
    }
}


