package com.example.counter

import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.felhr.usbserial.SerialInputStream
import com.felhr.usbserial.SerialOutputStream
import com.felhr.usbserial.UsbSerialDevice
import io.dronefleet.mavlink.MavlinkConnection
import io.dronefleet.mavlink.annotations.MavlinkEntryInfo
import io.dronefleet.mavlink.annotations.MavlinkEnum
import io.dronefleet.mavlink.ardupilotmega.CopterMode
import io.dronefleet.mavlink.ardupilotmega.EkfStatusFlags
import io.dronefleet.mavlink.ardupilotmega.EkfStatusReport
import io.dronefleet.mavlink.common.*
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

class DroneService : Service() {


    private var _setUsbStatus : (Boolean) -> Unit = { b -> {}}
    private var _setBoundStatus : (Boolean) -> Unit = { b -> {}}
    private var _setPitch : (String) -> Unit = {b -> {}}
    private var _setRoll : (String) -> Unit = {b -> {}}
    private var _setYaw : (String) -> Unit = {b -> {}}

    fun setUsbStatus(_fn: (Boolean)->Unit) {
        _setUsbStatus = _fn
    }

    fun setBoundStatus(_fn: (Boolean) -> Unit) {
        _setBoundStatus = _fn
    }

    fun setPitch(_fn: (String) -> Unit){
        _setPitch = _fn
    }

    fun setRoll(_fn: (String) -> Unit){
        _setRoll = _fn
    }

    fun setYaw(_fn: (String) -> Unit){
        _setYaw = _fn
    }

