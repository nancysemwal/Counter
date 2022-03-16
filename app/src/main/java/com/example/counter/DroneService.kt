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
import androidx.lifecycle.MutableLiveData
import com.felhr.usbserial.CDCSerialDevice
import com.felhr.usbserial.SerialInputStream
import com.felhr.usbserial.SerialOutputStream
import com.felhr.usbserial.UsbSerialDevice
import io.dronefleet.mavlink.Mavlink2Message
import io.dronefleet.mavlink.MavlinkConnection
import io.dronefleet.mavlink.MavlinkMessage
import io.dronefleet.mavlink.common.Attitude
import io.dronefleet.mavlink.common.Heartbeat
import io.dronefleet.mavlink.common.RequestDataStream
import java.io.EOFException
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random

class DroneService : Service() {


    private var _setUsbStatus : (Boolean) -> Unit = { b -> {}}
    private var _setBoundStatus : (Boolean) -> Unit = { b -> {}}
    private var _setUsbConnection : (String) -> Unit = { b -> {}}
    private var _setPitch : (String) -> Unit = {b -> {}}
    fun setUsbStatus(_fn: (Boolean)->Unit) {
        _setUsbStatus = _fn
    }

    fun setBoundStatus(_fn: (Boolean) -> Unit) {
        _setBoundStatus = _fn
    }

    fun setUsbConnection(_fn: (String) -> Unit){
        _setUsbConnection = _fn
    }

    fun setPitch(_fn: (String) -> Unit){
        _setPitch = _fn
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
                                usbConnected.value = true
                                usbConnected2 = true
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
                    serialPortConnected = false
                }
            }
        }
    }

    private val binder = DroneBinder()

    private val mGenerator = Random(10)

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
    var usbConnected = MutableLiveData<Boolean>(false)
    var usbConnected2 = false
    var SERVICE_CONNECTED = MutableLiveData<Boolean>(false)

    val randomNumber : Int
        get() = mGenerator.nextInt()

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

    private fun startMav(){
        if(usbDevice != null && usbConnection != null){
            serialPort = UsbSerialDevice.createUsbSerialDevice(usbDevice, usbConnection)
            Log.d("drishi", "$serialPort")
        }
        if(serialPort != null){
            if(serialPort.syncOpen()){
                serialPortConnected = true
                serialPort.setBaudRate(57600)
                var inputStream : SerialInputStream = serialPort.inputStream
                //Log.d("strm",inputStream.read().toString())
                var outputStream : SerialOutputStream = serialPort.outputStream
                mavlinkConnection = MavlinkConnection.create(inputStream, outputStream)
                Log.d("Drishi", "$mavlinkConnection")
                var requestDataStreamMsg : RequestDataStream = RequestDataStream.builder()
                    .targetSystem(1)
                    .targetComponent(0)
                    .reqStreamId(0)
                    .reqMessageRate(1)
                    .startStop(1)
                    .build()
                try {
                    mavlinkConnection.send2(255, 0, requestDataStreamMsg)
                } catch (e : IOException){
                    Log.d("xxception",e.stackTraceToString())
                }
                Log.d("mavlink", mavlinkConnection.toString())
                try {
                    var mavMsg = mavlinkConnection.next()
                }
                catch (e : EOFException){
                    Log.d("xxception2", e.stackTraceToString())
                }

//                while(mavlinkConnection.next() != null){
//                    mavMsg = mavlinkConnection.next()
//                    if(mavMsg.payload is Attitude){
//                        Log.d("attitude", "${mavMsg.payload}")
//                    }else{
//                        Log.d("attitude", "hello")
//                    }
//                }
                //Log.d("Mavlink", "${mavMsg.payload.toString()}")
            }
        }
    }

    inner class ConnectionThread : Thread() {
        public override fun run() {
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
        public override fun run() {
            Log.d("thrd", "in read thread")
            //var message = mavlinkConnection.next()
            //Log.d("msgx", "${message.toString()}")
            while (keep.get()){
                try{
                    var message = mavlinkConnection.next()
                    if(message.payload is Attitude){
                        var attitudeMsg : Attitude = message.payload as Attitude
                        _setPitch(attitudeMsg.pitch().toString())
                    }
                }catch (e : IOException){
                    Log.d("xxception", "hello")
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
}