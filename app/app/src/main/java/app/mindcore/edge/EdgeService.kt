package app.mindcore.edge

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView
import app.mindcore.ShareActivity
import app.mindcore.settings.AppSettings
import app.mindcore.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * mind-core's edge handle and "Save to mind-core?" pill (you turned this on in Accessibility settings).
 *
 * What it can and can't see, by design:
 * - It only receives window-change and toast/notification events, including their short text such as
 *   "Link copied" (see res/xml/edge_service.xml). canRetrieveWindowContent is false, so it can't read what's
 *   on screen, and it ignores everything except "copied" messages.
 * - It never reads the clipboard: Android blocks that for background services anyway. It only notices that
 *   something was copied; tapping the pill opens the save sheet, which reads the clipboard in the foreground.
 * - It sends nothing anywhere and keeps no history. Saving only happens when you press Save.
 */
class EdgeService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val main = Handler(Looper.getMainLooper())
    private lateinit var wm: WindowManager
    private var handle: View? = null
    private var pill: View? = null
    private var settings = AppSettings()
    private var lastPillAt = 0L

    override fun onServiceConnected() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        scope.launch {
            SettingsStore(applicationContext).settings.collect { s ->
                settings = s
                showHandle()
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        handle?.let { runCatching { wm.removeView(it) } }
        hidePill()
        super.onDestroy()
    }

    override fun onInterrupt() = Unit

    // ---------- noticing a copy ----------

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!settings.copyPopup || event.packageName == packageName) return
        val pkg = event.packageName?.toString().orEmpty()
        val cls = event.className?.toString().orEmpty()
        val text = event.text.joinToString(" ").lowercase()
        val copied =
            // Android 13+ shows its own clipboard preview whenever anything is copied.
            (pkg == "com.android.systemui" && (cls.contains("clipboard", true) || text.contains("copied"))) ||
                // Apps that confirm with a toast ("Link copied", "Copied to clipboard").
                (event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED && text.contains("copied"))
        if (copied) showPill(fromInstagram = pkg == "com.instagram.android")
    }

    private fun showPill(fromInstagram: Boolean) {
        val now = System.currentTimeMillis()
        if (now - lastPillAt < 3000) return // one pill per copy, even when several events fire
        lastPillAt = now
        hidePill()
        val view = TextView(this).apply {
            text = if (fromInstagram) "Save this to mind-core?" else "Save to mind-core?"
            setTextColor(0xFF4A1426.toInt())
            textSize = 15f
            setPadding(dp(20), dp(12), dp(20), dp(12))
            background = GradientDrawable().apply { cornerRadius = dp(24).toFloat(); setColor(0xFFF5A9BE.toInt()) }
            elevation = dp(6).toFloat()
            setOnClickListener {
                hidePill()
                openCapture(ShareActivity.MODE_PASTE)
            }
        }
        val lp = overlayParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(150)
        }
        runCatching { wm.addView(view, lp) }.onSuccess { pill = view }
        main.postDelayed({ if (pill === view) hidePill() }, 6000)
    }

    private fun hidePill() {
        pill?.let { runCatching { wm.removeView(it) } }
        pill = null
    }

    // ---------- edge handle ----------

    @SuppressLint("ClickableViewAccessibility")
    private fun showHandle() {
        handle?.let { runCatching { wm.removeView(it) } }
        val right = settings.edgeRight
        val view = View(this).apply {
            contentDescription = "Open mind-core drawer"
            // A slim 5 dp lotus strip inside a 24 dp touch area: easy to grab, barely visible.
            background = InsetDrawable(
                GradientDrawable().apply { cornerRadius = dp(3).toFloat(); setColor(0x99F5A9BE.toInt()) },
                if (right) dp(17) else dp(2), dp(6), if (right) dp(2) else dp(17), dp(6),
            )
        }
        var downX = 0f
        var downY = 0f
        view.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY }
                MotionEvent.ACTION_UP -> {
                    val dx = e.rawX - downX
                    val inward = if (right) -dx else dx
                    val tap = abs(dx) < dp(8) && abs(e.rawY - downY) < dp(8)
                    if (inward > dp(20) || tap) openDrawer()
                }
            }
            true
        }
        val lp = overlayParams(dp(24), dp(120)).apply {
            gravity = Gravity.TOP or if (right) Gravity.END else Gravity.START
            y = (resources.displayMetrics.heightPixels * settings.edgePosition).toInt()
        }
        runCatching { wm.addView(view, lp) }.onSuccess { handle = view }
    }

    private fun openDrawer() {
        startActivity(
            Intent(this, EdgeDrawerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EdgeDrawerActivity.EXTRA_RIGHT, settings.edgeRight),
        )
    }

    private fun openCapture(mode: String) {
        startActivity(
            Intent(this, ShareActivity::class.java).setAction(ShareActivity.ACTION_CAPTURE)
                .putExtra(ShareActivity.EXTRA_MODE, mode).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    // Accessibility overlays don't need the separate "display over other apps" permission.
    private fun overlayParams(w: Int, h: Int) = WindowManager.LayoutParams(
        w, h, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    )

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
