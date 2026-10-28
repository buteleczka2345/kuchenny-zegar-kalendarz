package pl.kuchenny.timer

import android.Manifest
import android.annotation.TargetApi
import android.app.Activity
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
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
import android.widget.DatePicker
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.ScrollView
import android.widget.Toast
import pl.kuchenny.timer.alarm.AlarmService
import pl.kuchenny.timer.alarm.Notifications
import pl.kuchenny.timer.alarm.Reminders
import pl.kuchenny.timer.core.ReconcileResult
import pl.kuchenny.timer.core.TimeFormat
import pl.kuchenny.timer.core.TimerController
import pl.kuchenny.timer.core.TimerLimits
import pl.kuchenny.timer.core.TimerState
import pl.kuchenny.timer.ui.AppTabs
import pl.kuchenny.timer.ui.Palette
import pl.kuchenny.timer.ui.PermissionUiState
import pl.kuchenny.timer.ui.SettingsScreen
import pl.kuchenny.timer.ui.TimerPage
import pl.kuchenny.timer.ui.label
import pl.kuchenny.timer.ui.matchWidth
import pl.kuchenny.timer.ui.smallButton
import java.io.File
import java.util.Calendar

/**
 * Ekran aplikacji: pełnoekranowa tarcza i osobne ustawienia pod przyciskiem.
 *
 * Klasa zajmuje się cyklem życia, uprawnieniami, wyborem plików i nazwami timerów.
 * Stanem timerów rządzi [TimerController].
 */
class MainActivity : Activity(), TimerPage.Listener, SettingsScreen.Listener {

    private lateinit var settings: Settings
    private lateinit var controller: TimerController
    private lateinit var timerPage: TimerPage
    private lateinit var settingsScreen: SettingsScreen

    private val handler = Handler(Looper.getMainLooper())
    private var pendingStart = false
    private var waitingForDndSettings = false
    private var settingsVisible = false
    private var reminderPlayer: MediaPlayer? = null

