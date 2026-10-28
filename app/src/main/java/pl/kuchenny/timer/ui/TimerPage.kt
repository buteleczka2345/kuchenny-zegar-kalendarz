package pl.kuchenny.timer.ui

import android.app.Activity
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import pl.kuchenny.timer.Settings
import pl.kuchenny.timer.core.TimeFormat
import pl.kuchenny.timer.core.TimerItem
import pl.kuchenny.timer.core.TimerLimits
import pl.kuchenny.timer.core.TimerState
import pl.kuchenny.timer.core.TimersSnapshot

/**
 * Główny ekran: jedna duża tarcza na całą wysokość, bez przewijania.
 *
 * Palec przesuwa wyłącznie tarczę, a w jej środku widać, ile czasu zostało
 * do zadzwonienia. Wszystkie ustawienia schowane są pod przyciskiem.
 */
class TimerPage(
    private val activity: Activity,
    private val settings: Settings,
    initialPresetMillis: Long,
    private val listener: Listener
) {

    interface Listener {
        fun onPrimaryAction()
        fun onCancelTimer()
        fun onResetTimer()
        fun onDurationSelected(minutes: Int)
        fun onQuickAdd(deltaMillis: Long)
        fun onSelectTimer(id: Long)
        fun onAddTimer()
        fun onRenameTimer()
        fun onOpenSettings()
        fun onOpenReminders()
        fun onRecordVoice()
    }

    val root: View

    private lateinit var dial: ClockDialView
    private lateinit var arc: TimerArcView
    private lateinit var timerStrip: LinearLayout
    private lateinit var timerName: TextView
    private lateinit var finishText: TextView
    private lateinit var nowText: TextView
    private lateinit var mainButton: Button
    private lateinit var secondaryButton: Button
    private lateinit var hoursChip: Button
    private lateinit var minutesChip: Button
    private lateinit var resetChip: Button
    private lateinit var voiceChip: Button

    private var stripSignature = ""
    private var voiceShown: Boolean? = null

    init {
        root = buildScreen(initialPresetMillis)
    }

    private fun buildScreen(initialPresetMillis: Long): View {
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Palette.background)
            setPadding(activity.dp(18), activity.dp(10), activity.dp(18), activity.dp(16))
        }

        val topBar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        topBar.addView(
            activity.label("Kuchenny zegar", 18f, Palette.text, true),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        topBar.addView(
            activity.settingsButton("Kalendarz", listener::onOpenReminders),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                activity.dp(40)
            ).apply { marginEnd = activity.dp(6) }
        )
        topBar.addView(
            activity.settingsButton("Ustawienia", listener::onOpenSettings),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                activity.dp(40)
            )
        )
        content.addView(topBar, activity.matchWidth(bottom = 8))

        content.addView(buildTimerStrip(), activity.matchWidth(bottom = 6))
        timerName = activity.label("Timer 1", 15f, Palette.mint, true).apply {
            gravity = Gravity.CENTER
            setOnClickListener { listener.onRenameTimer() }
        }
        content.addView(timerName, activity.matchWidth(bottom = 2))

        val dialFrame = FrameLayout(activity)
        dial = ClockDialView(activity).apply {
            val minutes = (initialPresetMillis / 60_000L).toInt()
            setTime(minutes / 60, minutes % 60)
            onTimePreview = { hours, minutes -> previewDuration(hours * 60 + minutes) }
            onTimeSettled = { hours, minutes -> listener.onDurationSelected(hours * 60 + minutes) }
        }
        arc = TimerArcView(activity)
        dialFrame.addView(
            dial,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        dialFrame.addView(
            arc,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        content.addView(
            dialFrame,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply {
                gravity = Gravity.CENTER
                topMargin = activity.dp(4)
                bottomMargin = activity.dp(6)
            }
        )

        finishText = activity.label("", 13f, Palette.muted).apply { gravity = Gravity.CENTER }
        content.addView(finishText, activity.matchWidth(bottom = 2))
        nowText = activity.label("", 12f, Palette.muted).apply { gravity = Gravity.CENTER }
        content.addView(nowText, activity.matchWidth(bottom = 8))

        content.addView(buildModeRow(), activity.matchWidth(bottom = 8))

        mainButton = activity.primaryButton("URUCHOM TIMER").apply {
            setOnClickListener { listener.onPrimaryAction() }
        }
        content.addView(mainButton, activity.matchWidth(bottom = 6))
        secondaryButton = activity.secondaryButton("WYŁĄCZ TIMER").apply {
            visibility = View.GONE
            setOnClickListener { listener.onCancelTimer() }
        }
        content.addView(secondaryButton, activity.matchWidth(bottom = 6))

        content.addView(buildQuickAdd(), activity.matchWidth(bottom = 2))
        content.addView(buildVolumeRow())
        return content
    }

    private fun buildTimerStrip(): View {
        val scroll = HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = false
        }
        timerStrip = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        scroll.addView(timerStrip)
        return scroll
    }

    private fun buildModeRow(): View {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        hoursChip = activity.choiceChip("Godziny", false).apply {
            setOnClickListener { setEditingMinutes(false) }
        }
        minutesChip = activity.choiceChip("Minuty", true).apply {
            setOnClickListener { setEditingMinutes(true) }
        }
        resetChip = activity.choiceChip("Zeruj", false).apply {
            setTextColor(Palette.mint)
            setOnClickListener { listener.onResetTimer() }
        }
        row.addView(hoursChip, LinearLayout.LayoutParams(0, activity.dp(34), 1f))
        row.addView(
            minutesChip,
            LinearLayout.LayoutParams(0, activity.dp(34), 1f).apply { marginStart = activity.dp(6) }
        )
        row.addView(
            resetChip,
            LinearLayout.LayoutParams(0, activity.dp(34), 1f).apply { marginStart = activity.dp(6) }
        )
        voiceChip = activity.choiceChip("🎤", false).apply {
            contentDescription = "Nagraj głos dla timera"
            setOnClickListener { listener.onRecordVoice() }
        }
        row.addView(
            voiceChip,
            LinearLayout.LayoutParams(activity.dp(52), activity.dp(34)).apply { marginStart = activity.dp(6) }
        )
        return row
    }

    private fun buildQuickAdd(): View {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val actions = listOf("+30 s" to 30_000L, "+1 min" to 60_000L, "+5 min" to 300_000L)
        actions.forEachIndexed { index, (label, millis) ->
            val button = activity.smallButton(label).apply {
                setOnClickListener { listener.onQuickAdd(millis) }
            }
            val lp = LinearLayout.LayoutParams(0, activity.dp(44), 1f)
            if (index > 0) lp.marginStart = activity.dp(6)
            row.addView(button, lp)
        }
        return row
    }

    private fun buildVolumeRow(): View {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(
            activity.label("Głośność", 12f, Palette.muted),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = activity.dp(10) }
        )
        val bar = SeekBar(activity).apply {
            max = 100
            progress = settings.alarmVolume
            contentDescription = "Głośność dzwonka"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                    if (fromUser) settings.alarmVolume = value
                }

                override fun onStartTrackingTouch(bar: SeekBar?) = Unit

                override fun onStopTrackingTouch(bar: SeekBar?) = Unit
            })
        }
        row.addView(bar, LinearLayout.LayoutParams(0, activity.dp(34), 1f))
        return row
    }

    private fun setEditingMinutes(editingMinutes: Boolean) {
        dial.setEditingMinutes(editingMinutes)
        activity.styleChip(hoursChip, !editingMinutes)
        activity.styleChip(minutesChip, editingMinutes)
    }

    // --- wyświetlanie stanu -------------------------------------------------

    fun renderTimers(snapshot: TimersSnapshot) {
        val signature = snapshot.timers.joinToString("|") { "${it.id}:${it.label}" } +
            "#" + snapshot.activeId
        if (signature == stripSignature) return
        stripSignature = signature

        timerStrip.removeAllViews()
        snapshot.timers.forEach { item ->
            val chip = activity.stripChip(item.label, item.id == snapshot.activeId)
            chip.setOnClickListener { listener.onSelectTimer(item.id) }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                activity.dp(36)
            )
            lp.marginEnd = activity.dp(6)
            timerStrip.addView(chip, lp)
        }
        val add = activity.stripChip("+ Nowy", false)
        add.setOnClickListener { listener.onAddTimer() }
        timerStrip.addView(
            add,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, activity.dp(36))
        )
        setText(timerName, snapshot.active.label)
    }

    /** Podświetlony mikrofon: aktywny timer zagra nagranym głosem. */
    fun renderVoice(recorded: Boolean) {
        if (voiceShown == recorded) return
        voiceShown = recorded
        activity.styleChip(voiceChip, recorded)
        voiceChip.text = if (recorded) "🎤✓" else "🎤"
    }

    fun renderTimer(item: TimerItem, nowMillis: Long) {
        val remaining = item.remainingMillis(nowMillis)
        // W trakcie przeciągania palcem to użytkownik dyktuje wartość.
        if (!dial.isUserInteracting) {
            dial.centerValue = TimeFormat.remaining(remaining)
            dial.centerCaption = when (item.state) {
                is TimerState.Ringing -> "CZAS MINĄŁ"
                is TimerState.Paused -> "PAUZA"
                is TimerState.Running -> "DO KOŃCA"
                TimerState.Idle -> idleCaption(item.presetMillis)
            }
            arc.progress = when (item.state) {
                TimerState.Idle, is TimerState.Ringing -> 1f
                else -> item.progress(nowMillis)
            }
        }

        if (!dial.isUserInteracting) {
            setText(
                finishText,
                when (item.state) {
                    is TimerState.Ringing -> "DZWONI"
                    else -> "GOTOWE O ${TimeFormat.finishTime(nowMillis, remaining)}"
                }
            )
        }
        setText(nowText, "TERAZ ${TimeFormat.clock(nowMillis)}")
        when (item.state) {
            is TimerState.Ringing -> {
                setText(mainButton, "ZATRZYMAJ DZWONEK")
                secondaryButton.visibility = View.GONE
            }
            is TimerState.Paused -> {
                setText(mainButton, "WZNÓW TIMER")
                secondaryButton.visibility = View.VISIBLE
            }
            is TimerState.Running -> {
                setText(mainButton, "WSTRZYMAJ")
                secondaryButton.visibility = View.VISIBLE
            }
            TimerState.Idle -> {
                setText(mainButton, "URUCHOM TIMER")
                secondaryButton.visibility = View.GONE
            }
        }
    }

    /** Ustawia tarczę czasu, o ile użytkownik właśnie jej nie przesuwa. */
    fun syncDurationDial(presetMillis: Long) {
        val minutes = (presetMillis / 60_000L).toInt()
        val hours = minutes / 60
        val rest = minutes % 60
        if (dial.hours != hours || dial.minutes != rest) {
            dial.setTime(hours, rest)
        }
    }

    // --- pomocnicze ---------------------------------------------------------

    private fun previewDuration(minutes: Int) {
        val safe = minutes.coerceIn(0, TimerLimits.MAX_MINUTES)
        val millis = safe * 60_000L
        dial.centerValue = TimeFormat.remaining(millis)
        dial.centerCaption = idleCaption(millis)
        // Godzina zadzwonienia liczy się od razu, razem z tarczą.
        setText(
            finishText,
            "GOTOWE O ${TimeFormat.finishTime(System.currentTimeMillis(), millis)}"
        )
    }

    private fun idleCaption(presetMillis: Long): String = when {
        presetMillis <= 0L -> "USTAW CZAS"
        presetMillis < 60_000L -> "SEKUNDY"
        presetMillis < 3_600_000L -> "MINUT"
        presetMillis % 3_600_000L == 0L -> "GODZINY"
        else -> "GODZ. I MIN."
    }

    private fun setText(view: TextView, value: String) {
        if (view.text.toString() != value) view.text = value
    }
}
