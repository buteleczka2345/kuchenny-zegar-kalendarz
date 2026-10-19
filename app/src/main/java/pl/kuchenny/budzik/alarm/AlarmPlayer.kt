package pl.kuchenny.budzik.alarm

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

/** Odtwarzanie zapętlonej melodii lub własnego dźwięku i wibracja budzika. */
class AlarmPlayer(context: Context) {

    private val appContext = context.applicationContext
    private var track: AudioTrack? = null
    private var media: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var currentVolume = 1f

    var volume: Float
        get() = currentVolume
        set(value) {
            currentVolume = value.coerceIn(0f, 1f)
            track?.let { runCatching { it.setVolume(currentVolume) } }
            media?.let { runCatching { it.setVolume(currentVolume, currentVolume) } }
        }

    /**
     * Gra w pętli własny plik (mp3, m4a, ogg, wav, flac…). Gdy pliku nie da się
     * otworzyć albo odtwarzanie się zepsuje, gra wbudowaną melodię [fallbackStyle],
     * żeby budzik zawsze zadzwonił.
     */
    fun startSound(uri: String, fallbackStyle: Int) {
        stopSound()
        val player = MediaPlayer()
        val started = try {
            player.setAudioAttributes(alarmAttributes())
            player.setDataSource(appContext, Uri.parse(uri))
            player.isLooping = true
            player.setVolume(currentVolume, currentVolume)
            player.setOnErrorListener { _, _, _ ->
                if (media === player) {
                    stopSound()
                    startMelody(fallbackStyle)
                }
                true
            }
            player.prepare()
            player.start()
            true
        } catch (exception: Exception) {
            // Plik usunięty, brak zgody albo nieobsługiwany format.
            false
        }
        if (started) {
            media = player
        } else {
            runCatching { player.release() }
            startMelody(fallbackStyle)
        }
    }

    fun startMelody(style: Int) {
        stopSound()
        val samples = MelodySynth.pcm16(style)
        val attributes = alarmAttributes()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(MelodySynth.SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val created = try {
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
        created.write(samples, 0, samples.size)
        created.setLoopPoints(0, samples.size, -1)
        created.setVolume(currentVolume)
        created.play()
        track = created
    }

    fun startVibration(strength: Int) {
        if (strength <= 0) return
        val service = appContext.getSystemService(Vibrator::class.java) ?: return
        if (!service.hasVibrator()) return
        val amplitude = (strength.coerceIn(0, 10) * 255) / 10
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createWaveform(
                VIBRATION_TIMINGS,
                intArrayOf(0, amplitude, 0),
                0
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val attributes = VibrationAttributes.Builder()
                    .setUsage(VibrationAttributes.USAGE_ALARM)
                    .build()
                service.vibrate(effect, attributes)
            } else {
                service.vibrate(effect)
            }
        } else {
            @Suppress("DEPRECATION")
            service.vibrate(VIBRATION_TIMINGS, 0)
        }
        vibrator = service
    }

    fun stop() {
        stopSound()
        vibrator?.cancel()
        vibrator = null
    }

    /** Zatrzymuje sam dźwięk, wibracja zostaje. */
    fun stopSound() {
        media?.let { player ->
            media = null
            runCatching { player.stop() }
            runCatching { player.release() }
        }
        track?.let { player ->
            track = null
            runCatching {
                if (player.playState == AudioTrack.PLAYSTATE_PLAYING) player.stop()
            }
            runCatching { player.release() }
        }
    }

    private fun alarmAttributes(): AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private companion object {
        val VIBRATION_TIMINGS = longArrayOf(0L, 700L, 500L)
    }
}
