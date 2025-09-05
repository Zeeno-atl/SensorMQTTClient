package github.umer0586.sensorserver.setting

import android.content.Context
import android.content.SharedPreferences
import github.umer0586.sensorserver.R

class AppSettings(context: Context)
{


    private val context: Context
    private val sharedPreferences: SharedPreferences

    init
    {
        this.context = context.applicationContext
        sharedPreferences = context.getSharedPreferences(
            context.getString(R.string.shared_pref_file),
            Context.MODE_PRIVATE
        )
    }

    fun saveHttpPortNo(portNo: Int)
    {
        sharedPreferences.edit()
                .putInt("httpPortNo", portNo)
                .apply()
    }

    fun getHttpPortNo(): Int
    {
        return sharedPreferences.getInt(
                "httpPortNo",
                DEFAULT_HTTP_PORT_NO
        )
    }

    fun saveSamplingRate(samplingRate: Int)
    {
        sharedPreferences.edit()
            .putInt(context.getString(R.string.pref_key_sampling_rate), samplingRate)
            .apply()
    }

    fun getSamplingRate(): Int
    {
        return sharedPreferences.getInt(
            context.getString(R.string.pref_key_sampling_rate),
            DEFAULT_SAMPLING_RATE
        )
    }

    fun enableLocalHostOption(state: Boolean)
    {
        sharedPreferences.edit()
            .putBoolean(context.getString(R.string.pref_key_localhost), state)
            .apply()
    }

    fun isLocalHostOptionEnable(): Boolean
    {
        return sharedPreferences.getBoolean(context.getString(R.string.pref_key_localhost), false)
    }

    fun enableHotspotOption(state: Boolean)
    {
        sharedPreferences.edit()
            .putBoolean(context.getString(R.string.pref_key_hotspot), state)
            .apply()
    }

    fun isHotspotOptionEnabled(): Boolean
    {
        return sharedPreferences.getBoolean(context.getString(R.string.pref_key_hotspot), false)
    }

    fun listenOnAllInterfaces(state : Boolean)
    {
        sharedPreferences.edit()
            .putBoolean(context.getString(R.string.pref_key_all_interface), state)
            .apply()
    }

    fun isAllInterfaceOptionEnabled() : Boolean
    {
        return sharedPreferences.getBoolean(context.getString(R.string.pref_key_all_interface), false)
    }

    fun saveDiscoverable(state: Boolean)
    {
        sharedPreferences.edit()
            .putBoolean(context.getString(R.string.pref_key_discoverable), state)
            .apply()

    }

    fun isDiscoverableEnabled() : Boolean
    {
        return sharedPreferences.getBoolean(context.getString(R.string.pref_key_discoverable), DEFAULT_DISCOVERABLE)
    }

    // MQTT Settings
    fun saveMqttBrokerHost(host: String) {
        sharedPreferences.edit()
            .putString("mqtt_broker_host", host)
            .apply()
    }
    
    fun getMqttBrokerHost(): String {
        return sharedPreferences.getString("mqtt_broker_host", DEFAULT_MQTT_BROKER_HOST) 
            ?: DEFAULT_MQTT_BROKER_HOST
    }
    
    fun saveMqttBrokerPort(port: Int) {
        sharedPreferences.edit()
            .putInt("mqtt_broker_port", port)
            .apply()
    }
    
    fun getMqttBrokerPort(): Int {
        return sharedPreferences.getInt("mqtt_broker_port", DEFAULT_MQTT_BROKER_PORT)
    }
    
    fun getDeviceId(): String {
        val key = "device_id"
        var deviceId = sharedPreferences.getString(key, null)
        
        if (deviceId == null) {
            deviceId = generateDeviceId()
            sharedPreferences.edit().putString(key, deviceId).apply()
        }
        
        return deviceId
    }
    
    fun saveMqttQosLevel(qos: Int) {
        sharedPreferences.edit()
            .putInt("mqtt_qos", qos)
            .apply()
    }
    
    fun getMqttQosLevel(): Int {
        return sharedPreferences.getInt("mqtt_qos", DEFAULT_MQTT_QOS)
    }
    
    private fun generateDeviceId(): String {
        return "android_${System.currentTimeMillis().toString(36)}"
    }

    companion object
    {


        private const val DEFAULT_WEBSOCKET_PORT_NO = 8080
        private const val DEFAULT_HTTP_PORT_NO = 9090
        private const val DEFAULT_SAMPLING_RATE = 200000
        private const val DEFAULT_DISCOVERABLE = false
        private const val DEFAULT_MQTT_BROKER_HOST = "localhost"
        private const val DEFAULT_MQTT_BROKER_PORT = 1883
        private const val DEFAULT_MQTT_QOS = 0
    }
}