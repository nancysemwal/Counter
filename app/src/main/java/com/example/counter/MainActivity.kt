package com.example.counter

import android.os.Bundle
import android.util.Log
import android.view.View
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
    override fun onCreate(savedInstanceState: Bundle?) {
        counterViewModel.updateCount()
        super.onCreate(savedInstanceState)
        setContent {
            CounterTheme {
                MainScreen(counterViewModel = counterViewModel)
            }
        }
        //counterViewModel.updateCount()
    }
}

@Composable
fun MainScreen(counterViewModel: CounterViewModel){
    val count by counterViewModel.counter.observeAsState()
    Column() {
        Text(text = count.toString())
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