    private val usbReceiver = object : BroadcastReceiver(){
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d("HAPTORK", "onReceive()")
            if (intent != null) {
                if(ACTION_USB_PERMISSION == intent.action) {
                    synchronized(this) {
                        val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            device?.apply {
                                Log.d("Drishi", "Permission Granted")
                                _setUsbStatus(true)
                                usbConnection = usbManager.openDevice(device)
                                usbDevice = device
                                Log.d("Drishi", "$usbConnection")
                                ConnectionThread().start()
                                //startMav()
                            }
                        } else {
                            //TODO - convey "permission not granted" message to user
                            Log.d("HAPTORK", "Permission is not Granted")
                        }
                    }
                }
                else if(ACTION_USB_ATTACHED == intent.action) {
                    if(!serialPortConnected) {
                        findSerialPortDevice()
                    }
                }
                else if(ACTION_USB_DETACHED == intent.action){
                    if(serialPortConnected){
                        serialPort.syncClose()
                        readThread.setKeep(false)

                    }
                    _setUsbStatus(false)
                    _setPitch("")
                    serialPortConnected = false
                }
            }
        }
    }

    private val binder = DroneBinder()

    private val usbManager by lazy {
        getSystemService(Context.USB_SERVICE) as UsbManager
    }

    private val ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION"
    private val ACTION_USB_ATTACHED = "android.hardware.usb.action.USB_DEVICE_ATTACHED"
    private val ACTION_USB_DETACHED = "android.hardware.usb.action.USB_DEVICE_DETACHED";
    private var serialPortConnected = false
    private lateinit var readThread : DroneService.ReadThread
    private lateinit var usbConnection : UsbDeviceConnection
    private lateinit var usbDevice: UsbDevice
    private lateinit var serialPort : UsbSerialDevice
    private lateinit var mavlinkConnection : MavlinkConnection
    private lateinit var pitch : String
    private lateinit var roll : String
    private lateinit var yaw : String
    private var armable : Boolean = false
    private var flightMode : String = ""
    private var gpsFixType : Int = -1
    private var ekfStatusFlags : Int = -1
    val STATUS_NO_GPS = "com.nancy.dronestatus.NO_GPS"
    val STATUS_NO_EKF = "com.nancy.dronestatus.NO_EKF"
    val STATUS_NO_FLIGHTMODE = "com.nancy.dronestatus.NO_FLIGHTMODE"

    override fun onCreate() {
        super.onCreate()
        Log.d("HAPTORK","Service's onCreate()")
        _setBoundStatus(true)
        setFilter()
        findSerialPortDevice()
    }

    private fun setFilter() {
        var filter = IntentFilter()
        filter.addAction(ACTION_USB_PERMISSION)
        filter.addAction(ACTION_USB_ATTACHED)
        filter.addAction(ACTION_USB_DETACHED)
        registerReceiver(usbReceiver, filter)
    }

    private fun findSerialPortDevice() {
        Log.d("HAPTORK", "findSerialPortDevice")
        val deviceList : HashMap<String, UsbDevice> = usbManager.deviceList
        val intent = Intent(ACTION_USB_PERMISSION)
        deviceList.values.forEach(){
            val pendingIntent = PendingIntent.getBroadcast(this, 0, intent
            , 0)
            usbManager.requestPermission(it, pendingIntent)
        }
    }

    inner class ConnectionThread : Thread() {
        override fun run() {
            serialPort = UsbSerialDevice.createUsbSerialDevice(usbDevice, usbConnection)
            if (serialPort != null) {
                if (serialPort.syncOpen()) {
                    serialPortConnected = true
                    serialPort.setBaudRate(57600)
                    var inputStream: SerialInputStream = serialPort.inputStream
                    var outputStream: SerialOutputStream = serialPort.outputStream
                    mavlinkConnection = MavlinkConnection.create(inputStream, outputStream)
                    Log.d("thrd", "${mavlinkConnection}")
                    val requestDataStream = RequestDataStream.builder()
                        .targetSystem(1)
                        .targetComponent(0)
                        .reqStreamId(0)
                        .reqMessageRate(1)
                        .startStop(1)
                        .build()
                    try {
                        mavlinkConnection.send2(255, 0, requestDataStream)
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                    readThread = ReadThread()
                    readThread.start()
                }
            }
        }
    }

    inner class ReadThread : Thread() {
        var keep: AtomicBoolean = AtomicBoolean(true)
        override fun run() {
            Log.d("thrd", "in read thread")
            //var message = mavlinkConnection.next()
            //Log.d("msgx", "${message.toString()}")
            while (keep.get()){
                try{
                    var message = mavlinkConnection.next()
                    if(message.payload is Attitude){
                        var attitudeMsg : Attitude = message.payload as Attitude
                        pitch = attitudeMsg.pitch().toString()
                        _setPitch(attitudeMsg.pitch().toString())
                        _setRoll(attitudeMsg.roll().toString())
                        _setYaw(attitudeMsg.yaw().toString())
                    }
                    if(message.payload is Heartbeat){
                        var heartbeatMsg : Heartbeat = message.payload as Heartbeat
                        var customMode : Int = heartbeatMsg.customMode().toInt()
                        flightMode = CopterMode.values()[customMode].toString()
                    }
                    if(message.payload is GpsRawInt){
                        var gpsMsg : GpsRawInt = message.payload as GpsRawInt
                        gpsFixType = gpsMsg.fixType().value()
                        Log.d("armable", "$gpsFixType")
                    }
                    if(message.payload is EkfStatusReport){
                        var ekfMsg : EkfStatusReport = message.payload as EkfStatusReport
                        var castToEKF = EkfStatusFlags.EKF_PRED_POS_HORIZ_ABS as MavlinkEnum
                        //var x = castToEkf as MavlinkEnum
                        //if ekfMsg.flags().value()
                        //var x = EkfStatusFlags.values()[castToEKF]
                        //x.
                                //as MavlinkEntryInfo
                        Log.d("ekf","$ekfMsg + ${ekfMsg.flags().value()} + $castToEKF")

                        //var x : EkfStatusFlags = ekfMsg.flags().value() as EkfStatusFlags
                        //var y = (x == EkfStatusFlags.EKF_PRED_POS_HORIZ_ABS)
                        //Log.d("ekf","$ekfMsg + $x + $y")

                        //ekfStatusFlags = (x == EkfStatusFlags.EKF_PRED_POS_HORIZ_ABS)
                    }
                }catch (e : IOException){
                    Log.d("xxception", "hello")
                }
            }
        }
        fun setKeep(keep: Boolean) {
            this.keep.set(keep)
        }
        fun getPitch() : String{
            Log.d("lunch", "in get pitch")
            return pitch
        }
    }

    inner class DroneBinder : Binder(){
        fun getService() : DroneService = this@DroneService
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(usbReceiver)
    }

    fun isArmable() : Boolean{
        Log.d("armable", "in armable")
        var intent : Intent? = null
        when{
            flightMode == "COPTER_MODE_INITIALISING" -> intent = Intent(STATUS_NO_FLIGHTMODE)
            gpsFixType <= 1 -> intent = Intent(STATUS_NO_GPS)
            ekfStatusFlags <= 0 -> intent = Intent(STATUS_NO_EKF)
        }
        /*if(flightMode == "COPTER_MODE_INITIALISING") {
            Log.d("armable", "no mode")
            intent = Intent(STATUS_NO_FLIGHTMODE)
        }
        else if(gpsFixType <= 1){
            intent = Intent(STATUS_NO_GPS)
        }
        else if(ekfStatusFlags <= 0){
            intent = Intent(STATUS_NO_EKF)
        }*/
        if(intent != null){
            Log.d("armable","sending ${intent.action}")
            sendBroadcast(intent)
        }
        armable = (flightMode != "COPTER_MODE_INITIALISING")
                && (gpsFixType > 1)
                && (ekfStatusFlags > 0)
        return armable
    }

    fun arm() {
        if(true){
            var mode: Int = CopterMode.COPTER_MODE_GUIDED.ordinal
        }
    }
}