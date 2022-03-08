package com.example.counter

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.counter.ui.theme.CounterTheme

class MainActivity : ComponentActivity() {

    val counterViewModel by viewModels<CounterViewModel>()
    private lateinit var  droneService : DroneService
    private var bound : Boolean = false
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d("HAPTORK", "Service is Connected")
            val binder = service as DroneService.DroneBinder
            droneService = binder.getService()
            bound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bound = false
        }

    }
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("HAPTORK", "HELLO")
        counterViewModel.updateCount()
        super.onCreate(savedInstanceState)
        setContent {
            CounterTheme {
                //MainScreen(counterViewModel = counterViewModel)
                MainScreen(counterViewModel = counterViewModel, droneService = droneService)
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
        bound = false
    }

    fun onClick(){
        if(bound) {
            val num : Int = droneService.randomNumber
            Toast.makeText(this, "Number : $num", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun MainScreen(counterViewModel: CounterViewModel,
            droneService: DroneService){
    val count by counterViewModel.counter.observeAsState()
    val connected by droneService.SERVICE_CONNECTED.observeAsState()
    Column() {
        Text(text = count.toString())
        Text(text = "Service Connected: " + connected.toString())
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