    private val ticker = object : Runnable {
        override fun run() {
            val snapshot = controller.snapshot()
            timerPage.renderTimers(snapshot)
            timerPage.renderTimer(snapshot.active, System.currentTimeMillis())
            handler.postDelayed(this, TICK_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Palette.background
        window.navigationBarColor = Palette.background

        settings = Settings(this)
        controller = TimerGraph.controller(this)
        Notifications.ensureChannels(this)

        timerPage = TimerPage(this, settings, controller.active().presetMillis, this)
        settingsScreen = SettingsScreen(this, settings, this)

        val container = FrameLayout(this).apply {
            addView(
                timerPage.root,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
            addView(
                settingsScreen.root,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }
        setContentView(AppTabs.wrap(this, container, AppTabs.Tab.TIMER))
        settingsScreen.root.visibility = View.GONE
        renderAll()
    }

    override fun onResume() {
        super.onResume()
        val reconciled = controller.reconcile()
        if (reconciled == ReconcileResult.TIMER_MISSED) {
            Toast.makeText(
                this,
                "Czas minął, gdy aplikacja była zamknięta — alarm nie zadzwonił.",
                Toast.LENGTH_LONG
            ).show()
        }
        if (waitingForDndSettings) {
            waitingForDndSettings = false
            if (getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted) {
                enableAlarmsDuringDnd()
            } else {
                renderPermissions()
            }
        }
        renderAll()
        startTicker()
    }

    override fun onPause() {
        stopTicker()
        super.onPause()
    }

    override fun onDestroy() {
        stopTicker()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Android, ale obsługa przycisku Wstecz nadal tego wymaga")
    override fun onBackPressed() {
        if (settingsVisible) {
            showSettings(false)
            return
        }
        // Zakładki to osobne aktywności: Wstecz chowa całą aplikację zamiast przełączać zakładkę.
        moveTaskToBack(true)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_NOTIFICATIONS) return
        val shouldStart = pendingStart
        pendingStart = false
        renderPermissions()
        if (!hasNotificationPermission()) {
            Toast.makeText(
                this,
                "Bez powiadomień Android może ukryć sterowanie alarmem.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        if (shouldStart) startTimer()
    }

    @Deprecated("Deprecated in Android, ale wybór pliku nadal działa przez to API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || requestCode != REQUEST_SOUND) return
        val uri = data?.data ?: return
        if (data.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION == 0) {
            Toast.makeText(
                this,
                "Wybrany plik nie udostępnia dostępu do odczytu. Wybierz inny plik.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (exception: SecurityException) {
            Toast.makeText(
                this,
                "Nie udało się zapamiętać dostępu do tego pliku. Wybierz plik ponownie.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val previous = settings.customSoundUri
        if (previous != null && previous != uri.toString()) {
            releasePersistedSoundPermission(Uri.parse(previous))
        }
        settings.customSoundUri = uri.toString()
        renderSoundLabel()
        Toast.makeText(this, "Dodano własny dzwonek.", Toast.LENGTH_SHORT).show()
    }

    // --- strona z tarczą ----------------------------------------------------

    override fun onPrimaryAction() {
        when (controller.active().state) {
            is TimerState.Ringing -> stopEverything()
            is TimerState.Running -> {
                controller.pause()
                renderAll()
            }
            is TimerState.Paused -> {
                controller.resume()
                renderAll()
            }
            TimerState.Idle -> requestNotificationThenStart()
        }
    }

    override fun onCancelTimer() {
        stopEverything()
    }

    override fun onResetTimer() {
        val wasRinging = controller.active().state is TimerState.Ringing
        controller.reset()
        if (wasRinging) {
            AlarmService.stopRinging(this)
        }
        renderAll()
    }

    override fun onDurationSelected(minutes: Int) {
        if (!controller.select(minutes)) {
            Toast.makeText(this, "Najpierw zatrzymaj dzwonek.", Toast.LENGTH_SHORT).show()
            timerPage.syncDurationDial(controller.active().presetMillis)
            return
        }
        renderAll()
    }

    override fun onQuickAdd(deltaMillis: Long) {
        if (!controller.addTime(deltaMillis)) {
            Toast.makeText(this, "Najpierw zatrzymaj dzwonek.", Toast.LENGTH_SHORT).show()
            return
        }
        renderAll()
    }

    override fun onSelectTimer(id: Long) {
        controller.selectTimer(id)
        renderAll()
    }

    override fun onAddTimer() {
        if (controller.addTimer() == null) {
            Toast.makeText(
                this,
                "Można mieć najwyżej ${TimerLimits.MAX_TIMERS} timerów.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        renderAll()
    }

    override fun onRenameTimer() {
        showTimerDialog()
    }

    override fun onOpenSettings() {
        showSettings(true)
    }

    override fun onOpenReminders() {
        showRemindersDialog()
    }

    // --- kalendarz przypomnień ---------------------------------------------

    private fun showRemindersDialog() {
        val reminders = Reminders(this)
        val list = reminders.all()
        val builder = AlertDialog.Builder(this)
            .setTitle("Kalendarz przypomnień")
            .setPositiveButton("Dodaj przypomnienie") { _, _ -> showAddReminderDialog() }
            .setNegativeButton("Zamknij", null)
        if (list.isEmpty()) {
            builder.setMessage(
                "Nie masz przypomnień. Wybierz dzień i godzinę, nagraj, co masz zrobić — " +
                    "o tej porze telefon to powie i sam ucichnie."
            )
        } else {
            builder.setItems(list.map { reminderTitle(it.at) }.toTypedArray()) { _, index ->
                showReminderActions(list[index].id, list[index].at)
            }
        }
        builder.show()
    }

    private fun showReminderActions(id: Long, at: Long) {
        val reminders = Reminders(this)
        AlertDialog.Builder(this)
            .setTitle(reminderTitle(at))
            .setPositiveButton("Odsłuchaj") { _, _ ->
                playRecording(reminders.note(id))
                showRemindersDialog()
            }
            .setNeutralButton("Usuń") { _, _ ->
                reminders.remove(id)
                showRemindersDialog()
            }
            .setNegativeButton("Wstecz") { _, _ -> showRemindersDialog() }
            .show()
    }

    private fun reminderTitle(at: Long): String {
        val calendar = Calendar.getInstance().apply { timeInMillis = at }
        return String.format(
            java.util.Locale.getDefault(),
            "%02d.%02d.%d  %s",
            calendar.get(Calendar.DAY_OF_MONTH),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.YEAR),
            TimeFormat.clock(at)
        )
    }

    private fun showAddReminderDialog() {
        val reminders = Reminders(this)
        val draft = reminders.draft().apply { delete() }
        var recorder: MediaRecorder? = null

        val datePicker = DatePicker(this).apply {
            minDate = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
            }.timeInMillis
        }
        val hourPicker = NumberPicker(this).apply {
            minValue = 0
            maxValue = 23
            wrapSelectorWheel = true
            value = 8
            contentDescription = "Godzina"
        }
        val minutePicker = NumberPicker(this).apply {
            minValue = 0
            maxValue = 59
            wrapSelectorWheel = true
            value = 0
            contentDescription = "Minuty"
        }
        val timeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        timeRow.addView(hourPicker, LinearLayout.LayoutParams(0, dp(120), 1f))
        timeRow.addView(label(":", 26f, Palette.text, true), LinearLayout.LayoutParams(-2, -2))
        timeRow.addView(minutePicker, LinearLayout.LayoutParams(0, dp(120), 1f))

        val recordStatus = label("", 13f, Palette.mint, true)
        val recordButton = smallButton("Nagraj głos")
        val playButton = smallButton("Odsłuchaj")
        fun renderRecordStatus() {
            recordStatus.text = when {
                recorder != null -> "Nagrywanie… mów, co masz zrobić."
                draft.exists() -> "Nagranie gotowe."
                else -> "Brak nagrania."
            }
        }
        fun stopRecording() {
            val active = recorder ?: return
            recorder = null
            // Zbyt krótkie nagranie rzuca wyjątek przy stop() — wtedy go nie ma.
            if (runCatching { active.stop() }.isFailure) draft.delete()
            active.release()
            recordButton.text = "Nagraj głos"
            renderRecordStatus()
        }

        recordButton.setOnClickListener {
            if (recorder != null) {
                stopRecording()
                return@setOnClickListener
            }
            if (Build.VERSION.SDK_INT >= 23 &&
                checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MIC)
                Toast.makeText(this, "Zezwól na mikrofon i dotknij „Nagraj głos” jeszcze raz.", Toast.LENGTH_LONG)
                    .show()
                return@setOnClickListener
            }
            stopReminderPlayback()
            draft.delete()
            val created = if (Build.VERSION.SDK_INT >= 31) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            val started = runCatching {
                created.setAudioSource(MediaRecorder.AudioSource.MIC)
                created.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                created.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                created.setAudioSamplingRate(44_100)
                created.setAudioEncodingBitRate(96_000)
                created.setOutputFile(draft.absolutePath)
                created.prepare()
                created.start()
            }.isSuccess
            if (started) {
                recorder = created
                recordButton.text = "Zatrzymaj nagrywanie"
            } else {
                created.release()
                draft.delete()
                Toast.makeText(this, "Nie udało się włączyć mikrofonu.", Toast.LENGTH_SHORT).show()
            }
            renderRecordStatus()
        }
        playButton.setOnClickListener {
            stopRecording()
            playRecording(draft)
        }
        renderRecordStatus()

        val recordRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        recordRow.addView(recordButton, LinearLayout.LayoutParams(0, dp(44), 1f))
        recordRow.addView(
            playButton,
            LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginStart = dp(8) }
        )

        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), dp(4))
        }
        form.addView(label("Dzień", 14f, Palette.text, true), matchWidth(top = 8))
        form.addView(
            datePicker,
            LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.CENTER_HORIZONTAL }
        )
        form.addView(label("Godzina", 14f, Palette.text, true), matchWidth(top = 8))
        form.addView(timeRow, matchWidth())
        form.addView(
            label("Co masz zrobić — nagraj głosem", 14f, Palette.text, true),
            matchWidth(top = 8, bottom = 6)
        )
        form.addView(recordRow, matchWidth())
        form.addView(recordStatus, matchWidth(top = 6))
        form.addView(
            label(
                "Nagranie zagra jak dzwonek zegara (powtórzenia z ustawień), samo ucichnie i się skasuje.",
                12f,
                Palette.muted
            ),
            matchWidth(top = 2)
        )

        AlertDialog.Builder(this)
            .setTitle("Nowe przypomnienie")
            .setView(ScrollView(this).apply { addView(form) })
            .setPositiveButton("Zapisz") { _, _ ->
                stopRecording()
                val at = Calendar.getInstance().apply {
                    clear()
                    set(
                        datePicker.year,
                        datePicker.month,
                        datePicker.dayOfMonth,
                        hourPicker.value,
                        minutePicker.value,
                        0
                    )
                }.timeInMillis
                when {
                    !draft.exists() ->
                        Toast.makeText(this, "Najpierw nagraj, co masz zrobić.", Toast.LENGTH_LONG).show()
                    at <= System.currentTimeMillis() ->
                        Toast.makeText(this, "Ta godzina już minęła — wybierz późniejszą.", Toast.LENGTH_LONG).show()
                    else -> {
                        val id = reminders.add(at)
                        draft.renameTo(reminders.note(id))
                        Toast.makeText(this, "Przypomnienie: ${reminderTitle(at)}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Anuluj", null)
            .setOnDismissListener {
                stopRecording()
                stopReminderPlayback()
                draft.delete()
            }
            .show()
    }

    private fun playRecording(file: File) {
        stopReminderPlayback()
        if (!file.exists()) return
        reminderPlayer = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(file.absolutePath)
                prepare()
                setVolume(settings.alarmVolume / 100f, settings.alarmVolume / 100f)
                setOnCompletionListener { stopReminderPlayback() }
                start()
            }
        }.getOrNull()
    }

    private fun stopReminderPlayback() {
        reminderPlayer?.let { runCatching { it.release() } }
        reminderPlayer = null
    }

    // --- strona ustawień ----------------------------------------------------

    override fun onBack() {
        showSettings(false)
    }

    override fun onPreviewSound() {
        val busy = controller.snapshot().timers.any {
            it.state is TimerState.Running || it.state is TimerState.Paused || it.state is TimerState.Ringing
        }
        if (busy) {
            Toast.makeText(this, "Podgląd jest dostępny po zatrzymaniu timerów.", Toast.LENGTH_SHORT)
                .show()
            return
        }
        if (!hasNotificationPermission()) {
            Toast.makeText(this, "Włącz powiadomienia, aby odtworzyć próbkę dzwonka.", Toast.LENGTH_LONG)
                .show()
            return
        }
        AlarmService.preview(this)
    }

    override fun onPickSoundFile() {
        openSoundPicker()
    }

    override fun onClearSoundFile() {
        val current = settings.customSoundUri
        if (current != null) {
            releasePersistedSoundPermission(Uri.parse(current))
            settings.customSoundUri = null
            renderSoundLabel()
        }
    }

    override fun onOpenNotificationSettings() {
        if (Build.VERSION.SDK_INT >= 33 && !hasNotificationPermission()) {
            pendingStart = false
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startActivity(
                Intent(SystemSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(SystemSettings.EXTRA_APP_PACKAGE, packageName)
            )
        }
    }

    override fun onOpenExactAlarmSettings() {
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

    override fun onOpenBatterySettings() {
        val powerManager = getSystemService(PowerManager::class.java)
        if (powerManager.isIgnoringBatteryOptimizations(packageName)) {
            Toast.makeText(this, "Oszczędzanie baterii jest już wyłączone dla aplikacji.", Toast.LENGTH_SHORT)
                .show()
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

    override fun onOpenSoundSettings() {
        startActivity(Intent(SystemSettings.ACTION_SOUND_SETTINGS))
    }

    override fun onOpenDndSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Toast.makeText(
                this,
                "Zezwolenie na alarmy w „Nie przeszkadzać” wymaga Androida 7 lub nowszego.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            waitingForDndSettings = true
            startActivity(Intent(SystemSettings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            return
        }
        enableAlarmsDuringDnd()
    }

    // --- nazwa i usuwanie timera -------------------------------------------

    private fun showTimerDialog() {
        val item = controller.active()
        val input = EditText(this).apply {
            setText(item.label)
            setSelection(item.label.length)
            setSingleLine()
            setTextColor(Palette.text)
            setHintTextColor(Palette.muted)
        }
        val container = FrameLayout(this).apply {
            setPadding(dp(24), dp(8), dp(24), 0)
        }
        container.addView(input)

        AlertDialog.Builder(this)
            .setTitle("Nazwa timera")
            .setView(container)
            .setPositiveButton("Zapisz") { _, _ ->
                controller.rename(item.id, input.text.toString())
                renderAll()
            }
            .setNegativeButton("Anuluj", null)
            .setNeutralButton("Usuń") { _, _ -> deleteTimer(item.id) }
            .create()
            .show()
    }

    private fun deleteTimer(id: Long) {
        val wasRinging = controller.snapshot().find(id)?.state is TimerState.Ringing
        if (!controller.removeTimer(id)) {
            Toast.makeText(this, "Musi zostać co najmniej jeden timer.", Toast.LENGTH_SHORT).show()
            return
        }
        if (wasRinging) {
            AlarmService.stopRinging(this)
        }
        renderAll()
    }

    // --- timer --------------------------------------------------------------

    private fun requestNotificationThenStart() {
        if (Build.VERSION.SDK_INT >= 33 && !hasNotificationPermission()) {
            pendingStart = true
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
            return
        }
        startTimer()
    }

    private fun startTimer() {
        if (!controller.start()) {
            Toast.makeText(this, "Najpierw ustaw czas timera.", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "Timer działa również po schowaniu aplikacji.", Toast.LENGTH_SHORT).show()
        renderAll()
    }

    private fun stopEverything() {
        val wasRinging = controller.active().state is TimerState.Ringing
        controller.stop()
        if (wasRinging) {
            AlarmService.stopRinging(this)
        }
        renderAll()
    }

    // --- wyświetlanie stanu -------------------------------------------------

    private fun showSettings(visible: Boolean) {
        settingsVisible = visible
        timerPage.root.visibility = if (visible) View.GONE else View.VISIBLE
        settingsScreen.root.visibility = if (visible) View.VISIBLE else View.GONE
        if (!visible) {
            renderAll()
        } else {
            renderPermissions()
        }
    }

    private fun renderAll() {
        val snapshot = controller.snapshot()
        timerPage.renderTimers(snapshot)
        timerPage.renderTimer(snapshot.active, System.currentTimeMillis())
        timerPage.syncDurationDial(snapshot.active.presetMillis)
        renderSoundLabel()
        renderPermissions()
    }

    private fun renderSoundLabel() {
        if (!::settingsScreen.isInitialized) return
        settingsScreen.renderSoundLabel(customSoundLabel(settings.customSoundUri))
    }

    private fun renderPermissions() {
        if (!::settingsScreen.isInitialized) return
        settingsScreen.renderPermissions(currentPermissions())
    }

    private fun currentPermissions(): PermissionUiState {
        val notificationManager = getSystemService(NotificationManager::class.java)
        val policyGranted = notificationManager.isNotificationPolicyAccessGranted
        val priorityCategories = notificationManager.notificationPolicy?.priorityCategories ?: 0
        val alarmsAllowed = policyGranted &&
            (priorityCategories and NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS) != 0
        return PermissionUiState(
            notifications = hasNotificationPermission(),
            exactAlarms = hasExactAlarmPermission(),
            batteryUnrestricted = getSystemService(PowerManager::class.java)
                .isIgnoringBatteryOptimizations(packageName),
            dndAccess = policyGranted,
            alarmsDuringDnd = alarmsAllowed
        )
    }

    private fun startTicker() {
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    private fun stopTicker() {
        handler.removeCallbacks(ticker)
    }

    // --- uprawnienia i pliki ------------------------------------------------

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun hasExactAlarmPermission(): Boolean =
        Build.VERSION.SDK_INT < 31 || getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    @TargetApi(Build.VERSION_CODES.N)
    private fun enableAlarmsDuringDnd() {
        // Konstruktor NotificationManager.Policy z efektami wizualnymi istnieje od Androida 7.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val notificationManager = getSystemService(NotificationManager::class.java)
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            renderPermissions()
            return
        }
        val policy = notificationManager.notificationPolicy ?: return
        if (policy.priorityCategories and NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS == 0) {
            notificationManager.setNotificationPolicy(
                NotificationManager.Policy(
                    policy.priorityCategories or NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS,
                    policy.priorityCallSenders,
                    policy.priorityMessageSenders,
                    policy.suppressedVisualEffects
                )
            )
        }
        renderPermissions()
        Toast.makeText(
            this,
            "Alarmy są dozwolone w „Nie przeszkadzać”. Tryb DND pozostaje włączony.",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun openSoundPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        try {
            startActivityForResult(intent, REQUEST_SOUND)
        } catch (exception: ActivityNotFoundException) {
            Toast.makeText(this, "Nie znaleziono aplikacji do wybierania plików audio.", Toast.LENGTH_LONG)
                .show()
        }
    }

    private fun customSoundLabel(uriValue: String?): String {
        if (uriValue == null) return "Aktywny: dźwięk wbudowany"
        val uri = Uri.parse(uriValue)
        var displayName: String? = null
        val cursor: Cursor? = try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        } catch (exception: SecurityException) {
            return "Dostęp do własnego dźwięku wygasł — wybierz plik ponownie"
        } catch (exception: IllegalArgumentException) {
            return "Nie można odczytać własnego dźwięku — wybierz plik ponownie"
        }
        cursor?.use {
            if (it.moveToFirst()) {
                val nameColumn = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameColumn >= 0) displayName = it.getString(nameColumn)
            }
        }
        return if (displayName.isNullOrBlank()) {
            "Aktywny własny dźwięk"
        } else {
            "Aktywny: $displayName"
        }
    }

    private fun releasePersistedSoundPermission(uri: Uri) {
        try {
            contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (exception: SecurityException) {
            Toast.makeText(this, "Nie udało się zwolnić dostępu do poprzedniego pliku.", Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val TICK_INTERVAL_MS = 1_000L
        const val REQUEST_NOTIFICATIONS = 90
        const val REQUEST_SOUND = 91
        const val REQUEST_MIC = 92
    }
}
