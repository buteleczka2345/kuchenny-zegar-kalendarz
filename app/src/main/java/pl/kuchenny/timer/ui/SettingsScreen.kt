package pl.kuchenny.timer.ui

import android.app.Activity
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import pl.kuchenny.timer.Settings
import pl.kuchenny.timer.core.TimerLimits

/**
 * Ekran ustawień dzwonka i niezawodności alarmu — osobno od tarczy,
 * żeby główny ekran został prosty.
 */
class SettingsScreen(
    private val activity: Activity,
    private val settings: Settings,
    private val listener: Listener
) {

    interface Listener {
        fun onPreviewSound()
        fun onPickSoundFile()
        fun onClearSoundFile()
        fun onOpenNotificationSettings()
        fun onOpenExactAlarmSettings()
        fun onOpenBatterySettings()
        fun onOpenSoundSettings()
        fun onOpenDndSettings()
        fun onBack()
    }

    val root: View

    private lateinit var customSoundLabel: TextView
    private lateinit var vibrationText: TextView
    private lateinit var permissionStatus: TextView
    private lateinit var notificationStatus: TextView
    private lateinit var exactAlarmStatus: TextView
    private lateinit var batteryStatus: TextView
    private lateinit var notificationPolicyStatus: TextView

    init {
        root = buildScreen()
    }

    private fun buildScreen(): View {
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Palette.background)
            setPadding(activity.dp(18), activity.dp(10), activity.dp(18), 0)
        }

        val topBar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        topBar.addView(
            activity.label("Ustawienia", 18f, Palette.text, true),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        topBar.addView(
            activity.settingsButton("Wróć", listener::onBack),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                activity.dp(40)
            )
        )
        content.addView(topBar, activity.matchWidth(bottom = 12))

        val scroll = ScrollView(activity).apply {
            isFillViewport = true
            clipToPadding = false
        }
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, activity.dp(24))
        }
        scroll.addView(column)
        content.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        column.addView(buildSoundPanel(), activity.matchWidth(bottom = 16))
        column.addView(buildReliabilityPanel())
        return content
    }

    private fun buildSoundPanel(): View {
        val soundPanel = panel()
        soundPanel.addView(activity.label("Dzwonek", 17f, Palette.text, true))
        soundPanel.addView(
            activity.label("Krótkie pojedyncze dzwonki timera kuchennego.", 13f, Palette.muted),
            activity.matchWidth(top = 4, bottom = 12)
        )

        val kitchenSoundLabel = activity.label("", 13f, Palette.mint, true)
        soundPanel.addView(kitchenSoundLabel, activity.matchWidth(bottom = 2))
        soundPanel.addView(
            soundPicker(
                names = Settings.SOUND_NAMES,
                initial = settings.soundStyle,
                onChange = { settings.soundStyle = it },
                label = kitchenSoundLabel
            ),
            LinearLayout.LayoutParams(-1, activity.dp(86))
        )

        customSoundLabel = activity.label("", 12f, Palette.muted)
        soundPanel.addView(customSoundLabel, activity.matchWidth(top = 4, bottom = 4))
        soundPanel.addView(soundFileButtons(), activity.matchWidth(bottom = 4))

        soundPanel.addView(
            activity.label("Powtórz dzwonek (1–20 razy)", 14f, Palette.text, true),
            activity.matchWidth(top = 14, bottom = 2)
        )
        soundPanel.addView(
            activity.label(
                "Liczba powtórzeń dotyczy dzwonków wbudowanych; własny plik gra przez wybrany czas.",
                12f,
                Palette.muted
            ),
            activity.matchWidth(bottom = 4)
        )
        soundPanel.addView(
            NumberPicker(activity).apply {
                minValue = TimerLimits.MIN_BELL_COUNT
                maxValue = TimerLimits.MAX_BELL_COUNT
                wrapSelectorWheel = false
                contentDescription = "Liczba powtórzeń dzwonka"
                value = settings.bellCount
                setOnValueChangedListener { _, _, newValue -> settings.bellCount = newValue }
            },
            LinearLayout.LayoutParams(-1, activity.dp(88))
        )

        soundPanel.addView(
            activity.label("Siła wibracji", 14f, Palette.text, true),
            activity.matchWidth(top = 10, bottom = 4)
        )
        vibrationText = activity.label("", 13f, Palette.mint, true)
        soundPanel.addView(vibrationText)
        soundPanel.addView(
            SeekBar(activity).apply {
                max = TimerLimits.MAX_VIBRATION_STRENGTH
                progress = settings.vibrationStrength
                contentDescription = "Siła wibracji"
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                        if (!fromUser) return
                        settings.vibrationStrength = value
                        renderVibrationStrength(value)
                    }

                    override fun onStartTrackingTouch(bar: SeekBar?) = Unit

                    override fun onStopTrackingTouch(bar: SeekBar?) = Unit
                })
            },
            activity.matchWidth()
        )
        renderVibrationStrength(settings.vibrationStrength)

        soundPanel.addView(
            activity.smallButton("Posłuchaj dzwonka").apply {
                setOnClickListener { listener.onPreviewSound() }
            },
            activity.matchWidth(top = 14)
        )
        return soundPanel
    }

    private fun buildReliabilityPanel(): View {
        val reliabilityPanel = panel()
        reliabilityPanel.addView(activity.label("Żeby alarm nie zasnął", 17f, Palette.text, true))
        reliabilityPanel.addView(
            activity.label(
                "Alarm działa po schowaniu aplikacji. Włącz poniższe zgody dla najlepszej niezawodności.",
                13f,
                Palette.muted
            ),
            activity.matchWidth(top = 4, bottom = 10)
        )
        reliabilityPanel.addView(
            activity.label(
                "Ta zgoda dopuszcza alarmy Androida w „Nie przeszkadzać”; nie wyłącza tego trybu.",
                12f,
                Palette.muted
            ),
            activity.matchWidth(bottom = 10)
        )

        permissionStatus = activity.label("", 12f, Palette.mint)
        notificationStatus = activity.label("", 13f, Palette.text)
        exactAlarmStatus = activity.label("", 13f, Palette.text)
        batteryStatus = activity.label("", 13f, Palette.text)
        notificationPolicyStatus = activity.label("", 13f, Palette.text)
        reliabilityPanel.addView(permissionStatus, activity.matchWidth(bottom = 7))
        reliabilityPanel.addView(
            activity.settingsButton("Ustawienia powiadomień", listener::onOpenNotificationSettings),
            activity.matchWidth(bottom = 7)
        )
        reliabilityPanel.addView(
            activity.settingsButton("Zezwól na dokładne alarmy", listener::onOpenExactAlarmSettings),
            activity.matchWidth(bottom = 7)
        )
        reliabilityPanel.addView(
            activity.settingsButton("Wyłącz oszczędzanie baterii", listener::onOpenBatterySettings),
            activity.matchWidth(bottom = 8)
        )
        reliabilityPanel.addView(
            activity.settingsButton("Sprawdź głośność alarmu", listener::onOpenSoundSettings),
            activity.matchWidth(bottom = 8)
        )
        reliabilityPanel.addView(
            activity.settingsButton("Zezwól na alarm w „Nie przeszkadzać”", listener::onOpenDndSettings),
            activity.matchWidth(bottom = 8)
        )
        reliabilityPanel.addView(notificationStatus)
        reliabilityPanel.addView(exactAlarmStatus, activity.matchWidth(top = 4))
        reliabilityPanel.addView(batteryStatus, activity.matchWidth(top = 4))
        reliabilityPanel.addView(notificationPolicyStatus, activity.matchWidth(top = 4))
        return reliabilityPanel
    }

    // --- wyświetlanie stanu -------------------------------------------------

    fun renderPermissions(state: PermissionUiState) {
        setText(notificationStatus, statusText("Powiadomienia", state.notifications))
        setText(exactAlarmStatus, statusText("Dokładny alarm", state.exactAlarms))
        setText(batteryStatus, statusText("Bez oszczędzania baterii", state.batteryUnrestricted))
        setText(
            notificationPolicyStatus,
            when {
                !state.dndAccess -> statusText("Dostęp do „Nie przeszkadzać”", false)
                !state.alarmsDuringDnd -> "Alarmy w „Nie przeszkadzać”: stuknij „Zezwól na alarm”"
                else -> statusText("Alarmy w „Nie przeszkadzać”", true)
            }
        )
        setText(
            permissionStatus,
            if (state.notifications && state.exactAlarms && state.batteryUnrestricted) {
                "Alarm ma najlepsze dostępne warunki działania."
            } else {
                "Ustawienia Androida mogą ograniczać alarm w tle."
            }
        )
    }

    fun renderSoundLabel(label: String) {
        setText(customSoundLabel, label)
    }

    private fun renderVibrationStrength(value: Int) {
        setText(
            vibrationText,
            if (value <= 0) "Wyłączona" else "$value / ${TimerLimits.MAX_VIBRATION_STRENGTH}"
        )
    }

    // --- pomocnicze ---------------------------------------------------------

    private fun panel(): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(activity.dp(16), activity.dp(15), activity.dp(16), activity.dp(15))
        background = activity.rounded(Palette.panel, 22)
    }

    private fun soundPicker(
        names: List<String>,
        initial: Int,
        onChange: (Int) -> Unit,
        label: TextView
    ): NumberPicker = NumberPicker(activity).apply {
        minValue = 1
        maxValue = names.size
        displayedValues = names.toTypedArray()
        wrapSelectorWheel = true
        contentDescription = "Wybór dźwięku"
        value = initial.coerceIn(0, names.lastIndex) + 1
        label.text = names[value - 1]
        setOnValueChangedListener { _, _, newValue ->
            onChange(newValue - 1)
            label.text = names[newValue - 1]
        }
    }

    private fun soundFileButtons(): LinearLayout {
        val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        val choose = activity.smallButton("Wybierz własny plik").apply {
            setOnClickListener { listener.onPickSoundFile() }
        }
        val clear = activity.smallButton("Wbudowany").apply {
            setOnClickListener { listener.onClearSoundFile() }
        }
        row.addView(choose, LinearLayout.LayoutParams(0, activity.dp(44), 1f))
        row.addView(
            clear,
            LinearLayout.LayoutParams(0, activity.dp(44), 1f).apply { marginStart = activity.dp(8) }
        )
        return row
    }

    private fun statusText(label: String, enabled: Boolean): String =
        "$label: ${if (enabled) "włączone" else "wymaga zgody"}"

    private fun setText(view: TextView, value: String) {
        if (view.text.toString() != value) view.text = value
    }
}
