package pl.kuchenny.budzik.alarm

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import pl.kuchenny.budzik.core.TimeText
import pl.kuchenny.budzik.ui.Palette
import pl.kuchenny.budzik.ui.dp
import pl.kuchenny.budzik.ui.label
import java.util.Calendar

/**
 * Pełnoekranowy ekran dzwoniącego budzika.
 *
 * Zapala ekran, pokazuje się nad blokadą i ma jeden duży czerwony przycisk STOP.
 * Gdy budzik ucichnie inaczej (potrząśnięcie, powiadomienie), ekran sam się zamyka.
 */
class AlarmStopActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())

    private val watchTask = object : Runnable {
        override fun run() {
            if (!AlarmService.isRinging) {
                finish()
                return
            }
            handler.postDelayed(this, WATCH_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()

        val now = Calendar.getInstance()
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Budzik"

        val stopButton = Button(this).apply {
            text = "STOP"
            textSize = 56f
            isAllCaps = true
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(STOP_RED)
                setStroke(dp(6), Color.rgb(255, 120, 110))
            }
            stateListAnimator = null
            setOnClickListener { stopAlarm() }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Palette.background)
            setPadding(dp(24), dp(48), dp(24), dp(48))
            addView(
                label(
                    TimeText.clock(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE)),
                    64f,
                    Palette.text,
                    bold = true
                ).apply { gravity = Gravity.CENTER }
            )
            addView(
                label(title, 22f, Palette.muted).apply { gravity = Gravity.CENTER }
            )
            addView(
                LinearLayout(this@AlarmStopActivity).apply {
                    gravity = Gravity.CENTER
                    val size = dp(260)
                    addView(stopButton, LinearLayout.LayoutParams(size, size))
                },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            )
            addView(
                label("Dotknij, aby wyłączyć budzik", 16f, Palette.muted).apply {
                    gravity = Gravity.CENTER
                }
            )
        }
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        handler.post(watchTask)
    }

    override fun onPause() {
        handler.removeCallbacks(watchTask)
        super.onPause()
    }

    @Deprecated("Wstecz nie chowa budzika — trzeba nacisnąć STOP.")
    override fun onBackPressed() {
        // Celowo nic: budzik wyłącza tylko przycisk STOP.
    }

    private fun stopAlarm() {
        AlarmService.stop(this)
        finish()
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    companion object {
        private const val EXTRA_TITLE = "pl.kuchenny.budzik.extra.STOP_TITLE"
        private const val WATCH_INTERVAL_MS = 500L
        private val STOP_RED = Color.rgb(211, 47, 47)

        fun intent(context: Context, title: String): Intent =
            Intent(context, AlarmStopActivity::class.java)
                .putExtra(EXTRA_TITLE, title)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_USER_ACTION or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
    }
}
