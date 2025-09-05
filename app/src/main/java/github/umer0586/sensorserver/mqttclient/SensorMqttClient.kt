package github.umer0586.sensorserver.mqttclient

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors
import android.view.MotionEvent
import androidx.core.app.ActivityCompat
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import github.umer0586.sensorserver.setting.AppSettings

class SensorMqttClient(
    private val context: Context,
    private val brokerHost: String,
    private val brokerPort: Int,
    private val deviceId: String,
    private val qosLevel: Int = 0
) : SensorEventListener, LocationListener {
    
    private val clientId = "mqtt_${deviceId}_${System.currentTimeMillis()}"
    private val brokerUrl = "tcp://$brokerHost:$brokerPort"
    private val persistence = MemoryPersistence()
    private val mqttClient = MqttClient(brokerUrl, clientId, persistence)
    private val topicPrefix = "sensors/$deviceId"
    private val messageQueue = MessageQueue(maxAge = 10_000) // 10s buffer
    
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val appSettings = AppSettings(context)
    private val minTimeMs = 1000L // Request location updates every 1 second minimum
    private val minDistanceM = 0f // No distance minimum - rely only on time interval
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // Sensor batching to prevent ANR  
    private val sensorDataBuffer = mutableMapOf<String, FloatArray>()
    private val batchHandler = Handler(Looper.getMainLooper())
    private val batchingIntervalMs = 100L // Batch every 100ms for reliability
    private var batchingRunnable: Runnable? = null
    private var batchingScheduled = false
    
    private var isConnected = false
    private var reconnectAttempts = 0
    private var reconnectDelay = 1000L // Start with 1s
    
    var onConnectionStatusChanged: ((Boolean, String?) -> Unit)? = null
    
    init {
        // Start GPS and sensors immediately when client is created (independent of MQTT connection)
        startLocationUpdates()
        startSensorUpdates()
    }
    
    fun connect() {
        executor.execute {
            try {
                val options = MqttConnectOptions().apply {
                    isAutomaticReconnect = false // We handle reconnection manually
                    isCleanSession = true
                    connectionTimeout = 30
                    keepAliveInterval = 60
                }
                
                mqttClient.setCallback(object : MqttCallback {
                    override fun connectionLost(cause: Throwable?) {
                        isConnected = false
                        mainHandler.post {
                            onConnectionStatusChanged?.invoke(false, "Connection lost: ${cause?.message}")
                        }
                        scheduleReconnect()
                    }
                    
                    override fun messageArrived(topic: String?, message: MqttMessage?) {
                        // Not needed for sensor publishing
                    }
                    
                    override fun deliveryComplete(token: IMqttDeliveryToken?) {
                        // Optional: track message delivery
                    }
                })
                
                mqttClient.connect(options)
                isConnected = true
                reconnectAttempts = 0
                reconnectDelay = 1000L
                
                mainHandler.post {
                    onConnectionStatusChanged?.invoke(true, "Connected to broker")
                    publishQueuedMessages()
                    publishStatusMessage("connected")
                    // GPS and sensors already started in init
                }
                
            } catch (exception: Exception) {
                isConnected = false
                mainHandler.post {
                    onConnectionStatusChanged?.invoke(false, exception.message)
                }
                scheduleReconnect()
            }
        }
    }
    
    private fun scheduleReconnect() {
        mainHandler.postDelayed({
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
        
        // Copy sensor values to avoid SensorEvent reuse issues
        val values = sensorEvent.values.clone()
        sensorDataBuffer[sensorType] = values
        scheduleBatchPublish()
    }
    
    override fun onLocationChanged(location: Location) {
        // Allow GPS publishing even when not connected (for debugging)
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
                val mqttMessage = MqttMessage(message.toByteArray()).apply {
                    qos = qosLevel
                    isRetained = false
                }
                mqttClient.publish(topic, mqttMessage)
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
                val mqttMessage = MqttMessage(message.toByteArray()).apply {
                    qos = qosLevel
                    isRetained = false
                }
                mqttClient.publish(topic, mqttMessage)
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
    
    private fun scheduleBatchPublish() {
        // Only schedule if not already scheduled
        if (batchingScheduled) return
        
        batchingScheduled = true
        batchingRunnable = Runnable {
            batchingScheduled = false
            publishBatchedSensorData()
        }
        batchHandler.postDelayed(batchingRunnable!!, batchingIntervalMs)
    }
    
    private fun publishBatchedSensorData() {
        if (sensorDataBuffer.isEmpty()) return
        
        executor.execute {
            val currentBatch = sensorDataBuffer.toMap()
            sensorDataBuffer.clear()
            
            currentBatch.forEach { (sensorType, values) ->
                val topic = "$topicPrefix/$sensorType"
                val message = createSensorMessageFromValues(values, sensorType)
                publishMessage(topic, message)
            }
        }
    }
    
    fun disconnect() {
        // Immediately stop sensors and set disconnected to prevent ANR
        isConnected = false
        stopSensorUpdates()
        // Keep GPS running even when MQTT disconnected
        
        // Cancel any pending batch publish
        batchingRunnable?.let { batchHandler.removeCallbacks(it) }
        batchingScheduled = false
        sensorDataBuffer.clear()
        
        executor.execute {
            try {
                publishStatusMessage("disconnected")
                stopLocationUpdates() // Stop GPS only after final status message
                mqttClient.disconnect()
            } catch (e: Exception) {
                // Ignore disconnect errors
            }
        }
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
    
    private fun createSensorMessageFromValues(values: FloatArray, sensorType: String): String = """
        {
            "deviceId": "$deviceId",
            "timestamp": ${System.currentTimeMillis()}, 
            "sensorType": "$sensorType",
            "values": [${values.joinToString(",")}],
            "accuracy": 0
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
    
    private fun startSensorUpdates() {
        try {
            // Get sampling rate from settings (in microseconds)
            val samplingRateUs = appSettings.getSamplingRate()
            
            // Unregister first to ensure clean state
            sensorManager.unregisterListener(this)
            
            // Register for common sensors
            val sensorsToRegister = listOf(
                android.hardware.Sensor.TYPE_ACCELEROMETER,
                android.hardware.Sensor.TYPE_GYROSCOPE, 
                android.hardware.Sensor.TYPE_MAGNETIC_FIELD,
                android.hardware.Sensor.TYPE_GRAVITY,
                android.hardware.Sensor.TYPE_LINEAR_ACCELERATION,
                android.hardware.Sensor.TYPE_ROTATION_VECTOR
            )
            
            sensorsToRegister.forEach { sensorType ->
                sensorManager.getDefaultSensor(sensorType)?.let { sensor ->
                    val success = sensorManager.registerListener(this, sensor, samplingRateUs)
                    if (!success) {
                        // Fallback to SENSOR_DELAY_GAME if custom rate fails
                        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore sensor registration errors
        }
    }
    
    private fun stopSensorUpdates() {
        sensorManager.unregisterListener(this)
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