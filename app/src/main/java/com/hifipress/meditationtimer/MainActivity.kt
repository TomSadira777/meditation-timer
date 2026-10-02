package com.hifipress.meditationtimer

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Hosts the timer UI in a WebView and relays session boundaries to
 * TimerService, which owns the audible bell.
 *
 * Division of labour:
 *   - This WebView draws the countdown and the interface.
 *   - TimerService + AlarmManager guarantee the bell, even if this
 *     Activity is backgrounded, frozen, or the screen is off.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            webViewClient = WebViewClient()
            setBackgroundColor(0xFF0B0B0C.toInt())
            addJavascriptInterface(Bridge(), "Android")
        }
        setContentView(web)
        web.loadUrl("file:///android_asset/index.html")

        requestNotificationPermissionIfNeeded()
        hideSystemBars()
    }

    /** Immersive mode — replaces the web Fullscreen API, which is a no-op here. */
    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1
            )
        }
    }

    /**
     * Called from JS. Timestamps arrive as Strings because the JS bridge
     * marshals numbers as 32-bit — epoch milliseconds would overflow.
     */
    inner class Bridge {
        @JavascriptInterface
        fun startSession(endAtMs: String, intervalMs: String) =
            send(TimerService.ACTION_START, endAtMs, intervalMs)

        @JavascriptInterface
        fun resumeSession(endAtMs: String, intervalMs: String) =
            send(TimerService.ACTION_START, endAtMs, intervalMs)

        @JavascriptInterface
        fun pauseSession() = send(TimerService.ACTION_PAUSE)

        @JavascriptInterface
        fun endSession() = send(TimerService.ACTION_STOP)

        private fun send(action: String, endAt: String? = null, interval: String? = null) {
            val i = Intent(this@MainActivity, TimerService::class.java).setAction(action)
            endAt?.let { i.putExtra(TimerService.EXTRA_END_AT, it.toLong()) }
            interval?.let { i.putExtra(TimerService.EXTRA_INTERVAL, it.toLong()) }
            ContextCompat.startForegroundService(this@MainActivity, i)
        }
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }
}
