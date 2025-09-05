package github.umer0586.sensorserver.fragments

import android.content.DialogInterface
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.core.text.HtmlCompat
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import github.umer0586.sensorserver.R
import github.umer0586.sensorserver.setting.AppSettings

class SettingsFragment : PreferenceFragmentCompat()
{


    private lateinit var appSettings: AppSettings



    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?)
    {
        setPreferencesFromResource(R.xml.settings_preference, rootKey)
        appSettings = AppSettings(requireContext())

        handleMqttBrokerHostPreference()
        handleMqttBrokerPortPreference()
        handleDeviceIdPreference()
        handleSamplingRatePreference()

    }


    private fun handleMqttBrokerHostPreference()
    {
        val mqttHostPref = findPreference<EditTextPreference>(getString(R.string.pref_key_mqtt_broker_host))

        mqttHostPref?.setOnPreferenceChangeListener { _, newValue ->
            val host = newValue.toString().trim()
            if (host.isNotEmpty()) {
                appSettings.saveMqttBrokerHost(host)
                return@setOnPreferenceChangeListener true
            } else {
                showAlertDialog("Please enter a valid MQTT broker host")
                return@setOnPreferenceChangeListener false
            }
        }
    }

    private fun handleDeviceIdPreference()
    {
        val deviceIdPref = findPreference<EditTextPreference>(getString(R.string.pref_key_device_id))

        deviceIdPref?.setOnPreferenceChangeListener { _, newValue ->
            val deviceId = newValue.toString().trim()
            if (deviceId.isNotEmpty() && deviceId.matches(Regex("^[a-zA-Z0-9_-]+$"))) {
                appSettings.saveDeviceId(deviceId)
                return@setOnPreferenceChangeListener true
            } else {
                showAlertDialog("Please enter a valid device ID (alphanumeric, underscore, and dash only)")
                return@setOnPreferenceChangeListener false
            }
        }
    }

    private fun handleMqttBrokerPortPreference()
    {
        val mqttPortPref = findPreference<EditTextPreference>(getString(R.string.pref_key_mqtt_broker_port))

        mqttPortPref?.setOnBindEditTextListener { editText: EditText ->
            editText.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
        }

        mqttPortPref?.setOnPreferenceChangeListener { _, newValue ->
            try
            {
                val portNo: Int = newValue.toString().toInt()
                if (portNo >= 1 && portNo <= 65535)
                {
                    appSettings.saveMqttBrokerPort(portNo)
                    return@setOnPreferenceChangeListener true
                }
                else
                {
                    showAlertDialog("Please enter a valid port number (1-65535)")
                    return@setOnPreferenceChangeListener false
                }
            }
            catch (e: NumberFormatException)
            {
                e.printStackTrace()
                showAlertDialog("Please enter a valid port number")
                return@setOnPreferenceChangeListener false
            }
        }
    }

    private fun handleSamplingRatePreference()
    {
        val samplingRatePref = findPreference<EditTextPreference>(getString(R.string.pref_key_sampling_rate))
        //samplingRatePref?.summary = appSettings.getSamplingRate().toString()

        val dialogText = """
                The data delay (or sampling rate) controls the interval at which sensor events are sent to application. Change this value before starting a Server
                <br><br>
                <font color="#689f38"><b>Note : </b></font> <i>The delay that you specify is only a suggested delay. The Android system and other applications can alter this delay.</i>
                <br><br>
                 Normal Rate : <font color="#5c6bc0"><b>200000</b>μs</font>
                <br>
                 Fastest Rate : <font color="#5c6bc0"><b>0</b>μs</font>
                <br><br>
                 Enter value in <font color="#5c6bc0"><b>Microseconds</b></font>
                """.trimIndent()

        samplingRatePref?.dialogMessage = HtmlCompat.fromHtml(dialogText,HtmlCompat.FROM_HTML_MODE_LEGACY)
        samplingRatePref?.setOnBindEditTextListener { editText: EditText ->
            editText.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
        }

        samplingRatePref?.setOnPreferenceChangeListener { _, newValue ->
            try
            {
                if (newValue.toString().trim { it <= ' ' }.isEmpty())
                    return@setOnPreferenceChangeListener false

                val samplingRate: Int = newValue.toString().toInt()

                if (samplingRate < 0)
                {
                    AlertDialog.Builder(requireContext())
                        .setTitle("Invalid Input")
                        .setMessage("Negative value not allowed")
                        .setCancelable(false)
                        .setPositiveButton("Okay") { dialog: DialogInterface, _ -> dialog.cancel() }
                        .create()
                        .show()
                    return@setOnPreferenceChangeListener false
                }
                appSettings.saveSamplingRate(samplingRate)
                return@setOnPreferenceChangeListener true
            }
            catch (e: NumberFormatException)
            {
                e.printStackTrace()
                AlertDialog.Builder(requireContext())
                    .setTitle("Invalid Input")
                    .setMessage("Value too large")
                    .setCancelable(false)
                    .setPositiveButton("Okay") { dialog: DialogInterface, _ -> dialog.cancel() }
                    .create()
                    .show()
                return@setOnPreferenceChangeListener false
            }
        }
    }

    private fun showAlertDialog(message: CharSequence)
    {
        AlertDialog.Builder(requireContext())
            .setTitle("Invalid Port No")
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("Okay") { dialog: DialogInterface, _ -> dialog.cancel() }
            .create()
            .show()
    }

    companion object
    {
        private val TAG: String = SettingsFragment::class.java.getName()
    }
}