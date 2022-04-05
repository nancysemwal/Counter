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
import android.location.Location
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
    private var _setMode : (String) -> Unit = {b -> {}}
    private var _setGpsFix : (String) -> Unit = {b -> {}}
    private var _setSatellites : (String) -> Unit = {b -> {}}

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

    fun setMode(_fn: (String) -> Unit){
        _setMode = _fn
    }

    fun setGpsFix(_fn: (String) -> Unit){
        _setGpsFix = _fn
    }

    fun setSatellites(_fn: (String) -> Unit){
        _setSatellites = _fn
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
                    _setMode("")
                    _setGpsFix("")
                    _setSatellites("")
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
    private var satellites : String = ""
    private var ekfStatusFlags : Int = -1
    private var droneStatus : String = ""
    /*private var groundSpeed : Float = 0F
    private var airSpeed : Float = 0F*/
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
                        val heartbeatMsg : Heartbeat = message.payload as Heartbeat
                        val customMode : Int = heartbeatMsg.customMode().toInt()
                        val baseMode = heartbeatMsg.baseMode().value()
                        val fmode = CopterMode.values()[customMode].name
                        flightMode = fmode.split("_").last()
                        _setMode(flightMode)
                        armed = ((baseMode and 128) != 0)
                        if(armed && (droneStatus == Status.Unarmed.name)){
                                        droneStatus = Status.Armed.name
                                        _setDroneStatus(droneStatus)
                            Log.d("hrtbt","armed")
                                         _writeToDebugSpace("Drone Armed")
                        }
                        else if(!armed){
                            droneStatus = Status.Unarmed.name
                            _setDroneStatus(droneStatus)
                        }
                    }
                    if(message.payload is GpsRawInt){
                        val gpsMsg : GpsRawInt = message.payload as GpsRawInt
                        gpsFixType = gpsMsg.fixType().value()
                        satellites = gpsMsg.satellitesVisible().toString()
                        _setGpsFix(gpsFixType.toString())
                        _setSatellites(satellites)
                    }
                    if(message.payload is EkfStatusReport){
                        val ekfMsg : EkfStatusReport = message.payload as EkfStatusReport
                        ekfStatusFlags = ekfMsg.flags().value() and 512
                    }
                    if (message.payload is Statustext){
                        val statusTextMsg : Statustext = message.payload as Statustext
                        val level : String = statusTextMsg.severity().entry().toString().split("_").last()
                        val logMsg : String = statusTextMsg.text()
                        if(level == "CRITICAL" || level == "WARNING") {
                            _writeToDebugSpace("$level $logMsg")
                        }
                        Log.d("ststxt","$level + $logMsg")
                    }
                    if(message.payload is CommandAck){
                        val ackMsg : CommandAck = message.payload as CommandAck
                        val command : String = ackMsg.command().entry().name
                        val result : String = ackMsg.result().entry().name
                        val reply = "$command sent\n$result"
                        Log.d("rply", reply)
                        _writeToDebugSpace(reply)
                        if(ackMsg.command().value() == 400 /*arm/disarm*/ ){
                            if(flag == 1 /*arm*/ ){
                                if (ackMsg.result().value() != 0){
                                    //_writeToDebugSpace("Could not arm")
                                }
                            }else if(flag == 0 /*disarm*/){
                                if(ackMsg.result().value() == 0){
                                    droneStatus = Status.Unarmed.name
                                }
                            }
                        }
                        if(ackMsg.command().value() == 22 /*takeoff*/ ){
                            if (ackMsg.result().value() == 0) {
                                droneStatus = Status.InFlight.name
                                _setDroneStatus(droneStatus)
                            }
                        }
                        if(ackMsg.command().value() == 21 /*landing*/){
                            if(ackMsg.result().value() == 0){
                                droneStatus = Status.Landing.name
                                _setDroneStatus(droneStatus)
                            }
                        }
                    }
                    if(message.payload is MissionAck){
                        val ackMsg : MissionAck = message.payload as MissionAck
                        val result : String = ackMsg.type().entry().name
                        _writeToDebugSpace(result)
                    }
                    if(message.payload is VfrHud){
                        val vfrHudMsg : VfrHud = message.payload as VfrHud
                        val groundSpeed = vfrHudMsg.groundspeed()
                        val airSpeed = vfrHudMsg.airspeed()
                        Log.d("vfrhud", "$groundSpeed $airSpeed")
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
            .param2(mode.toFloat())
            .param3(0F)
            .param4(0F)
            .param5(0F)
            .param6(0F)
            .param7(0F)
            .build();
        Log.d("msgs",message.toString())
        try{
            mavlinkConnection.send2(systemId, componentId, message)
        }catch (e : IOException){

        }
    }


    fun arm() {
        flag = 1
        //if(true){
        if(isArmable()){
            var guidedMode: Int = CopterMode.COPTER_MODE_GUIDED.ordinal
            changeMode(mode = guidedMode)
            val command : MavCmd = MavCmd.MAV_CMD_COMPONENT_ARM_DISARM
            val message : CommandLong = CommandLong.builder()
                .targetSystem(1)
                .targetComponent(0)
                .command(command)
                .confirmation(0)
                .param1(1F)
                .param2(0F)
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
    fun disarm() {
        flag = 0
        if(armed){
            val guidedMode: Int = CopterMode.COPTER_MODE_GUIDED.ordinal
            changeMode(mode = guidedMode)
            val command : MavCmd = MavCmd.MAV_CMD_COMPONENT_ARM_DISARM
            val message : CommandLong = CommandLong.builder()
                .targetSystem(1)
                .targetComponent(0)
                .command(command)
                .confirmation(0)
                .param1(0F)
                .param2(0F)
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
            .param1(0F)
            .param2(0F)
            .param3(0F)
            .param4(0F)
            .param5(0F)
            .param6(0F)
            .param7(altitude)
            .build();
        try{
            mavlinkConnection.send2(systemId, componentId, message)
            _writeToDebugSpace("Taking off with altitude value $altitude")
        }catch (e : IOException){
            _writeToDebugSpace(e.toString())
        }
    }

    fun land(latitude: Float = 0F, longitude: Float = 0F){
        val command : MavCmd = MavCmd.MAV_CMD_NAV_LAND
        val message : CommandLong = CommandLong.builder()
            .targetSystem(1)
            .targetComponent(0)
            .command(command)
            .confirmation(0)
            .param1(0F)
            .param2(0F)
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
            _writeToDebugSpace(e.toString())
        }
    }

    fun gotoLocation(location: Location){
        val altitude = location.altitude
        val latitude = location.latitude
        val longitude = location.longitude
        val command = MavCmd.MAV_CMD_NAV_WAYPOINT
        val message : CommandLong = CommandLong.builder()
            .targetSystem(1)
            .targetComponent(0)
            .command(command)
            .confirmation(0)
            .param1(0F)
            .param2(0F)
            .param3(0F)
            .param4(0F)
            .param5(latitude.toFloat())
            .param6(longitude.toFloat())
            .param7(altitude.toFloat())
            .build()
        try {
            mavlinkConnection.send2(systemId, componentId, message)
            Log.d("wpt","goto message sent $message")
        }catch (e : IOException){

        }
    }

    private fun setAirSpeed(airSpeed: Float){
        val speedType = 0F //airspeed
        val command : MavCmd = MavCmd.MAV_CMD_DO_CHANGE_SPEED
        val message = CommandLong.builder()
            .targetSystem(1)
            .targetComponent(0)
            .command(command)
            .confirmation(0)
            .param1(speedType)
            .param2(airSpeed)
            .param3(-1F) //throttle, indicates no change
            .param4(0F)
            .param5(0F)
            .param6(0F)
            .param7(0F)
            .build();
        try {
            mavlinkConnection.send2(systemId, componentId, message)
            _writeToDebugSpace("Air speed changed to $airSpeed m/s")
        }catch (e : IOException){

        }
    }

    private fun setGroundSpeed(groundSpeed: Float){
        val speedType = 1F //groundspeed
        val command : MavCmd = MavCmd.MAV_CMD_DO_CHANGE_SPEED
        val message = CommandLong.builder()
            .targetSystem(1)
            .targetComponent(0)
            .command(command)
            .confirmation(0)
            .param1(speedType)
            .param2(groundSpeed)
            .param3(-1F) //throttle, indicates no change
            .param4(0F)
            .param5(0F)
            .param6(0F)
            .param7(0F)
            .build();
        try {
            mavlinkConnection.send2(systemId, componentId, message)
            _writeToDebugSpace("Ground speed changed to $groundSpeed m/s")
        }catch (e : IOException){

        }
    }

    fun gotoLocation2(
        location: Location,
        groundSpeed: Float? = null, airSpeed: Float? = 1.0F ){

        if(location.accuracy > 7){
            _writeToDebugSpace("Location accuracy > 5 meters. Drone won't proceed")
            return
        }
        else{
            _writeToDebugSpace("Proceeding to coordinates with altitude ${location.altitude}")
            if(groundSpeed != null){
                setGroundSpeed(groundSpeed)
            }
            if(airSpeed != null){
                setAirSpeed(airSpeed)
            }
            val frame = MavFrame.MAV_FRAME_GLOBAL_RELATIVE_ALT //try relative alt also
            //val altitude = location.altitude
            val altitude = 10
            /*val latitude : Int = (location.latitude * 10000000).toInt() //try giving raw values also
            val longitude : Int = (location.longitude * 10000000).toInt()*/
            val latitude = location.latitude.toFloat()
            val longitude = location.longitude.toFloat()
            val command = MavCmd.MAV_CMD_NAV_WAYPOINT
            val message : MissionItem = MissionItem.builder()  //try mission item also
                .targetSystem(1)
                .targetComponent(0)
                .seq(0)
                .frame(frame)
                .command(command)
                .current(2) //try 1 also
                .autocontinue(0)
                .param1(0F)
                .param2(0F)
                .param3(0F)
                .param4(0F)
                .x(latitude)
                .y(longitude)
                .z(altitude.toFloat())
                //.missionType(MavMissionType.MAV_MISSION_TYPE_MISSION)
                .build()
            try {
                mavlinkConnection.send2(systemId, componentId, message)
                Log.d("wpt","$message")
            }catch (e : IOException){

            }
        }
    }
}
