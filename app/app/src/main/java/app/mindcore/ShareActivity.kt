package app.mindcore

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import app.mindcore.data.Api
import app.mindcore.data.Capture
import app.mindcore.data.Capturer
import app.mindcore.settings.AppSettings
import app.mindcore.settings.SettingsStore
import app.mindcore.ui.CaptureContent
import app.mindcore.ui.theme.colorSchemeFor
import app.mindcore.widget.WidgetData
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/** "Share → mind-core" from any app: a sheet slides up over that app, saves, and goes away. */
class ShareActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Opened from the tile, widget or app-icon shortcut: an empty sheet with the text box and pickers.
        val quick = intent.action == ACTION_CAPTURE
        val mode = intent.getStringExtra(EXTRA_MODE)
        val capture = if (quick) Capture() else captureFrom(intent)
        setContent {
            val store = remember { SettingsStore(applicationContext) }
            val settings by store.settings.collectAsState(initial = AppSettings())
            val capturer = remember(settings.apiToken, settings.serverUrl) {
                if (settings.apiToken.isBlank()) null else Capturer(applicationContext, Api(settings.serverUrl, settings.apiToken))
            }
            MaterialTheme(colorScheme = colorSchemeFor(settings)) {
                ModalBottomSheet(onDismissRequest = { finish() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
                    CaptureContent(
                        initial = capture, capturer = capturer, editable = quick,
                        autoVoice = mode == MODE_VOICE, autoPaste = mode == MODE_PASTE,
                        onDone = { lifecycleScope.launch { WidgetData.sync(applicationContext) }; finish() },
                    )
                }
            }
        }
    }

    companion object {
        const val ACTION_CAPTURE = "app.mindcore.CAPTURE"
        const val EXTRA_MODE = "app.mindcore.MODE"
        const val MODE_VOICE = "voice" // start recording right away (the widget's 🎙)
        const val MODE_PASTE = "paste" // paste what you copied (the widget's 📋)
    }

    private fun captureFrom(intent: Intent): Capture {
        val type = intent.type ?: ""
        val streams: List<Uri> = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(intent.parcelable(Intent.EXTRA_STREAM))
            Intent.ACTION_SEND_MULTIPLE -> intent.parcelableList(Intent.EXTRA_STREAM)
            else -> emptyList()
        }
        // WhatsApp "Export chat": a .zip (or .txt + media). Hand the whole thing to the chat importer.
        val chat = streams.firstOrNull { uri ->
            val t = contentResolver.getType(uri) ?: ""
            (t.contains("zip") || t == "text/plain" || uri.lastPathSegment?.endsWith(".zip", true) == true) && t != "application/pdf"
        }
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT) ?: ""
        if (chat != null && (subject.contains("WhatsApp", true) || type.contains("zip") || streams.size > 1)) {
            return Capture(chatExport = chat)
        }
        val text = Capture.fromText(intent.getStringExtra(Intent.EXTRA_TEXT))
        val images = streams.filter { (contentResolver.getType(it) ?: type).startsWith("image/") }
        val pdfs = streams.filter { (contentResolver.getType(it) ?: type) == "application/pdf" }
        return text.copy(images = images, pdfs = pdfs)
    }

    @Suppress("DEPRECATION")
    private fun Intent.parcelable(key: String): Uri? =
        if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(key, Uri::class.java) else getParcelableExtra(key)

    @Suppress("DEPRECATION")
    private fun Intent.parcelableList(key: String): List<Uri> =
        (if (Build.VERSION.SDK_INT >= 33) getParcelableArrayListExtra(key, Uri::class.java) else getParcelableArrayListExtra(key))
            ?: emptyList()
}
