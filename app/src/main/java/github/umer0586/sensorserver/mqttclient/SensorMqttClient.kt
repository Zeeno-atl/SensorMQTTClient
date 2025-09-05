package github.umer0586.sensorserver.mqttclient

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import androidx.core.app.ActivityCompat
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.*

class SensorMqttClient(
    private val context: Context,
    private val brokerHost: String,
    private val brokerPort: Int,
    private val deviceId: String,
    private val qosLevel: Int = 0
) : SensorEventListener, LocationListener {
    
    private val clientId = "mqtt_${deviceId}_${System.currentTimeMillis()}"
    private val brokerUrl = "tcp://$brokerHost:$brokerPort"
    private val mqttClient = MqttAndroidClient(context, brokerUrl, clientId)
    private val topicPrefix = "sensors/$deviceId"
    private val messageQueue = MessageQueue(maxAge = 10_000) // 10s buffer
    
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val minTimeMs = 5000L // Request location updates every 5 seconds minimum
    private val minDistanceM = 1f // Request updates when moved 1 meter minimum
    
    private var isConnected = false
    private var reconnectAttempts = 0
    private var reconnectDelay = 1000L // Start with 1s
    
    var onConnectionStatusChanged: ((Boolean, String?) -> Unit)? = null
    
    fun connect() {
        val options = MqttConnectOptions().apply {
            isAutomaticReconnect = false // We handle reconnection manually
            isCleanSession = true
            connectionTimeout = 30
            keepAliveInterval = 60
        }
        
        mqttClient.connect(options, null, object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                isConnected = true
                reconnectAttempts = 0
                reconnectDelay = 1000L
                onConnectionStatusChanged?.invoke(true, "Connected to broker")
                publishQueuedMessages()
                publishStatusMessage("connected")
                startLocationUpdates()
            }
            
            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                isConnected = false
                onConnectionStatusChanged?.invoke(false, exception?.message)
                scheduleReconnect()
            }
        })
        
        mqttClient.setCallback(object : MqttCallback {
            override fun connectionLost(cause: Throwable?) {
                isConnected = false
                onConnectionStatusChanged?.invoke(false, "Connection lost: ${cause?.message}")
                scheduleReconnect()
            }
            
            override fun messageArrived(topic: String?, message: MqttMessage?) {
                // Not needed for sensor publishing
            }
            
            override fun deliveryComplete(token: IMqttDeliveryToken?) {
                // Optional: track message delivery
            }
        })
    }
    
    private fun scheduleReconnect() {
        Handler(Looper.getMainLooper()).postDelayed({
            reconnectAttempts++
            connect()
            reconnectDelay = minOf(reconnectDelay * 2, 60_000L) // Exponential backoff, max 60s
        }, reconnectDelay)
    }
    
    override fun onSensorChanged(sensorEvent: SensorEvent) {
        val sensorType = when(sensorEvent.sensor.type) {
            android.hardware.Sensor.TYPE_ACCELEROMETER -> "accelerometer"
            android.hardware.Sensor.TYPE_GYROSCOPE -> "gyroscope"
            android.hardware.Sensor.TYPE_MAGNETIC_FIELD -> "magnetometer"
            android.hardware.Sensor.TYPE_GRAVITY -> "gravity"
            android.hardware.Sensor.TYPE_LINEAR_ACCELERATION -> "linear_acceleration"
            android.hardware.Sensor.TYPE_ROTATION_VECTOR -> "rotation_vector"
            else -> "sensor_${sensorEvent.sensor.type}"
        }
        val topic = "$topicPrefix/$sensorType"
        val message = createSensorMessage(sensorEvent, sensorType)
        publishMessage(topic, message)
    }
    
    override fun onLocationChanged(location: Location) {
        val topic = "$topicPrefix/gps"
        val message = createGpsMessage(location)
        publishMessage(topic, message)
    }
    
    fun publishTouchEvent(motionEvent: MotionEvent) {
        val topic = "$topicPrefix/touch"
        val message = createTouchMessage(motionEvent)
        publishMessage(topic, message)
    }
    
    private fun publishMessage(topic: String, message: String) {
        if (isConnected && mqttClient.isConnected) {
            try {
                mqttClient.publish(topic, message.toByteArray(), qosLevel, false)
            } catch (e: Exception) {
                messageQueue.add(topic, message)
            }
        } else {
            messageQueue.add(topic, message)
        }
    }
    
    private fun publishQueuedMessages() {
        messageQueue.drainTo { topic, message ->
            try {
                mqttClient.publish(topic, message.toByteArray(), qosLevel, false)
                true
            } catch (e: Exception) {
                false
            }
        }
    }
    
    private fun publishStatusMessage(status: String) {
        val topic = "$topicPrefix/status"
        val message = """
            {
                "deviceId": "$deviceId",
                "timestamp": ${System.currentTimeMillis()},
                "status": "$status",
                "message": "Client $status"
            }
        """.trimIndent()
        publishMessage(topic, message)
    }
    
    fun disconnect() {
        publishStatusMessage("disconnected")
        stopLocationUpdates()
        mqttClient.disconnect()
        isConnected = false
    }
    
    private fun createSensorMessage(sensorEvent: SensorEvent, sensorType: String): String = """
        {
            "deviceId": "$deviceId",
            "timestamp": ${sensorEvent.timestamp / 1_000_000}, 
            "sensorType": "$sensorType",
            "values": [${sensorEvent.values.joinToString(",")}],
            "accuracy": ${sensorEvent.accuracy}
        }
    """.trimIndent()
    
    private fun createGpsMessage(location: Location): String = """
        {
            "deviceId": "$deviceId",
            "timestamp": ${location.time},
            "latitude": ${location.latitude},
            "longitude": ${location.longitude},
            "altitude": ${location.altitude},
            "accuracy": ${location.accuracy},
            "speed": ${location.speed},
            "bearing": ${location.bearing}
        }
    """.trimIndent()
    
    private fun createTouchMessage(motionEvent: MotionEvent): String = """
        {
            "deviceId": "$deviceId", 
            "timestamp": ${System.currentTimeMillis()},
            "action": "${getActionString(motionEvent)}",
            "x": ${motionEvent.x},
            "y": ${motionEvent.y}
        }
    """.trimIndent()
    
    private fun getActionString(motionEvent: MotionEvent): String {
        return when (motionEvent.actionMasked) {
            MotionEvent.ACTION_DOWN -> "ACTION_DOWN"
            MotionEvent.ACTION_UP -> "ACTION_UP" 
            MotionEvent.ACTION_MOVE -> "ACTION_MOVE"
            else -> "ACTION_UNKNOWN"
        }
    }
    
    override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {
        // Not implemented for now
    }
    
    private fun startLocationUpdates() {
        if (!hasLocationPermission()) {
            return
        }
        
        try {
            // Try GPS first (more accurate)
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    minTimeMs,
                    minDistanceM,
                    this
                )
            }
            
            // Also try network provider as fallback
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    minTimeMs,
                    minDistanceM,
                    this
                )
            }
        } catch (e: SecurityException) {
            // Location permission was revoked
        }
    }
    
    private fun stopLocationUpdates() {
        try {
            locationManager.removeUpdates(this)
        } catch (e: SecurityException) {
            // Permission was revoked
        }
    }
    
    private fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ActivityCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
}