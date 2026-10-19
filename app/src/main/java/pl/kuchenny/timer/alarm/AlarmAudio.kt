package pl.kuchenny.timer.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import java.io.IOException

/**
 * Odtwarzanie dzwonka i wibracja o zadanej sile.
 *
 * Siła wibracji przychodzi z ustawień jako amplituda 0–255; zero oznacza
 * wibrację wyłączoną.
 */
class AlarmAudio(context: Context) {

    private val appContext = context.applicationContext
    private var audioTrack: AudioTrack? = null
    private var mediaPlayer: MediaPlayer? = null
    private var trackEndsAt = 0L
    private var currentVolume = 1f

    /** Wywoływane, gdy wybrany plik jest nieczytelny — serwis wraca do dzwonka wbudowanego. */
    var onPlaybackError: (() -> Unit)? = null

    /** Amplituda wibracji 0–255 (0 = brak wibracji). */
    var vibrationAmplitude: Int = 255
        set(value) {
            field = value.coerceIn(0, 255)
        }

    var volume: Float
        get() = currentVolume
        set(value) {
            currentVolume = value.coerceIn(0f, 1f)
            mediaPlayer?.let { player ->
                runCatching { player.setVolume(currentVolume, currentVolume) }
            }
            audioTrack?.let { track ->
                runCatching { track.setVolume(currentVolume) }
            }
        }

    /**
     * Czy odtwarzacz jest zajęty (przygotowuje albo gra dźwięk).
     * Samo `MediaPlayer.isPlaying` bywa chwilę fałszywe po `prepareAsync`.
     */
    val isPlaying: Boolean
        get() = mediaPlayer != null || System.currentTimeMillis() < trackEndsAt

    fun playBell(
        frequencies: DoubleArray,
        durationMs: Long = BellSynth.BELL_DURATION_MS,
        decayPerSecond: Double
    ) {
        stop()
        val samples = BellSynth.pcm16(
            frequencies = frequencies,
            durationMs = durationMs,
            decayPerSecond = decayPerSecond
        )
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(BellSynth.SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(samples.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
        } catch (exception: IllegalArgumentException) {
            return
        } catch (exception: IllegalStateException) {
            return
        }
        track.write(samples, 0, samples.size)
        track.setVolume(currentVolume)
        track.play()
        audioTrack = track
        trackEndsAt = System.currentTimeMillis() + durationMs
        vibrate()
    }

    fun playFile(uri: Uri, looping: Boolean) {
        stop()
        val player = MediaPlayer()
        mediaPlayer = player
        try {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            player.setDataSource(appContext, uri)
            player.isLooping = looping
            player.setVolume(currentVolume, currentVolume)
            player.setOnPreparedListener { prepared ->
                if (mediaPlayer !== prepared) return@setOnPreparedListener
                try {
                    prepared.start()
                    vibrate()
                } catch (exception: IllegalStateException) {
                    handleFailure(prepared)
                }
            }
            player.setOnCompletionListener { completed ->
                if (mediaPlayer === completed) {
                    mediaPlayer = null
                    runCatching { completed.release() }
                }
            }
            player.setOnErrorListener { failed, _, _ ->
                handleFailure(failed)
                true
            }
            player.prepareAsync()
        } catch (exception: IOException) {
            handleFailure(player)
        } catch (exception: IllegalArgumentException) {
            handleFailure(player)
        } catch (exception: SecurityException) {
            handleFailure(player)
        }
    }

    fun stop() {
        mediaPlayer?.let { player ->
            mediaPlayer = null
            player.setOnCompletionListener(null)
            player.setOnPreparedListener(null)
            player.setOnErrorListener(null)
            runCatching { player.release() }
        }
        audioTrack?.let { track ->
            runCatching {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
            }
            runCatching { track.release() }
        }
        audioTrack = null
        trackEndsAt = 0L
    }

    fun vibrate() {
        if (vibrationAmplitude <= 0) return
        val vibrator = appContext.getSystemService(Vibrator::class.java) ?: return
        if (!vibrator.hasVibrator()) return

        // Dwa stuknięcia o zadanej sile; kategoria „alarm” pozwala im przejść
        // tam, gdzie zwykłe wibracje są wyciszone.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val amplitudes = intArrayOf(0, vibrationAmplitude, 0, vibrationAmplitude)
            val effect = VibrationEffect.createWaveform(VIBRATION_TIMINGS, amplitudes, -1)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val attributes = VibrationAttributes.Builder()
                    .setUsage(VibrationAttributes.USAGE_ALARM)
                    .build()
                vibrator.vibrate(effect, attributes)
            } else {
                vibrator.vibrate(effect)
            }
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(VIBRATION_TIMINGS, -1)
        }
    }

    fun cancelVibration() {
        appContext.getSystemService(Vibrator::class.java)?.cancel()
    }

    private fun handleFailure(player: MediaPlayer) {
        if (mediaPlayer === player) {
            mediaPlayer = null
            runCatching { player.release() }
        }
        Toast.makeText(
            appContext,
            "Nie można odtworzyć wybranego pliku — dzwonię dzwonkiem wbudowanym.",
            Toast.LENGTH_LONG
        ).show()
        onPlaybackError?.invoke()
    }

    private companion object {
        val VIBRATION_TIMINGS = longArrayOf(0L, 260L, 140L, 260L)
    }
}
