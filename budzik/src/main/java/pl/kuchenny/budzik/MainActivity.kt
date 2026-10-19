package pl.kuchenny.budzik

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.OpenableColumns
import android.provider.Settings as SystemSettings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import pl.kuchenny.budzik.core.AlarmItem
import pl.kuchenny.budzik.alarm.AlarmPlayer
import pl.kuchenny.budzik.alarm.ShakeTester
import pl.kuchenny.budzik.core.AlarmController
import pl.kuchenny.budzik.core.AlarmsSnapshot
import pl.kuchenny.budzik.core.CustomSound
import pl.kuchenny.budzik.core.Days
import pl.kuchenny.budzik.core.Ramp
import pl.kuchenny.budzik.core.Shake
import pl.kuchenny.budzik.core.TimeText
import pl.kuchenny.budzik.ui.Palette
import pl.kuchenny.budzik.ui.PermissionUiState
import pl.kuchenny.budzik.ui.choiceChip
import pl.kuchenny.budzik.ui.dp
import pl.kuchenny.budzik.ui.label
import pl.kuchenny.budzik.ui.matchWidth
import pl.kuchenny.budzik.ui.primaryButton
import pl.kuchenny.budzik.ui.rounded
import pl.kuchenny.budzik.ui.settingsButton
import pl.kuchenny.budzik.ui.smallButton
import pl.kuchenny.budzik.ui.styleChip

/**
 * Ekran budzika: lista alarmów i ustawienia dzwonka na jednej stronie.
 */
class MainActivity : Activity() {

    private lateinit var settings: AlarmSettings
    private lateinit var controller: AlarmController

