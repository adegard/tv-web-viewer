package com.example.adblock.sample

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.example.adblock.AdBlockWebView

@SuppressLint("ClickableViewAccessibility")
class MainActivity : AppCompatActivity() {

    private lateinit var webView: AdBlockWebView
    private lateinit var cursor: ImageView

    private val prefs by lazy { getSharedPreferences("adblock_browser", Context.MODE_PRIVATE) }

    private val uiHandler = Handler(Looper.getMainLooper())

    private var cursorActive = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        cursor = findViewById(R.id.cursor)

        webView.onAdBlocked = { _ -> }

        webView.onPageFinishedCallback = { url ->
            if (url.startsWith("http")) {
                prefs.edit().putString(KEY_LAST_URL, url).apply()
            }
        }

        val startUrl = resolveStartUrl()
        webView.loadUrlWithAdBlock(startUrl)
        webView.requestFocus()
        onRemoteActivity()
    }

    private fun resolveStartUrl(): String {
        val startup = prefs.getString(KEY_STARTUP_URL, null)
            ?.takeIf { it.startsWith("http") }
        if (startup != null) return startup
        val last = prefs.getString(KEY_LAST_URL, null)
            ?.takeIf { it.startsWith("http") }
        return last ?: DEFAULT_START_URL
    }

    private fun normalizeUrl(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        return when {
            trimmed.startsWith("https://") || trimmed.startsWith("http://") -> trimmed
            else -> "https://$trimmed"
        }
    }

    private fun openStartupDialog() {
        val input = EditText(this)
        input.hint = "https://example.com"
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        input.setText(prefs.getString(KEY_STARTUP_URL, null) ?: webView.url ?: "")
        input.selectAll()
        AlertDialog.Builder(this)
            .setTitle("Startup page")
            .setMessage("Set the page loaded when the app opens.")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val url = normalizeUrl(input.text.toString())
                if (url != null) {
                    prefs.edit().putString(KEY_STARTUP_URL, url).apply()
                    webView.loadUrlWithAdBlock(url)
                    webView.requestFocus()
                    onRemoteActivity()
                }
            }
            .setNegativeButton("Cancel") { _, _ ->
                webView.requestFocus()
            }
            .setOnCancelListener { webView.requestFocus() }
            .show()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && webView.width > 0) {
            cursor.x = webView.left + webView.width / 2f - cursor.width / 2f
            cursor.y = webView.top + webView.height / 2f - cursor.height / 2f
        }
    }

    private val hideCursorTask = Runnable {
        cursor.visibility = View.GONE
    }

    private fun onRemoteActivity() {
        if (!cursorActive) return
        cursor.visibility = View.VISIBLE
        uiHandler.removeCallbacks(hideCursorTask)
        uiHandler.postDelayed(hideCursorTask, HIDE_CURSOR_AFTER_MS)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) onRemoteActivity()
        if (event.action == KeyEvent.ACTION_DOWN && webView.hasFocus()) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_MENU,
                KeyEvent.KEYCODE_BUTTON_Y,
                KeyEvent.KEYCODE_PROG_RED -> {
                    toggleCursor()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> if (cursorActive) return moveCursor(-STEP_X, 0f)
                KeyEvent.KEYCODE_DPAD_RIGHT -> if (cursorActive) return moveCursor(STEP_X, 0f)
                KeyEvent.KEYCODE_DPAD_UP -> if (cursorActive) return moveCursor(0f, -STEP_Y)
                KeyEvent.KEYCODE_DPAD_DOWN -> if (cursorActive) return moveCursor(0f, STEP_Y)
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                    webView.evaluateJavascript("window.scrollBy(0, $SCROLL);", null)
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_REWIND -> {
                    webView.evaluateJavascript("window.scrollBy(0, -$SCROLL);", null)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> if (cursorActive) {
                    clickAtCursor()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun toggleCursor() {
        cursorActive = !cursorActive
        if (cursorActive) {
            onRemoteActivity()
        } else {
            uiHandler.removeCallbacks(hideCursorTask)
            cursor.visibility = View.GONE
        }
    }

    private fun moveCursor(dx: Float, dy: Float): Boolean {
        val minX = webView.left.toFloat()
        val maxX = webView.right - cursor.width.toFloat()
        val minY = webView.top.toFloat()
        val maxY = webView.bottom - cursor.height.toFloat()
        var nx = cursor.x + dx
        var ny = cursor.y + dy
        if (ny < minY) {
            webView.evaluateJavascript("window.scrollBy(0, -$SCROLL);", null)
            ny = minY
            nx = cursor.x
        } else if (ny > maxY) {
            webView.evaluateJavascript("window.scrollBy(0, $SCROLL);", null)
            ny = maxY
            nx = cursor.x
        }
        cursor.x = nx.coerceIn(minX, maxX)
        cursor.y = ny.coerceIn(minY, maxY)
        return true
    }

    private fun clickAtCursor() {
        val webLoc = IntArray(2)
        val curLoc = IntArray(2)
        webView.getLocationOnScreen(webLoc)
        cursor.getLocationOnScreen(curLoc)
        val x = curLoc[0] - webLoc[0] + cursor.width / 2f
        val y = curLoc[1] - webLoc[1] + cursor.height / 2f

        if (y < TOP_HIT_ZONE && x > webView.width - RIGHT_HIT_ZONE) {
            openStartupDialog()
            return
        }

        val now = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(now, now + 40, MotionEvent.ACTION_UP, x, y, 0)
        down.source = InputDevice.SOURCE_TOUCHSCREEN
        up.source = InputDevice.SOURCE_TOUCHSCREEN
        webView.dispatchTouchEvent(down)
        webView.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    companion object {
        private const val KEY_STARTUP_URL = "startup_url"
        private const val KEY_LAST_URL = "last_url"
        private const val DEFAULT_START_URL = "https://www.google.com"
        private const val STEP_X = 28f
        private const val STEP_Y = 64f
        private const val SCROLL = 260
        private const val HIDE_CURSOR_AFTER_MS = 5000L
        private const val TOP_HIT_ZONE = 140F
        private const val RIGHT_HIT_ZONE = 260
    }
}