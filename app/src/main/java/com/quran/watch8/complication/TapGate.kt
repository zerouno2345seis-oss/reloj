package com.quran.watch8.complication

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import com.quran.watch8.MainActivity

/*
 * A watch face can only answer one tap, but zaher wants the figures 12, 3, 6
 * and 9 (and the digital hour and minute) to need two, so that a stray touch,
 * or the touch that wakes the screen, does not leave the face. The face's tap
 * opens this see-through window instead of the app: a second tap within
 * WINDOW_MS, on the window or on the face again, opens the route; otherwise
 * the window closes and the face is back as it was.
 */
class TapGateActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private val close = Runnable { finishQuietly() }
    private var route: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        arrive(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        arrive(intent)
    }

    /** One tap on the face. Two in a row, close together, open the route. */
    private fun arrive(intent: Intent) {
        val r = intent.getStringExtra(EXTRA_ROUTE) ?: return finishQuietly()
        val now = SystemClock.uptimeMillis()
        if (r == lastRoute && now - lastTap <= WINDOW_MS) {
            lastRoute = null
            open(r)
            return
        }
        lastRoute = r
        lastTap = now
        route = r
        handler.removeCallbacks(close)
        handler.postDelayed(close, WINDOW_MS)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            val r = route
            if (r != null && SystemClock.uptimeMillis() - lastTap <= WINDOW_MS) {
                lastRoute = null
                open(r)
            } else finishQuietly()
        }
        return true
    }

    private fun open(r: String) {
        handler.removeCallbacks(close)
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra("targetRoute", r)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finishQuietly()
    }

    private fun finishQuietly() {
        handler.removeCallbacks(close)
        route = null
        finish()
        overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        handler.removeCallbacks(close)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_ROUTE = "gateRoute"
        const val WINDOW_MS = 700L
        // Kept across launches: the second tap can reach the face again before
        // this window is up, which starts a second launch.
        private var lastRoute: String? = null
        private var lastTap = 0L
    }
}

/** Like [openRoute], but through [TapGateActivity], so it takes two taps. */
internal fun Context.openRouteOnDoubleTap(route: String): PendingIntent =
    PendingIntent.getActivity(
        this,
        ("gate:" + route).hashCode(),
        Intent(this, TapGateActivity::class.java)
            .putExtra(TapGateActivity.EXTRA_ROUTE, route)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
