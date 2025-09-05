package github.umer0586.sensorserver.activities

import android.annotation.SuppressLint
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.MotionEvent
import github.umer0586.sensorserver.R

class TouchScreenActivity : AppCompatActivity()
{
    private val TAG = "TouchScreenActivity"

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_touch_screen)

        // Touch screen WebSocket functionality removed in MQTT migration

    }

    override fun onTouchEvent(event: MotionEvent?): Boolean
    {
        // Touch event handling disabled - was used for WebSocket server
        // Could be adapted for MQTT publishing if needed
        return super.onTouchEvent(event)
    }


}