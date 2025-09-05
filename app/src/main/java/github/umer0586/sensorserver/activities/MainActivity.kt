package github.umer0586.sensorserver.activities

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.navigation.NavigationBarView
import com.permissionx.guolindev.PermissionX
import github.umer0586.sensorserver.R
import github.umer0586.sensorserver.databinding.ActivityMainBinding
import github.umer0586.sensorserver.fragments.AvailableSensorsFragment
import github.umer0586.sensorserver.fragments.ClientFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.lang.Exception

class MainActivity : AppCompatActivity(), NavigationBarView.OnItemSelectedListener
{

    private lateinit var actionBarDrawerToggle: ActionBarDrawerToggle


    private lateinit var binding : ActivityMainBinding

    companion object
    {

        private val TAG: String = MainActivity::class.java.simpleName

        // Fragments Positions
        private const val POSITION_SERVER_FRAGMENT = 0
        private const val POSITION_AVAILABLE_SENSORS_FRAGMENT = 1
    }

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
       //toolBarBinding = ToolbarBinding.inflate(layoutInflater)

        setContentView(binding.root)

        // Set a Toolbar to replace the ActionBar.
        setSupportActionBar(binding.toolbar.root)


        binding.dashboard.bottomNavView.selectedItemId = R.id.navigation_client
        binding.dashboard.bottomNavView.setOnItemSelectedListener(this)






        binding.dashboard.viewPager.isUserInputEnabled = false
        binding.dashboard.viewPager.adapter = MyFragmentStateAdapter(this)


        actionBarDrawerToggle = ActionBarDrawerToggle(this, binding.drawerLayout, R.string.nav_open, R.string.nav_close)
        binding.drawerLayout.addDrawerListener(actionBarDrawerToggle)
        actionBarDrawerToggle.syncState()


        supportActionBar?.setDisplayHomeAsUpEnabled(true)


        binding.drawerNavigationView.setNavigationItemSelectedListener { menuItem ->

            if (menuItem.itemId == R.id.nav_drawer_about)
                startActivity(Intent(this, AboutActivity::class.java))

            if (menuItem.itemId == R.id.nav_drawer_settings)
                startActivity( Intent(this,SettingsActivity::class.java)  )

            if (menuItem.itemId == R.id.nav_drawer_device_axis)
                startActivity( Intent(this, DeviceAxisActivity::class.java ) )

            if (menuItem.itemId == R.id.nav_drawer_touch_sensors)
                startActivity( Intent(this, TouchScreenActivity::class.java) )


            false
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean
    {
        Log.d(TAG, "onOptionsItemSelected: $item")
        return if (actionBarDrawerToggle.onOptionsItemSelected(item))
        {
            true
        }
        else super.onOptionsItemSelected(item)
    }





    override fun onPause()
    {
        super.onPause()
        Log.d(TAG, "onPause()")

    }



    override fun onNavigationItemSelected(item: MenuItem): Boolean
    {
        when (item.itemId)
        {
            R.id.navigation_available_sensors ->
            {
                binding.dashboard.viewPager.setCurrentItem(POSITION_AVAILABLE_SENSORS_FRAGMENT, false)
                supportActionBar?.title = "Available Sensors"
                return true
            }

            R.id.navigation_client ->
            {
                binding.dashboard.viewPager.setCurrentItem(POSITION_SERVER_FRAGMENT, false)
                supportActionBar?.title = "Sensor Server"
                return true
            }
        }
        return false
    }

    private inner class MyFragmentStateAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity)
    {

        override fun createFragment(pos: Int): Fragment
        {
            when (pos)
            {
                POSITION_SERVER_FRAGMENT -> return ClientFragment()
                POSITION_AVAILABLE_SENSORS_FRAGMENT -> return AvailableSensorsFragment()
            }
            return ClientFragment()
        }

        override fun getItemCount(): Int
        {
            return 2
        }
    }


}