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
import io.dronefleet.mavlink.ardupilotmega.CopterMode
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
    private var _setDroneStatus : (String) -> Unit = {b -> {}}
    private var _writeToDebugSpace : (String) -> Unit = {b -> {}}

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

    fun setDroneStatus(_fn: (String) -> Unit){
        _setDroneStatus = _fn
    }

    fun writeToDebugSpace(_fn: (String) -> Unit){
        _writeToDebugSpace = _fn
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
                                droneStatus = Status.Unarmed.name
                                _setDroneStatus(droneStatus)
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
                    _setRoll("")
                    _setYaw("")
                    _setDroneStatus("")
                    _writeToDebugSpace("")
                    droneStatus = ""
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
    private val systemId = 255
    private val componentId = 0
    private var flag = -1
    private lateinit var readThread : DroneService.ReadThread
    private lateinit var usbConnection : UsbDeviceConnection
    private lateinit var usbDevice: UsbDevice
    private lateinit var serialPort : UsbSerialDevice
    private lateinit var mavlinkConnection : MavlinkConnection
    private lateinit var pitch : String
    private lateinit var roll : String
    private lateinit var yaw : String
    private var armable : Boolean = false
    private var armed : Boolean = false
    private var flightMode : String = ""
    private var gpsFixType : Int = -1
    private var ekfStatusFlags : Int = -1
    private var droneStatus : String = ""
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
                    Log.d("thrd", "$mavlinkConnection")
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
                        var baseMode = heartbeatMsg.baseMode().value()
                        Log.d("hrtbt", "basemode : $baseMode")
                        flightMode = CopterMode.values()[customMode].toString()
                        armed = (baseMode and 128) != 0
                        if(armed && (droneStatus == Status.InFlight.name ||
                                    droneStatus == Status.Unarmed.name)){
                            _setDroneStatus(Status.Armed.name)
                            _writeToDebugSpace("Arming Successful")
                        }
                        else if(!armed){
                            droneStatus = Status.Unarmed.name
                            _setDroneStatus(droneStatus)
                        }
                    }
                    if(message.payload is GpsRawInt){
                        var gpsMsg : GpsRawInt = message.payload as GpsRawInt
                        gpsFixType = gpsMsg.fixType().value()
                    }
                    if(message.payload is EkfStatusReport){
                        var ekfMsg : EkfStatusReport = message.payload as EkfStatusReport
                        ekfStatusFlags = ekfMsg.flags().value() and 512
                    }
                    if(message.payload is CommandAck){
                        var ackMsg : CommandAck = message.payload as CommandAck
                        if(ackMsg.command().value() == 400){
                            if(flag == 1){
                                if (ackMsg.result().value() != 0){
                                    _writeToDebugSpace("Could not arm")
                                }
                            }else if(flag == 0){
                                if(ackMsg.result().value() == 0){
                                    droneStatus = Status.Unarmed.name
                                }
                            }
                        }
                        if(ackMsg.command().value() == 22){
                            if (ackMsg.result().value() == 0) {
                                droneStatus = Status.InFlight.name
                                _setDroneStatus(Status.InFlight.name)
                            }
                        }
                        if(ackMsg.command().value() == 21){
                            if(ackMsg.result().value() == 0){
                                droneStatus = Status.Armed.name
                            }
                        }
                    }
                    /*if(message.payload is EkfStatusReport){
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
                    }*/
                }catch (e : IOException){
                    Log.d("xxception", "hello")
                }
            }
        }
        fun setKeep(keep: Boolean) {
            this.keep.set(keep)
        }
        fun getPitch() : String{
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
        var intent : Intent? = null
        when{
            flightMode == "COPTER_MODE_INITIALISING" -> intent = Intent(STATUS_NO_FLIGHTMODE)
            gpsFixType <= 1 -> intent = Intent(STATUS_NO_GPS)
            ekfStatusFlags <= 0 -> intent = Intent(STATUS_NO_EKF)
        }
        if(intent != null){
            sendBroadcast(intent)
        }
        armable = (flightMode != "COPTER_MODE_INITIALISING")
                && (gpsFixType > 1)
                && (ekfStatusFlags > 0)
        /*if (armable){
            _setDroneStatus(Status.Armable.name)
        }*/
        return armable
    }

    private fun changeMode(mode : Int){
        val command : MavCmd = MavCmd.MAV_CMD_DO_SET_MODE
        val message : CommandLong = CommandLong.builder()
            .targetSystem(1)
            .targetComponent(0)
            .command(command)
            .confirmation(0)
            .param1(1F)
            .param1(mode.toFloat())
            .param3(0F)
            .param4(0F)
            .param5(0F)
            .param6(0F)
            .param7(0F)
            .build();
        try{
            mavlinkConnection.send2(systemId, componentId, message)
        }catch (e : IOException){

        }
    }

    fun arm() {
        flag = 1
        if(true){
        //if(isArmable()){
            var guidedMode: Int = CopterMode.COPTER_MODE_GUIDED.ordinal
            changeMode(mode = guidedMode)
            val command : MavCmd = MavCmd.MAV_CMD_COMPONENT_ARM_DISARM
            val message : CommandLong = CommandLong.builder()
                .targetSystem(1)
                .targetComponent(0)
                .command(command)
                .confirmation(0)
                .param1(1F)
                .param1(0F)
                .param3(0F)
                .param4(0F)
                .param5(0F)
                .param6(0F)
                .param7(0F)
                .build();
            try{
                mavlinkConnection.send2(systemId, componentId, message)
                _writeToDebugSpace("Sent Arming Command")
            }catch (e : IOException){

            }
        }
    }
    fun disarm() {
        flag = 0
        if(true){
            //if(armed){
            val guidedMode: Int = CopterMode.COPTER_MODE_GUIDED.ordinal
            changeMode(mode = guidedMode)
            val command : MavCmd = MavCmd.MAV_CMD_COMPONENT_ARM_DISARM
            val message : CommandLong = CommandLong.builder()
                .targetSystem(1)
                .targetComponent(0)
                .command(command)
                .confirmation(0)
                .param1(0F)
                .param1(0F)
                .param3(0F)
                .param4(0F)
                .param5(0F)
                .param6(0F)
                .param7(0F)
                .build();
            try{
                mavlinkConnection.send2(systemId, componentId, message)
            }catch (e : IOException){

            }
        }
    }

    fun takeoff(altitude : Float){
        val command : MavCmd = MavCmd.MAV_CMD_NAV_TAKEOFF
        val message : CommandLong = CommandLong.builder()
            .targetSystem(1)
            .targetComponent(0)
            .command(command)
            .confirmation(0)
            .param1(1F)
            .param1(0F)
            .param3(0F)
            .param4(0F)
            .param5(0F)
            .param6(0F)
            .param7(altitude)
            .build();
        try{
            mavlinkConnection.send2(systemId, componentId, message)
        }catch (e : IOException){

        }
    }

    fun land(latitude: Float = 0F, longitude: Float = 0F){
        val command : MavCmd = MavCmd.MAV_CMD_NAV_LAND
        val message : CommandLong = CommandLong.builder()
            .targetSystem(1)
            .targetComponent(0)
            .command(command)
            .confirmation(0)
            .param1(1F)
            .param1(0F)
            .param3(0F)
            .param4(0F)
            .param5(latitude)
            .param6(longitude)
            .param7(0F)
            .build();
        try{
            mavlinkConnection.send2(systemId, componentId, message)
            Log.d("lndg","$latitude + $longitude")
        }catch (e : IOException){

        }
    }
}
