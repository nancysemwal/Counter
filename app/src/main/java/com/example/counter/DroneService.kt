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
import java.math.RoundingMode
import java.text.DecimalFormat
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.collections.HashMap
import kotlin.math.*

class DroneService : Service() {


    private var _setUsbStatus : (Boolean) -> Unit = { b -> {}}
    private var _setBoundStatus : (Boolean) -> Unit = { b -> {}}
    private var _setPitch : (String) -> Unit = {b -> {}}
    private var _setRoll : (String) -> Unit = {b -> {}}
    private var _setYaw : (String) -> Unit = {b -> {}}
    private var _setDroneStatus : (String) -> Unit = {b -> {}}
    private var _writeToDebugSpace : (String) -> Unit = { {} }
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

    fun setWriteToDebugSpace(_fn: (String) -> Unit){
        _writeToDebugSpace = _fn
    }

    private val usbReceiver = object : BroadcastReceiver(){
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent != null) {
                if(ACTION_USB_PERMISSION == intent.action) {
                    synchronized(this) {
                        val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            device?.apply {
                                _setUsbStatus(true)
                                droneStatus = Status.Unarmed.name
                                _setDroneStatus(droneStatus)
                                usbConnection = usbManager.openDevice(device)
                                usbDevice = device
                                ConnectionThread().start()
                            }
                        } else {
                                _writeToDebugSpace("USB Permission not granted")
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
        _setBoundStatus(true)
        setFilter()
        findSerialPortDevice()
    }

    private fun setFilter() {
        val filter = IntentFilter()
        filter.addAction(ACTION_USB_PERMISSION)
        filter.addAction(ACTION_USB_ATTACHED)
        filter.addAction(ACTION_USB_DETACHED)
        registerReceiver(usbReceiver, filter)
    }

    private fun findSerialPortDevice() {
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
                    val inputStream: SerialInputStream = serialPort.inputStream
                    val outputStream: SerialOutputStream = serialPort.outputStream
                    mavlinkConnection = MavlinkConnection.create(inputStream, outputStream)
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
            val decimalFormat = DecimalFormat("#.##")
            decimalFormat.roundingMode = RoundingMode.DOWN
            while (keep.get()){
                try{
                    val message = mavlinkConnection.next()
                    if(message.payload is Attitude){
                        val attitudeMsg : Attitude = message.payload as Attitude
                        pitch = decimalFormat.format(attitudeMsg.pitch()).toString()
                        roll = decimalFormat.format(attitudeMsg.roll()).toString()
                        yaw = decimalFormat.format(attitudeMsg.yaw()).toString()
                        _setPitch(pitch)
                        _setRoll(roll)
                        _setYaw(yaw)
                    }
                    if(message.payload is Heartbeat){
                        val heartbeatMsg : Heartbeat = message.payload as Heartbeat
                        val customMode : Int = heartbeatMsg.customMode().toInt()
                        val baseMode = heartbeatMsg.baseMode().value()
                        val fMode = CopterMode.values()[customMode].name
                        flightMode = fMode.split("_").last()
                        _setMode(flightMode)
                        armed = ((baseMode and 128) != 0)
                        if(armed && (droneStatus == Status.Unarmed.name))
                        {
                            droneStatus = Status.Armed.name
                            _setDroneStatus(droneStatus)
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
                    }
                    if(message.payload is CommandAck){
                        val ackMsg : CommandAck = message.payload as CommandAck
                        val command : String = ackMsg.command().entry().name
                        val result : String = ackMsg.result().entry().name
                        val reply = "$command sent\n$result"
                        _writeToDebugSpace(reply)
                        if(ackMsg.command().value() == 400 /*arm/disarm*/ ){
                            if(flag == 1 /*arm*/ ){
                                if (ackMsg.result().value() != 0){
                                    //_writeToDebugSpace("Could not arm")
                                }
                            }else if(flag == 0 /*disarm*/){
                                if(ackMsg.result().value() == 0){
                                    droneStatus = Status.Unarmed.name
                                    _setDroneStatus(droneStatus)
                                    _writeToDebugSpace("Drone unarmed")
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
                    /*if(message.payload is MissionAck){
                        val ackMsg : MissionAck = message.payload as MissionAck
                        val result : String = ackMsg.type().entry().name
                        _writeToDebugSpace(result)
                    }*/
                    if(message.payload is VfrHud){
                        val vfrHudMsg : VfrHud = message.payload as VfrHud
                        val groundSpeed = vfrHudMsg.groundspeed()
                        val airSpeed = vfrHudMsg.airspeed()
                    }
                }catch (e : IOException){

                }
            }
        }
        fun setKeep(keep: Boolean) {
            this.keep.set(keep)
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

    private fun isArmable() : Boolean{
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
        try{
            mavlinkConnection.send2(systemId, componentId, message)
        }catch (e : IOException){

        }
    }


    fun arm() {
        flag = 1
        if(isArmable()){
            val guidedMode: Int = CopterMode.COPTER_MODE_GUIDED.ordinal
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
        _writeToDebugSpace("Taking off with altitude $altitude")
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
            _writeToDebugSpace("Landing using NAV_LAND")
        }catch (e : IOException){
            _writeToDebugSpace(e.toString())
        }
        //TODO: Change mode from AUTO as specified in the ArduPilot docs
    }

    fun landUsingSetMode(latitude: Float = 0F, longitude: Float = 0F){
        //val landMode : Int = CopterMode.COPTER_MODE_LAND.ordinal
        //TODO: Ordinal gives value 8 while LAND mode is 9
        val landMode : Int = 9
        _writeToDebugSpace("Landing using DO_SET_MODE")
        droneStatus = Status.Landing.name
        _setDroneStatus(droneStatus)
        changeMode(mode = landMode)
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
        }catch (e : IOException){

        }
    }

    fun distanceInMeters(currentLoc : Location, targetLoc : Location): Double{
        var distance : Double = 0.0
        val earthRadius = 6371000
        val latDiff = Math.toRadians(currentLoc.latitude - targetLoc.latitude)
        val lonDiff = Math.toRadians(currentLoc.longitude - targetLoc.longitude)
        val a = sin(latDiff / 2) * sin(latDiff / 2 ) +
                cos(Math.toRadians(currentLoc.latitude)) * cos(Math.toRadians(targetLoc.latitude)) *
                sin(lonDiff / 2 ) * sin(lonDiff / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        distance = earthRadius * c
        return distance
    }

    fun calculateAirspeed(distance: Float): Float{
        val speed : Float = 0.5F * distance
        if(speed <= 1F) return 1F
        if(speed >= 5F) return 5F
        return speed
    }

    private var lastAltitude : Double = 10.0
    fun gotoLocation(
        location: Location,
        altitude: Double? = null,
        groundSpeed: Float? = null,
        airSpeed: Float? = 1.0F,
        distance: Float? = 0F //only for logging purpose
    ){
        if(droneStatus != Status.InFlight.name){
            _writeToDebugSpace("Fatal: GOTO Failed as drone not in flight")
            return
        }
        val acc = 15
        if(location.accuracy > acc){
            _writeToDebugSpace("GPS Location accuracy > $acc meters. Drone won't proceed")
            return
        }
        if (altitude == null) {
            location.altitude = lastAltitude
        } else {
            location.altitude = altitude
            lastAltitude = altitude
        }
        if(groundSpeed != null){
            setGroundSpeed(groundSpeed)
        }
        if(airSpeed != null){
            setAirSpeed(airSpeed)
        }
        val frame = MavFrame.MAV_FRAME_GLOBAL_RELATIVE_ALT //try relative alt also
        //val altitude = location.altitude.toFloat()
        //val altitude = 10F
        val latitude = location.latitude.toFloat() //try giving raw values also
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
            .z(location.altitude.toFloat())
            .build()
        try {
            mavlinkConnection.send2(systemId, componentId, message)
            _writeToDebugSpace("Going to ${location.latitude} , ${location.longitude}" +
                    " with \n - altitude $altitude mts" +
                    "\n - distance $distance mts " +
                    "\n - airspeed $airSpeed m/s" +
                    "\n - groundspeed $groundSpeed m/s")
        }catch (e : IOException){

        }
    }
}
/*
fun calculateHeading(
        location1: Location,
        location2: Location
    ): Double {
        val loc1latRad = Math.toRadians(location1.latitude)
        val loc1lonRad = Math.toRadians(location1.longitude)
        val loc2latRad = Math.toRadians(location2.latitude)
        val loc2lonRad = Math.toRadians(location2.longitude)
        val x = sin(loc2lonRad - loc1lonRad) * cos(loc2latRad)
        val y = cos(loc1latRad) * sin(loc2latRad) -
                sin(loc1latRad) * cos(loc2latRad) * cos(loc2lonRad - loc1lonRad)
        val theta = atan2(x, y)
        val heading = (theta * 180 / Math.PI + 360) % 360
        return heading
    }

    fun getNewCoords(
        location: Location,
        heading: Double,
        distance: Double
    ): Location{
        val oldLatitude = Math.toRadians(location.latitude)
        val oldLongitude = Math.toRadians(location.longitude)
        val headingInRadians = Math.toRadians(heading)
        val earthRadius = 6371 //6371000
        val angularDistance = distance / earthRadius
        val newLatitude = asin(sin(oldLatitude) * cos(angularDistance) +
                cos(oldLatitude) * sin(angularDistance) * cos(heading))
        val newLongitude = oldLongitude +
                atan2(sin(headingInRadians) * sin(angularDistance) * cos(oldLatitude), cos(angularDistance) - sin(oldLatitude) * sin(newLatitude))
        val newLocation = Location("dummyprovider")
        newLocation.longitude = Math.toDegrees(newLongitude)
        newLocation.latitude = Math.toDegrees(newLatitude)
        Log.d("brng", "$newLocation")
        return newLocation
    }

    fun getOffsetLocation(
        prevLocation: Location,
        currLocation: Location,
        offset: Double
    ): Location {
        val heading = calculateHeading(prevLocation, currLocation)
        return getNewCoords(prevLocation, heading, offset)
    }

    fun calculateHeadingEuclid(
        location1: Location,
        location2: Location,
    ): Double{
        val loc1latRad = Math.toRadians(location1.latitude)
        val loc1lonRad = Math.toRadians(location1.longitude)
        val loc2latRad = Math.toRadians(location2.latitude)
        val loc2lonRad = Math.toRadians(location2.longitude)
        val deltaX = (loc1latRad - loc2latRad)
        val deltaY = loc1lonRad - loc2lonRad
        val theta = atan(deltaY / deltaX)
        val headingEuclid = Math.toDegrees(theta)
        return headingEuclid
    }
 */