    private lateinit var alarmsColumn: LinearLayout
    private lateinit var nowText: TextView
    private lateinit var nextText: TextView
    private lateinit var volumeText: TextView
    private lateinit var rampText: TextView
    private lateinit var soundPicker: NumberPicker
    private lateinit var soundLabel: TextView
    private lateinit var previewButton: Button
    private lateinit var removeSoundButton: Button
    private var previewPlayer: AlarmPlayer? = null
    private lateinit var shakeTestButton: Button
    private lateinit var shakeTestText: TextView
    private var shakeTester: ShakeTester? = null
    private var shakeTestPeak = 0
    private lateinit var vibrationText: TextView
    private lateinit var permissionStatus: TextView
    private lateinit var notificationStatus: TextView
    private lateinit var exactStatus: TextView
    private lateinit var batteryStatus: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val stopPreviewTask = Runnable { stopPreview() }
    private val stopShakeTestTask = Runnable { stopShakeTest() }
    private val ticker = object : Runnable {
        override fun run() {
            renderNow()
            handler.postDelayed(this, TICK_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Palette.background
        window.navigationBarColor = Palette.background

        settings = AlarmSettings(this)
        controller = BudzikGraph.controller(this)
        setContentView(buildScreen())
        renderAll()

        if (Build.VERSION.SDK_INT >= 33 && !hasNotificationPermission()) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        renderAll()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        stopPreview()
        stopShakeTest()
        super.onPause()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_SOUND || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        addCustomSound(uri)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTIFICATIONS) renderPermissions()
    }

    // --- budowanie ekranu ---------------------------------------------------

    private fun buildScreen(): View {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Palette.background)
            isFillViewport = true
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(28))
        }
        scroll.addView(content)

        content.addView(label("BUDZIK", 12f, Palette.mint, true), matchWidth(bottom = 6))
        content.addView(label("Alarmy", 30f, Palette.text, true), matchWidth(bottom = 10))
        nowText = label("", 20f, Palette.text, true)
        content.addView(nowText, matchWidth(bottom = 4))
        nextText = label("", 13f, Palette.muted)
        content.addView(nextText, matchWidth(bottom = 18))

        alarmsColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(alarmsColumn, matchWidth(bottom = 12))

        content.addView(
            primaryButton("Dodaj alarm").apply { setOnClickListener { showEditDialog(null) } },
            matchWidth(bottom = 20)
        )

        content.addView(buildSoundPanel(), matchWidth(bottom = 16))
        content.addView(buildReliabilityPanel(), matchWidth(bottom = 16))
        return scroll
    }

    private fun buildSoundPanel(): View {
        val panel = panel()
        panel.addView(label("Dzwonek", 17f, Palette.text, true))

        panel.addView(
            label("Dźwięk", 14f, Palette.text, true),
            matchWidth(top = 10, bottom = 2)
        )
        soundLabel = label("", 13f, Palette.mint, true)
        panel.addView(soundLabel)
        soundPicker = NumberPicker(this).apply {
            contentDescription = "Dźwięk budzika"
            setOnValueChangedListener { _, _, newValue -> selectSound(newValue - 1) }
        }
        panel.addView(soundPicker, LinearLayout.LayoutParams(-1, dp(90)))

        val soundButtons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        previewButton = smallButton("Odsłuchaj").apply { setOnClickListener { togglePreview() } }
        soundButtons.addView(previewButton, LinearLayout.LayoutParams(0, dp(44), 1f))
        soundButtons.addView(
            smallButton("Dodaj własny dźwięk").apply { setOnClickListener { pickCustomSound() } },
            LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginStart = dp(8) }
        )
        panel.addView(soundButtons, matchWidth(top = 6))
        removeSoundButton = smallButton("Usuń ten własny dźwięk").apply {
            setOnClickListener { removeSelectedSound() }
        }
        panel.addView(removeSoundButton, matchWidth(top = 6))
        renderSounds()

        panel.addView(
            label("Głośność", 14f, Palette.text, true),
            matchWidth(top = 12, bottom = 2)
        )
        volumeText = label("", 13f, Palette.mint, true)
        panel.addView(volumeText)
        panel.addView(
            SeekBar(this).apply {
                max = 100
                progress = settings.volume
                contentDescription = "Głośność budzika"
                setOnSeekBarChangeListener(simpleListener { value ->
                    settings.volume = value
                    renderVolume()
                })
            },
            matchWidth()
        )

        panel.addView(
            label("Narastanie głośności", 14f, Palette.text, true),
            matchWidth(top = 12, bottom = 2)
        )
        rampText = label("", 13f, Palette.mint, true)
        panel.addView(rampText)
        panel.addView(
            SeekBar(this).apply {
                max = Ramp.MAX_SECONDS / Ramp.STEP_SECONDS
                progress = settings.rampSeconds / Ramp.STEP_SECONDS
                contentDescription = "Narastanie głośności"
                setOnSeekBarChangeListener(simpleListener { value ->
                    settings.rampSeconds = value * Ramp.STEP_SECONDS
                    renderRamp()
                })
            },
            matchWidth()
        )
        panel.addView(
            label("Narastanie działa przy dzwonieniu alarmu. „Odsłuchaj” gra od razu głośno.", 12f, Palette.muted),
            matchWidth(top = 2)
        )

        panel.addView(
            label("Wibracja", 14f, Palette.text, true),
            matchWidth(top = 14, bottom = 2)
        )
        vibrationText = label("", 13f, Palette.mint, true)
        panel.addView(vibrationText)
        panel.addView(
            SeekBar(this).apply {
                max = AlarmSettings.MAX_VIBRATION_STRENGTH
                progress = settings.vibrationStrength
                contentDescription = "Siła wibracji"
                setOnSeekBarChangeListener(simpleListener { value ->
                    settings.vibrationStrength = value
                    renderVibration()
                })
            },
            matchWidth()
        )

        val shakeBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        shakeBox.addView(
            label("Siła potrząśnięcia", 14f, Palette.text, true),
            matchWidth(top = 4, bottom = 2)
        )
        val shakeText = label(Shake.describe(settings.shakeLevel), 13f, Palette.mint, true)
        shakeBox.addView(shakeText)
        shakeBox.addView(
            SeekBar(this).apply {
                max = Shake.MAX_LEVEL - Shake.MIN_LEVEL
                progress = settings.shakeLevel - Shake.MIN_LEVEL
                contentDescription = "Siła potrząśnięcia"
                setOnSeekBarChangeListener(simpleListener { value ->
                    settings.shakeLevel = value + Shake.MIN_LEVEL
                    setText(shakeText, Shake.describe(settings.shakeLevel))
                    if (shakeTester != null) renderShakeTest()
                })
            },
            matchWidth()
        )
        shakeBox.addView(
            label("Lekko — wystarczy ruch ręką. Mocno — trzeba energicznie potrząsnąć.", 12f, Palette.muted),
            matchWidth(top = 2)
        )
        shakeTestButton = smallButton("Sprawdź potrząśnięcie").apply {
            setOnClickListener { toggleShakeTest() }
        }
        shakeBox.addView(shakeTestButton, matchWidth(top = 8))
        shakeTestText = label("", 13f, Palette.mint, true).apply { visibility = View.GONE }
        shakeBox.addView(shakeTestText, matchWidth(top = 6))
        shakeBox.visibility = if (settings.shakeEnabled) View.VISIBLE else View.GONE

        panel.addView(
            checkBox("Potrząśnij, aby wyłączyć", settings.shakeEnabled) {
                settings.shakeEnabled = it
                if (!it) stopShakeTest()
                shakeBox.visibility = if (it) View.VISIBLE else View.GONE
            },
            matchWidth(top = 12)
        )
        panel.addView(shakeBox, matchWidth())
        panel.addView(
            checkBox("Odwróć telefon, aby wyłączyć", settings.flipEnabled) {
                settings.flipEnabled = it
            },
            matchWidth(top = 4)
        )
        return panel
    }

    private fun buildReliabilityPanel(): View {
        val panel = panel()
        panel.addView(label("Żeby budzik nie zasnął", 17f, Palette.text, true))
        panel.addView(
            label(
                "Alarm działa po schowaniu aplikacji. Te zgody zwiększają pewność, że zadzwoni.",
                13f,
                Palette.muted
            ),
            matchWidth(top = 4, bottom = 10)
        )

        permissionStatus = label("", 12f, Palette.mint)
        notificationStatus = label("", 13f, Palette.text)
        exactStatus = label("", 13f, Palette.text)
        batteryStatus = label("", 13f, Palette.text)

        panel.addView(permissionStatus, matchWidth(bottom = 7))
        panel.addView(
            settingsButton("Ustawienia powiadomień", ::openNotificationSettings),
            matchWidth(bottom = 7)
        )
        panel.addView(
            settingsButton("Zezwól na dokładne alarmy", ::openExactAlarmSettings),
            matchWidth(bottom = 7)
        )
        panel.addView(
            settingsButton("Wyłącz oszczędzanie baterii", ::openBatterySettings),
            matchWidth(bottom = 8)
        )
        panel.addView(notificationStatus)
        panel.addView(exactStatus, matchWidth(top = 4))
        panel.addView(batteryStatus, matchWidth(top = 4))
        return panel
    }

    private fun panel(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(15), dp(16), dp(15))
        background = rounded(Palette.panel, 22)
    }

    private fun checkBox(text: String, checked: Boolean, onChange: (Boolean) -> Unit): CheckBox =
        CheckBox(this).apply {
            setText(text)
            isChecked = checked
            setTextColor(Palette.text)
            textSize = 14f
            setOnCheckedChangeListener { _, value -> onChange(value) }
        }

    private fun simpleListener(onChange: (Int) -> Unit) =
        object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                if (fromUser) onChange(value)
            }

            override fun onStartTrackingTouch(bar: SeekBar?) = Unit

            override fun onStopTrackingTouch(bar: SeekBar?) = Unit
        }

    // --- lista alarmów ------------------------------------------------------

    private fun renderAll() {
        renderAlarms()
        renderNow()
        renderVolume()
        renderRamp()
        renderVibration()
        renderPermissions()
    }

    private fun renderAlarms() {
        alarmsColumn.removeAllViews()
        val alarms = controller.snapshot().alarms
        if (alarms.isEmpty()) {
            alarmsColumn.addView(
                label("Nie masz jeszcze alarmu. Dotknij „Dodaj alarm”.", 13f, Palette.muted),
                matchWidth(bottom = 8)
            )
        } else {
            alarms.forEach { alarm ->
                alarmsColumn.addView(alarmCard(alarm), matchWidth(bottom = 10))
            }
        }
        renderNext()
    }

    private fun alarmCard(alarm: AlarmItem): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = rounded(Palette.panel, 22)
        }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(label(TimeText.clock(alarm.hour, alarm.minute), 34f, Palette.text, true))
        column.addView(
            label(TimeText.days(alarm.days), 12f, Palette.muted),
            matchWidth(top = 2)
        )
        card.addView(column, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val toggle = Switch(this).apply {
            isChecked = alarm.enabled
            contentDescription = "Alarm ${TimeText.clock(alarm.hour, alarm.minute)}"
            setOnCheckedChangeListener { _, checked ->
                controller.setEnabled(alarm.id, checked)
                renderNext()
            }
        }
        card.addView(toggle)
        card.setOnClickListener { showEditDialog(alarm.id) }
        return card
    }

    private fun renderNow() {
        val now = System.currentTimeMillis()
        setText(nowText, "Teraz ${TimeText.clock(now)}")
        renderNext()
    }

    private fun renderNext() {
        val next = controller.nextAlarm()
        setText(
            nextText,
            if (next == null) {
                "Brak włączonych alarmów."
            } else {
                val (alarm, triggerAt) = next
                "Następny alarm ${TimeText.clock(alarm.hour, alarm.minute)} · " +
                    TimeText.until(System.currentTimeMillis(), triggerAt)
            }
        )
    }

    private fun renderVolume() {
        setText(volumeText, "${settings.volume}%")
    }

    private fun renderRamp() {
        setText(rampText, TimeText.duration(settings.rampSeconds))
    }

    private fun renderVibration() {
        val strength = settings.vibrationStrength
        setText(vibrationText, if (strength <= 0) "Wyłączona" else "$strength / 10")
    }

    private fun renderPermissions() {
        val state = currentPermissions()
        setText(notificationStatus, statusText("Powiadomienia", state.notifications))
        setText(exactStatus, statusText("Dokładny alarm", state.exactAlarms))
        setText(batteryStatus, statusText("Bez oszczędzania baterii", state.batteryUnrestricted))
        setText(
            permissionStatus,
            if (state.notifications && state.exactAlarms && state.batteryUnrestricted) {
                "Budzik ma najlepsze dostępne warunki działania."
            } else {
                "Ustawienia Androida mogą ograniczać alarm w tle."
            }
        )
    }

    // --- edycja alarmu ------------------------------------------------------

    private fun showEditDialog(alarmId: Long?) {
        val existing = alarmId?.let { controller.snapshot().find(it) }
        val hourPicker = NumberPicker(this).apply {
            minValue = 0
            maxValue = 23
            wrapSelectorWheel = true
            value = existing?.hour ?: 7
            contentDescription = "Godzina"
        }
        val minutePicker = NumberPicker(this).apply {
            minValue = 0
            maxValue = 59
            wrapSelectorWheel = true
            value = existing?.minute ?: 0
            contentDescription = "Minuty"
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
        }
        row.addView(hourPicker, LinearLayout.LayoutParams(0, dp(130), 1f))
        row.addView(label(":", 26f, Palette.text, true), LinearLayout.LayoutParams(-2, -2))
        row.addView(minutePicker, LinearLayout.LayoutParams(0, dp(130), 1f))

        var days = existing?.days ?: Days.ALL
        val daysHint = label(TimeText.days(days), 13f, Palette.mint, true)
        val daysRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val dayChips = mutableListOf<Button>()
        Days.SHORT_NAMES.forEachIndexed { index, name ->
            val chip = choiceChip(name, Days.isOn(days, index))
            chip.setOnClickListener {
                days = Days.toggle(days, index)
                dayChips.forEachIndexed { chipIndex, button ->
                    styleChip(button, Days.isOn(days, chipIndex))
                }
                daysHint.text = TimeText.days(days)
            }
            dayChips.add(chip)
            val params = LinearLayout.LayoutParams(0, dp(40), 1f)
            if (index > 0) params.marginStart = dp(4)
            daysRow.addView(chip, params)
        }

        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), dp(4))
        }
        form.addView(row)
        form.addView(label("Dni tygodnia", 14f, Palette.text, true), matchWidth(top = 8, bottom = 6))
        form.addView(daysRow, matchWidth())
        form.addView(daysHint, matchWidth(top = 6))
        form.addView(
            label("Bez zaznaczonych dni alarm zadzwoni raz.", 12f, Palette.muted),
            matchWidth(top = 2)
        )

        val builder = AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Nowy alarm" else "Edycja alarmu")
            .setView(form)
            .setPositiveButton("Zapisz") { _, _ ->
                if (existing == null) {
                    if (controller.add(hourPicker.value, minutePicker.value, days = days) == null) {
                        Toast.makeText(
                            this,
                            "Można mieć najwyżej ${AlarmsSnapshot.MAX_ALARMS} alarmów.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    controller.update(existing.id, hourPicker.value, minutePicker.value, days)
                }
                renderAll()
            }
            .setNegativeButton("Anuluj", null)

        if (existing != null) {
            builder.setNeutralButton("Usuń") { _, _ ->
                controller.remove(existing.id)
                renderAll()
            }
        }
        builder.show()
    }

    // --- dźwięk -------------------------------------------------------------

    /** Wbudowane melodie, a po nich własne dźwięki. */
    private fun soundNames(customs: List<CustomSound>): List<String> =
        AlarmSettings.MELODY_NAMES + customs.map { "♪ ${it.name}" }

    private fun selectedSoundIndex(customs: List<CustomSound>): Int {
        val selected = settings.selectedCustomSound ?: return settings.melodyStyle
        return AlarmSettings.MELODY_NAMES.size + customs.indexOfFirst { it.uri == selected.uri }
    }

    private fun renderSounds() {
        val customs = settings.customSounds
        val names = soundNames(customs)
        val index = selectedSoundIndex(customs).coerceIn(0, names.lastIndex)
        // Najpierw zerujemy nazwy, inaczej NumberPicker nie przyjmie dłuższej listy.
        soundPicker.displayedValues = null
        soundPicker.minValue = 1
        soundPicker.maxValue = names.size
        soundPicker.displayedValues = names.toTypedArray()
        soundPicker.value = index + 1
        soundPicker.wrapSelectorWheel = true
        setText(soundLabel, names[index])
        renderRemoveSound()
    }

    private fun renderRemoveSound() {
        removeSoundButton.visibility =
            if (settings.selectedCustomSound != null) View.VISIBLE else View.GONE
    }

    private fun selectSound(index: Int) {
        stopPreview()
        val customs = settings.customSounds
        val melodies = AlarmSettings.MELODY_NAMES.size
        if (index < melodies) {
            settings.melodyStyle = index
            settings.selectedCustomSound = null
        } else {
            settings.selectedCustomSound = customs.getOrNull(index - melodies)
        }
        setText(soundLabel, soundNames(customs).getOrElse(index) { "" })
        renderRemoveSound()
    }

    private fun pickCustomSound() {
        if (settings.customSounds.size >= CustomSound.MAX_SOUNDS) {
            Toast.makeText(
                this,
                "Można dodać najwyżej ${CustomSound.MAX_SOUNDS} własnych dźwięków.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("audio/*")
            .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("audio/*", "application/ogg"))
            .addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        try {
            startActivityForResult(intent, REQUEST_SOUND)
        } catch (exception: ActivityNotFoundException) {
            Toast.makeText(this, "Brak aplikacji do wyboru plików.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addCustomSound(uri: Uri) {
        try {
            // Bez trwałej zgody budzik nie odczyta pliku po restarcie telefonu.
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (exception: SecurityException) {
            Toast.makeText(this, "Nie udało się zachować dostępu do pliku.", Toast.LENGTH_SHORT).show()
            return
        }
        val sound = CustomSound(uri.toString(), displayName(uri))
        settings.customSounds = settings.customSounds.filterNot { it.uri == sound.uri } + sound
        settings.selectedCustomSound = sound
        stopPreview()
        renderSounds()
    }

    private fun displayName(uri: Uri): String {
        val name = runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull()
        return name?.substringBeforeLast('.')?.ifBlank { null } ?: "Własny dźwięk"
    }

    private fun removeSelectedSound() {
        val selected = settings.selectedCustomSound ?: return
        stopPreview()
        runCatching {
            contentResolver.releasePersistableUriPermission(
                Uri.parse(selected.uri),
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        settings.customSounds = settings.customSounds.filterNot { it.uri == selected.uri }
        settings.selectedCustomSound = null
        renderSounds()
    }

    private fun togglePreview() {
        if (previewPlayer != null) {
            stopPreview()
            return
        }
        val player = AlarmPlayer(this)
        player.volume = settings.volume / 100f
        val custom = settings.selectedCustomSound
        if (custom != null) {
            player.startSound(custom.uri, settings.melodyStyle)
        } else {
            player.startMelody(settings.melodyStyle)
        }
        previewPlayer = player
        previewButton.text = "Zatrzymaj"
        handler.postDelayed(stopPreviewTask, PREVIEW_MS)
    }

    private fun stopPreview() {
        handler.removeCallbacks(stopPreviewTask)
        previewPlayer?.stop()
        previewPlayer = null
        if (::previewButton.isInitialized) previewButton.text = "Odsłuchaj"
    }

    // --- sprawdzanie potrząśnięcia -----------------------------------------

    private fun toggleShakeTest() {
        if (shakeTester != null) {
            stopShakeTest()
            return
        }
        val tester = ShakeTester(this) { acceleration ->
            val level = Shake.levelFor(acceleration)
            if (level > shakeTestPeak) {
                shakeTestPeak = level
                renderShakeTest()
            }
        }
        if (!tester.start()) {
            Toast.makeText(this, "Ten telefon nie ma czujnika ruchu.", Toast.LENGTH_SHORT).show()
            return
        }
        shakeTester = tester
        shakeTestPeak = 0
        shakeTestButton.text = "Zakończ sprawdzanie"
        shakeTestText.visibility = View.VISIBLE
        renderShakeTest()
        handler.postDelayed(stopShakeTestTask, SHAKE_TEST_MS)
    }

    /** Wynik zostaje na ekranie po zakończeniu, żeby można było go przeczytać. */
    private fun stopShakeTest() {
        handler.removeCallbacks(stopShakeTestTask)
        shakeTester?.stop()
        shakeTester = null
        if (::shakeTestButton.isInitialized) shakeTestButton.text = "Sprawdź potrząśnięcie"
    }

    private fun renderShakeTest() {
        setText(shakeTestText, Shake.testResult(shakeTestPeak, settings.shakeLevel))
    }

    // --- uprawnienia --------------------------------------------------------

    private fun currentPermissions(): PermissionUiState = PermissionUiState(
        notifications = hasNotificationPermission(),
        exactAlarms = hasExactAlarmPermission(),
        batteryUnrestricted = getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(packageName)
    )

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun hasExactAlarmPermission(): Boolean =
        Build.VERSION.SDK_INT < 31 ||
            getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun openNotificationSettings() {
        if (Build.VERSION.SDK_INT >= 33 && !hasNotificationPermission()) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startActivity(
                Intent(SystemSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(SystemSettings.EXTRA_APP_PACKAGE, packageName)
            )
        }
    }

    private fun openExactAlarmSettings() {
        if (hasExactAlarmPermission()) {
            Toast.makeText(this, "Dokładne alarmy są już włączone.", Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(
            Intent(
                SystemSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:$packageName")
            )
        )
    }

    private fun openBatterySettings() {
        val powerManager = getSystemService(PowerManager::class.java)
        if (powerManager.isIgnoringBatteryOptimizations(packageName)) {
            Toast.makeText(this, "Oszczędzanie baterii jest już wyłączone.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            startActivity(
                Intent(SystemSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:$packageName"))
            )
        } catch (exception: ActivityNotFoundException) {
            startActivity(Intent(SystemSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun statusText(label: String, enabled: Boolean): String =
        "$label: ${if (enabled) "włączone" else "wymaga zgody"}"

    private fun setText(view: TextView, value: String) {
        if (view.text.toString() != value) view.text = value
    }

    private companion object {
        const val TICK_INTERVAL_MS = 1_000L
        const val REQUEST_NOTIFICATIONS = 90
        const val REQUEST_SOUND = 91
        const val PREVIEW_MS = 8_000L
        const val SHAKE_TEST_MS = 15_000L
    }
}
