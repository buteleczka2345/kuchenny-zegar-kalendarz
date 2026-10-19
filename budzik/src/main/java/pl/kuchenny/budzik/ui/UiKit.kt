package pl.kuchenny.budzik.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Kolorystyka budzika — spójna z aplikacją timera. */
object Palette {
    val background: Int = Color.rgb(16, 28, 28)
    val panel: Int = Color.rgb(27, 43, 42)
    val track: Int = Color.rgb(44, 65, 62)
    val mint: Int = Color.rgb(124, 224, 181)
    val text: Int = Color.rgb(241, 246, 241)
    val muted: Int = Color.rgb(163, 183, 175)
}

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

fun Context.rounded(color: Int, radiusDp: Int): GradientDrawable = GradientDrawable().apply {
    setColor(color)
    cornerRadius = dp(radiusDp).toFloat()
}

fun Context.label(value: String, size: Float, color: Int, bold: Boolean = false): TextView =
    TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

fun Context.primaryButton(label: String): Button = Button(this).apply {
    text = label
    textSize = 15f
    isAllCaps = true
    setTextColor(Palette.background)
    background = rounded(Palette.mint, 18)
    minHeight = dp(56)
}

fun Context.smallButton(label: String): Button = Button(this).apply {
    text = label
    textSize = 13f
    isAllCaps = false
    setTextColor(Palette.mint)
    background = rounded(Palette.track, 14)
    minHeight = dp(44)
    gravity = Gravity.CENTER
}

fun Context.settingsButton(label: String, action: () -> Unit): Button = Button(this).apply {
    text = label
    textSize = 12f
    isAllCaps = false
    gravity = Gravity.CENTER_VERTICAL or Gravity.START
    setTextColor(Palette.text)
    background = rounded(Palette.track, 13)
    minHeight = dp(44)
    setPadding(dp(12), 0, dp(12), 0)
    setOnClickListener { action() }
}

fun Context.choiceChip(label: String, selected: Boolean): Button = Button(this).apply {
    text = label
    textSize = 12f
    isAllCaps = false
    minWidth = 0
    minimumWidth = 0
    minHeight = 0
    minimumHeight = 0
    setPadding(dp(3), 0, dp(3), 0)
    styleChip(this, selected)
}

fun Context.styleChip(button: Button, selected: Boolean) {
    button.background = rounded(if (selected) Palette.mint else Palette.track, 14)
    button.setTextColor(if (selected) Palette.background else Palette.text)
}

fun Context.matchWidth(top: Int = 0, bottom: Int = 0): LinearLayout.LayoutParams =
    LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply {
        topMargin = dp(top)
        bottomMargin = dp(bottom)
    }
