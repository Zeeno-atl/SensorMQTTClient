package github.umer0586.sensorserver.service

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import github.umer0586.sensorserver.R
import github.umer0586.sensorserver.activities.MainActivity
import github.umer0586.sensorserver.mqttclient.SensorMqttClient
import github.umer0586.sensorserver.setting.AppSettings

interface ClientStateListener {
    fun onClientConnected(deviceId: String, brokerHost: String)
    fun onClientDisconnected()
    fun onClientError(error: String?)
    fun onClientAlreadyConnected(deviceId: String)
}

class MqttClientService : Service() {
    
    private var sensorMqttClient: SensorMqttClient? = null
    private var clientStateListener: ClientStateListener? = null
    private lateinit var appSettings: AppSettings
    private var broadcastMessageReceiver: BroadcastReceiver? = null
    
    private val binder: IBinder = LocalBinder()
    
    companion object {
        private val TAG: String = MqttClientService::class.java.simpleName
        const val CHANNEL_ID = "MqttClientServiceChannel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_DISCONNECT_CLIENT = "ACTION_DISCONNECT_CLIENT"
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate()")
        appSettings = AppSettings(applicationContext)
        createNotificationChannel()
        registerBroadcastReceiver()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand() - Starting MQTT client service")
        
        handleAndroid8andAbove()
        
        if (intent?.action == ACTION_DISCONNECT_CLIENT) {
            disconnectClient()
            return START_NOT_STICKY
        }
        
        if (sensorMqttClient != null) {
            Log.i(TAG, "MQTT client already running")
            clientStateListener?.onClientAlreadyConnected(appSettings.getDeviceId())
            return START_NOT_STICKY
        }
        
        startClient()
        return START_NOT_STICKY
    }
    
    private fun startClient() {
        val brokerHost = appSettings.getMqttBrokerHost()
        val brokerPort = appSettings.getMqttBrokerPort()
        val deviceId = appSettings.getDeviceId()
        val qosLevel = appSettings.getMqttQosLevel()
        
        Log.i(TAG, "Starting MQTT client - Broker: $brokerHost:$brokerPort, Device: $deviceId")
        
        sensorMqttClient = SensorMqttClient(
            context = applicationContext,
            brokerHost = brokerHost,
            brokerPort = brokerPort, 
            deviceId = deviceId,
            qosLevel = qosLevel
        ).apply {
            onConnectionStatusChanged = { connected, message ->
                if (connected) {
                    Log.i(TAG, "MQTT client connected")
                    clientStateListener?.onClientConnected(deviceId, brokerHost)
                    showConnectedNotification(deviceId, brokerHost)
                } else {
                    Log.w(TAG, "MQTT client disconnected: $message")
                    clientStateListener?.onClientDisconnected()
                    clientStateListener?.onClientError(message)
                    showDisconnectedNotification(message)
                }
            }
        }
        
        sensorMqttClient?.connect()
    }
    
    private fun showConnectedNotification(deviceId: String, brokerHost: String) {
        val disconnectIntent = Intent(ACTION_DISCONNECT_CLIENT).apply {
            setPackage(packageName)
        }
        val disconnectPendingIntent = PendingIntent.getBroadcast(
            this, 0, disconnectIntent, 
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )
        
        val mainActivityIntent = Intent(this, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(
            this, 0, mainActivityIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MQTT Client Connected")
            .setContentText("Device: $deviceId → mqtt://$brokerHost")
            .setSmallIcon(R.drawable.ic_baseline_info_24)
            .setContentIntent(contentPendingIntent)
            .addAction(R.drawable.ic_baseline_info_24, "Disconnect", disconnectPendingIntent)
            .build()
            
        startForeground(NOTIFICATION_ID, notification)
    }
    
    private fun showDisconnectedNotification(error: String?) {
        val mainActivityIntent = Intent(this, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(
            this, 0, mainActivityIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MQTT Client Disconnected")
            .setContentText(error ?: "Connection lost")
            .setSmallIcon(R.drawable.ic_baseline_info_24)
            .setContentIntent(contentPendingIntent)
            .build()
            
        startForeground(NOTIFICATION_ID, notification)
    }
    
    fun disconnectClient() {
        Log.i(TAG, "Disconnecting MQTT client")
        sensorMqttClient?.disconnect()
        sensorMqttClient = null
        clientStateListener?.onClientDisconnected()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    
    fun setClientStateListener(listener: ClientStateListener?) {
        clientStateListener = listener
    }
    
    fun checkState() {
        sensorMqttClient?.let {
            clientStateListener?.onClientAlreadyConnected(appSettings.getDeviceId())
        }
    }
    
    fun isClientRunning(): Boolean {
        return sensorMqttClient != null
    }
    
    fun getMqttClient(): SensorMqttClient? {
        return sensorMqttClient
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "MQTT Client Service"
            val descriptionText = "MQTT sensor data publishing service"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun registerBroadcastReceiver() {
        broadcastMessageReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    ACTION_DISCONNECT_CLIENT -> {
                        Log.i(TAG, "Received disconnect broadcast")
                        disconnectClient()
                    }
                }
            }
        }
        
        val intentFilter = IntentFilter().apply {
            addAction(ACTION_DISCONNECT_CLIENT)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(broadcastMessageReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(broadcastMessageReceiver, intentFilter)
        }
    }
    
    private fun handleAndroid8andAbove() {
        val mainActivityIntent = Intent(this, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(
            this, 0, mainActivityIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Starting MQTT Client...")
            .setContentText("Connecting to broker...")
            .setSmallIcon(R.drawable.ic_baseline_info_24)
            .setContentIntent(contentPendingIntent)
            .build()
            
        startForeground(NOTIFICATION_ID, notification)
    }
    
    inner class LocalBinder : Binder() {
        val service: MqttClientService get() = this@MqttClientService
    }
    
    override fun onBind(intent: Intent): IBinder = binder
    
    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "onDestroy()")
        sensorMqttClient?.disconnect()
        broadcastMessageReceiver?.let {
            unregisterReceiver(it)
        }
    }
}