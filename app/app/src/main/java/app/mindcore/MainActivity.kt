package app.mindcore

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import app.mindcore.settings.DEFAULT_SERVER
import app.mindcore.settings.SettingsStore
import app.mindcore.ui.MindCoreApp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handlePairing(intent)
        setContent { MindCoreApp() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handlePairing(intent)
    }

    /** mindcore://pair?url=...&token=... — sent from the laptop over adb so the key is never typed. */
    private fun handlePairing(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != "mindcore" || data.host != "pair") return
        val url = data.getQueryParameter("url")
        val token = data.getQueryParameter("token") ?: return
        // Any app can fire this link, so it may only pair with your own server, never a different one.
        if (url != null && Uri.parse(url).host != Uri.parse(DEFAULT_SERVER).host) {
            Toast.makeText(this, "Ignored a pairing link for another server", Toast.LENGTH_LONG).show()
            return
        }
        lifecycleScope.launch {
            SettingsStore(applicationContext).update { it.copy(serverUrl = url ?: it.serverUrl, apiToken = token) }
            Toast.makeText(this@MainActivity, "Paired with your mind-core server", Toast.LENGTH_SHORT).show()
        }
    }
}
