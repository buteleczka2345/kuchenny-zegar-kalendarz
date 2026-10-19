package pl.kuchenny.timer.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Kolorystyka aplikacji w jednym miejscu. */
internal object Palette {
    val background: Int = Color.rgb(16, 28, 28)
    val panel: Int = Color.rgb(27, 43, 42)
    val track: Int = Color.rgb(44, 65, 62)
    val mint: Int = Color.rgb(124, 224, 181)
    val text: Int = Color.rgb(241, 246, 241)
    val muted: Int = Color.rgb(163, 183, 175)
}

internal fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

internal fun Context.rounded(color: Int, radiusDp: Int): GradientDrawable =
    GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

internal fun Context.label(
    value: String,
    size: Float,
    color: Int,
    bold: Boolean = false
): TextView = TextView(this).apply {
    text = value
    textSize = size
    setTextColor(color)
    if (bold) setTypeface(typeface, Typeface.BOLD)
}

internal fun Context.primaryButton(label: String): Button = Button(this).apply {
    text = label
    textSize = 15f
    isAllCaps = true
    setTextColor(Palette.background)
    background = rounded(Palette.mint, 18)
    minHeight = dp(58)
    setPadding(dp(12), dp(8), dp(12), dp(8))
}

internal fun Context.secondaryButton(label: String): Button = Button(this).apply {
    text = label
    textSize = 13f
    isAllCaps = true
    setTextColor(Palette.text)
    background = rounded(Palette.track, 16)
    minHeight = dp(48)
}

internal fun Context.smallButton(label: String): Button = Button(this).apply {
    text = label
    textSize = 13f
    isAllCaps = false
    setTextColor(Palette.mint)
    background = rounded(Palette.track, 14)
    minHeight = dp(44)
    gravity = Gravity.CENTER
}

internal fun Context.settingsButton(label: String, action: () -> Unit): Button = Button(this).apply {
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

internal fun Context.choiceChip(label: String, selected: Boolean): Button = Button(this).apply {
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

/** Większy kafel z nazwą timera — pasek u góry ekranu. */
internal fun Context.stripChip(label: String, selected: Boolean): Button = Button(this).apply {
    text = label
    textSize = 12f
    isAllCaps = false
    minWidth = 0
    minimumWidth = 0
    minHeight = 0
    minimumHeight = 0
    maxLines = 1
    ellipsize = android.text.TextUtils.TruncateAt.END
    maxWidth = dp(170)
    setPadding(dp(14), 0, dp(14), 0)
    styleChip(this, selected)
}

internal fun Context.styleChip(button: Button, selected: Boolean) {
    button.background = rounded(if (selected) Palette.mint else Palette.track, 14)
    button.setTextColor(if (selected) Palette.background else Palette.text)
}

/** Parametry wiersza rozciągniętego na całą szerokość, z odstępami w dp. */
internal fun Context.matchWidth(top: Int = 0, bottom: Int = 0): LinearLayout.LayoutParams =
    LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply {
        topMargin = dp(top)
        bottomMargin = dp(bottom)
    }
