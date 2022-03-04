package com.example.counter

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder

class DroneService : Service() {

    private val binder = DroneBinder()
    inner class DroneBinder : Binder(){
        fun getService() : DroneService = this@DroneService
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }
}