package pl.kuchenny.budzik.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import pl.kuchenny.timer.R

/** Głos mówiący, która jest godzina — nagrania „Jest godzina …” od 0 do 23. */
object HourVoicePlayer {

    private val clips = intArrayOf(
        R.raw.godzina_00, R.raw.godzina_01, R.raw.godzina_02, R.raw.godzina_03,
        R.raw.godzina_04, R.raw.godzina_05, R.raw.godzina_06, R.raw.godzina_07,
        R.raw.godzina_08, R.raw.godzina_09, R.raw.godzina_10, R.raw.godzina_11,
        R.raw.godzina_12, R.raw.godzina_13, R.raw.godzina_14, R.raw.godzina_15,
        R.raw.godzina_16, R.raw.godzina_17, R.raw.godzina_18, R.raw.godzina_19,
        R.raw.godzina_20, R.raw.godzina_21, R.raw.godzina_22, R.raw.godzina_23
    )

    /**
     * Mówi godzinę [hour] (0–23) z głośnością [volume] (0..1), po zakończeniu
     * zwalnia odtwarzacz i woła [onDone]. Gdy nagranie nie zagra — pyka.
     */
    fun play(context: Context, hour: Int, volume: Float, onDone: () -> Unit = {}) {
        val player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        // Jak pykanie: słychać także przy wyciszonych multimediach.
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                context.resources.openRawResourceFd(clips[hour.coerceIn(0, 23)]).use {
                    setDataSource(it.fileDescriptor, it.startOffset, it.length)
                }
                prepare()
                val level = volume.coerceIn(0f, 1f)
                setVolume(level, level)
            }
        }.getOrNull()
        if (player == null) {
            ChimePlayer.play(volume, onDone)
            return
        }

        var finished = false
        val handler = Handler(Looper.getMainLooper())
        val finish = Runnable {
            if (!finished) {
                finished = true
                runCatching { player.release() }
                onDone()
            }
        }
        player.setOnCompletionListener { handler.post(finish) }
        player.setOnErrorListener { _, _, _ -> handler.post(finish); true }
        // Zapas na wypadek, gdyby odtwarzacz nie zgłosił końca.
        handler.postDelayed(finish, player.duration.coerceAtLeast(0) + RELEASE_MARGIN_MS)
        if (runCatching { player.start() }.isFailure) handler.post(finish)
    }

    private const val RELEASE_MARGIN_MS = 1_000L
}
