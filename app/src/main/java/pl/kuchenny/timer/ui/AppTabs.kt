package pl.kuchenny.timer.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import pl.kuchenny.timer.MainActivity as TimerActivity
import pl.kuchenny.budzik.MainActivity as AlarmActivity

/**
 * Dolny pasek z dwiema zakładkami: kuchenny zegar i budzik.
 *
 * Każda zakładka to osobna aktywność. Przełączenie wysuwa już otwartą aktywność na wierzch
 * (bez animacji), więc oba ekrany zachowują swój stan.
 */
internal object AppTabs {

    enum class Tab(val title: String) {
        TIMER("Zegar kuchenny"),
        ALARM("Budzik")
    }

    /** Zwraca [content] z paskiem zakładek pod spodem. */
    fun wrap(activity: Activity, content: View, selected: Tab): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Palette.background)
            addView(content, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(
                bar(activity, selected),
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, activity.dp(56))
            )
        }

    private fun bar(activity: Activity, selected: Tab): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Palette.panel)
            setPadding(activity.dp(8), activity.dp(6), activity.dp(8), activity.dp(6))
            Tab.values().forEach { tab ->
                addView(
                    tabButton(activity, tab, tab == selected),
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                        marginStart = activity.dp(4)
                        marginEnd = activity.dp(4)
                    }
                )
            }
        }

    private fun tabButton(activity: Activity, tab: Tab, isSelected: Boolean): View =
        TextView(activity).apply {
            text = tab.title
            textSize = 15f
            gravity = Gravity.CENTER
            setTypeface(typeface, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
            setTextColor(if (isSelected) Palette.background else Palette.muted)
            background = activity.rounded(if (isSelected) Palette.mint else Palette.panel, 22)
            contentDescription = tab.title
            this.isSelected = isSelected
            if (!isSelected) {
                setOnClickListener { open(activity, tab) }
            }
        }

    private fun open(activity: Activity, tab: Tab) {
        val target = when (tab) {
            Tab.TIMER -> TimerActivity::class.java
            Tab.ALARM -> AlarmActivity::class.java
        }
        activity.startActivity(Intent(activity, target).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        @Suppress("DEPRECATION")
        activity.overridePendingTransition(0, 0)
    }
}
