package app.parkingsms

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import kotlinx.coroutines.launch
import app.parkingsms.ui.AppRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AppRoot() }
    }

    override fun onResume() {
        super.onResume()
        // Re-create alarms / notification for an active session (e.g. exact-alarm permission just granted).
        val c = container
        c.appScope.launch { c.sessionManager.rearm() }
    }
}
