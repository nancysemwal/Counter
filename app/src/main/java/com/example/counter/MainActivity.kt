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
import androidx.compose.runtime.setValue
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.counter.ui.theme.CounterTheme

class MainActivity : ComponentActivity() {

    val counterViewModel by viewModels<CounterViewModel>()
    private var  droneService : DroneService? = null
    //private var bound : Boolean = false
    private val bound = MutableLiveData<Boolean>(false)
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d("HAPTORK", "Service is Connected")
            val binder = service as DroneService.DroneBinder
            droneService = binder.getService()
            //bound = true
            bound.value = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            //bound = false
            bound.value = false
        }

    }
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("HAPTORK", "HELLO")
        counterViewModel.updateCount()
        super.onCreate(savedInstanceState)
        setContent {
            CounterTheme {
                MainScreen(counterViewModel = counterViewModel, droneService = droneService)
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
fun MainScreen(
    counterViewModel: CounterViewModel, droneService: DroneService? = null){
    val count by counterViewModel.counter.observeAsState()
    val connected by droneService!!.usbConnected.observeAsState()
    Column() {
        Text(text = count.toString())
        if (connected!!) {
            Text(text = "Service Connected: $connected")
        }
        Button(onClick = {counterViewModel.updateCount()}) {
            Text(text = "Update")
        }
    }
}



class CounterViewModel : ViewModel(){
    private val _count = MutableLiveData(0)
    var count = 0
    val counter : LiveData<Int> = _count
    fun updateCount(){
        Log.d("SEE","inside update")
        _count.value = ++count
    }

}
