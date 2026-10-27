package pl.kuchenny.budzik.alarm

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper

/** Jednorazowe odegranie pyknięcia o pełnej godzinie. */
object ChimePlayer {

    /**
     * Gra jedno pyknięcie z głośnością [volume] (0..1) i po zakończeniu
     * zwalnia odtwarzacz, a potem woła [onDone]. Zwraca czas trwania w ms.
     */
    fun play(volume: Float, onDone: () -> Unit = {}): Long {
        val samples = MelodySynth.chime()
        val durationMs = samples.size * 1_000L / MelodySynth.SAMPLE_RATE
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        // Jak budzik: pyka także przy wyciszonych multimediach.
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(MelodySynth.SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(samples.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
        } catch (exception: RuntimeException) {
            onDone()
            return 0L
        }
        val started = runCatching {
            track.write(samples, 0, samples.size)
            track.setVolume(volume.coerceIn(0f, 1f))
            track.play()
        }.isSuccess
        if (!started) {
            runCatching { track.release() }
            onDone()
            return 0L
        }
        Handler(Looper.getMainLooper()).postDelayed({
            runCatching { track.stop() }
            runCatching { track.release() }
            onDone()
        }, durationMs + RELEASE_MARGIN_MS)
        return durationMs
    }

    private const val RELEASE_MARGIN_MS = 300L
}
