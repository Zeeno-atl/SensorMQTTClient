package github.umer0586.sensorserver.fragments

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import github.umer0586.sensorserver.R
import github.umer0586.sensorserver.databinding.FragmentClientBinding
import github.umer0586.sensorserver.service.ClientStateListener
import github.umer0586.sensorserver.service.MqttClientService
import github.umer0586.sensorserver.service.ServiceBindHelper
import github.umer0586.sensorserver.setting.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ClientFragment : Fragment(), ClientStateListener {
    
    private var mqttClientService: MqttClientService? = null
    private lateinit var serviceBindHelper: ServiceBindHelper
    private lateinit var appSettings: AppSettings
    
    private var _binding: FragmentClientBinding? = null
    private val binding get() = _binding!!
    
    companion object {
        private val TAG: String = ClientFragment::class.java.simpleName
    }
    
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseLocationGranted = permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        
        if (fineLocationGranted || coarseLocationGranted) {
            showMessage("GPS permission granted - GPS data will be included")
            // Start MQTT service now that permission is granted
            val intent = Intent(context, MqttClientService::class.java)
            ContextCompat.startForegroundService(requireContext(), intent)
        } else {
            showMessage("GPS permission denied - GPS data will not be available")
            // Start MQTT service anyway (GPS won't work but sensors will)
            val intent = Intent(context, MqttClientService::class.java)
            ContextCompat.startForegroundService(requireContext(), intent)
        }
    }
    
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = FragmentClientBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.i(TAG, "onViewCreated")
        
        appSettings = AppSettings(requireContext())
        
        serviceBindHelper = ServiceBindHelper(
            context = requireContext(),
            service = MqttClientService::class.java,
            componentLifecycle = lifecycle
        )
        
        serviceBindHelper.onServiceConnected { binder ->
            val localBinder = binder as MqttClientService.LocalBinder
            mqttClientService = localBinder.service
            
            mqttClientService?.setClientStateListener(this)
            mqttClientService?.checkState()
        }
        
        hideStatus()
        
        binding.connectButton.setOnClickListener { v ->
            if (v.tag == "disconnected") {
                startClient()
            } else if (v.tag == "connected") {
                stopClient()
            }
        }
        
        updateUI(false)
    }
    
    private fun startClient() {
        Log.d(TAG, "startClient() called")
        
        // Check location permission and request if needed
        if (!hasLocationPermission()) {
            requestLocationPermission()
            // Service will start after permission is granted (see locationPermissionLauncher)
        } else {
            // Permission already granted, start service immediately
            val intent = Intent(context, MqttClientService::class.java)
            ContextCompat.startForegroundService(requireContext(), intent)
        }
    }
    
    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            requireContext(),
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun requestLocationPermission() {
        locationPermissionLauncher.launch(arrayOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ))
    }
    
    private fun stopClient() {
        Log.d(TAG, "stopClient() called")
        
        val intent = Intent(MqttClientService.ACTION_DISCONNECT_CLIENT).apply {
            setPackage(requireContext().packageName)
        }
        requireContext().sendBroadcast(intent)
    }
    
    override fun onClientConnected(deviceId: String, brokerHost: String) {
        Log.d(TAG, "onClientConnected() called")
        lifecycleScope.launch(Dispatchers.Main) {
            showStatus("mqtt://$brokerHost → $deviceId")
            showPulseAnimation()
            
            binding.connectButton.tag = "connected"
            binding.connectButton.text = "DISCONNECT"
            
            showMessage("Connected to MQTT broker")
        }
    }
    
    override fun onClientDisconnected() {
        Log.d(TAG, "onClientDisconnected() called")
        lifecycleScope.launch(Dispatchers.Main) {
            hideStatus()
            hidePulseAnimation()
            
            binding.connectButton.tag = "disconnected" 
            binding.connectButton.text = "CONNECT"
            
            showMessage("Disconnected from MQTT broker")
        }
    }
    
    override fun onClientError(error: String?) {
        Log.w(TAG, "onClientError() called: $error")
        lifecycleScope.launch(Dispatchers.Main) {
            showMessage("MQTT Error: ${error ?: "Unknown error"}")
        }
    }
    
    override fun onClientAlreadyConnected(deviceId: String) {
        Log.d(TAG, "onClientAlreadyConnected() called")
        lifecycleScope.launch(Dispatchers.Main) {
            val brokerHost = appSettings.getMqttBrokerHost()
            showStatus("mqtt://$brokerHost → $deviceId")
            showPulseAnimation()
            
            binding.connectButton.tag = "connected"
            binding.connectButton.text = "DISCONNECT"
        }
    }
    
    private fun showStatus(statusText: String) {
        binding.statusCard.visibility = View.VISIBLE
        binding.statusText.text = statusText
    }
    
    private fun hideStatus() {
        binding.statusCard.visibility = View.GONE
    }
    
    private fun showPulseAnimation() {
        binding.pulseAnimation.visibility = View.VISIBLE
    }
    
    private fun hidePulseAnimation() {
        binding.pulseAnimation.visibility = View.GONE
    }
    
    private fun updateUI(connected: Boolean) {
        if (connected) {
            binding.connectButton.tag = "connected"
            binding.connectButton.text = "DISCONNECT"
        } else {
            binding.connectButton.tag = "disconnected"
            binding.connectButton.text = "CONNECT"
        }
    }
    
    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause()")
        
        // To prevent memory leak
        mqttClientService?.setClientStateListener(null)
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    private fun showMessage(message: String) {
        view?.let {
            Snackbar.make(it, message, Snackbar.LENGTH_SHORT).apply {
                setAnchorView(R.id.bottom_nav_view)
            }.show()
        }
    }
}