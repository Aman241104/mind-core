package app.mindcore.edge

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Typeface
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import app.mindcore.R
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
        hidePill(animated = false)
        super.onDestroy()
    }

    override fun onInterrupt() = Unit

    // Rotation changes the screen size: re-place the handle so it stays on the edge and on screen.
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::wm.isInitialized) showHandle()
    }

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
        hidePill(animated = false)
        // Dark glass capsule: translucent tint, a light rim, Material "bookmark add" icon, two lines of text.
        val view = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(20), dp(8))
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xE62A2327.toInt(), 0xF0161214.toInt())).apply {
                cornerRadius = dp(30).toFloat()
                setStroke(dp(1), 0x40FFFFFF)
            }
            elevation = dp(10).toFloat()
            addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_sym_bookmark_add)
                setColorFilter(0xFF4A1426.toInt())
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFFF5A9BE.toInt()) }
                setPadding(dp(9), dp(9), dp(9), dp(9))
            }, LinearLayout.LayoutParams(dp(42), dp(42)))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), 0, 0, 0)
                addView(TextView(context).apply {
                    text = "Save to mind-core"
                    setTextColor(0xFFFFFFFF.toInt())
                    textSize = 15f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                })
                addView(TextView(context).apply {
                    text = if (fromInstagram) "The Instagram link you copied" else "What you just copied"
                    setTextColor(0xB3FFFFFF.toInt())
                    textSize = 12f
                })
            })
            setOnClickListener {
                hidePill()
                openCapture(ShareActivity.MODE_PASTE)
            }
        }
        val lp = overlayParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(150)
        }
        runCatching { wm.addView(view, lp) }.onSuccess {
            pill = view
            // Springs up into place.
            view.alpha = 0f
            view.scaleX = 0.86f
            view.scaleY = 0.86f
            view.translationY = dp(28).toFloat()
            view.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
                .setDuration(420).setInterpolator(OvershootInterpolator(1.4f)).start()
        }
        main.postDelayed({ if (pill === view) hidePill() }, 6000)
    }

    private fun hidePill(animated: Boolean = true) {
        val view = pill ?: return
        pill = null
        if (!animated) { runCatching { wm.removeView(view) }; return }
        view.animate().alpha(0f).scaleX(0.92f).scaleY(0.92f).translationY(dp(16).toFloat()).setDuration(200)
            .setInterpolator(AccelerateInterpolator()).withEndAction { runCatching { wm.removeView(view) } }.start()
    }

    // ---------- edge handle ----------

    @SuppressLint("ClickableViewAccessibility")
    private fun showHandle() {
        handle?.let { runCatching { wm.removeView(it) } }
        val right = settings.edgeRight
        // A soft glass strip inside a 26 dp touch area: easy to grab, barely there until you touch it.
        val strip = View(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0x66FFFFFF, 0x99F5A9BE.toInt(), 0x66FFFFFF)).apply { cornerRadius = dp(3).toFloat() }
            alpha = 0.7f
        }
        val view = FrameLayout(this).apply {
            contentDescription = "Open mind-core drawer"
            addView(strip, FrameLayout.LayoutParams(dp(5), FrameLayout.LayoutParams.MATCH_PARENT).apply {
                gravity = if (right) Gravity.END else Gravity.START
                if (right) marginEnd = dp(3) else marginStart = dp(3)
                topMargin = dp(8); bottomMargin = dp(8)
            })
        }
        var downX = 0f
        var downY = 0f
        view.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    strip.animate().alpha(1f).scaleX(1.8f).setDuration(160).setInterpolator(OvershootInterpolator()).start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    strip.animate().alpha(0.7f).scaleX(1f).setDuration(220).start()
                    if (e.action == MotionEvent.ACTION_UP) {
                        val dx = e.rawX - downX
                        val inward = if (right) -dx else dx
                        val tap = abs(dx) < dp(8) && abs(e.rawY - downY) < dp(8)
                        if (inward > dp(20) || tap) openDrawer()
                    }
                }
            }
            true
        }
        // Current window size (not the portrait one), and never let the handle hang off the bottom.
        val screenH = wm.currentWindowMetrics.bounds.height()
        val handleH = dp(if (screenH < dp(500)) 96 else 128) // shorter in landscape
        val lp = overlayParams(dp(26), handleH).apply {
            gravity = Gravity.TOP or if (right) Gravity.END else Gravity.START
            y = (screenH * settings.edgePosition).toInt().coerceIn(dp(24), (screenH - handleH - dp(24)).coerceAtLeast(dp(24)))
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
