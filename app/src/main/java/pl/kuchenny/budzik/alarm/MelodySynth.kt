package pl.kuchenny.budzik.alarm

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Melodie budzika.
 *
 * Każda melodia to krótki fragment dźwięku; odtwarzacz zapętla go w całości,
 * więc budzik dzwoni bez przerw do zatrzymania. Pierwsze melodie to sekwencje
 * prostych dźwięków, dalsze (ptaki, kogut, kukułka, telefon) są syntezowane
 * z przesuwaną wysokością i alikwotami.
 */
object MelodySynth {

    const val SAMPLE_RATE = 44_100

    /** Szczyt sygnału — taki sam dla wszystkich melodii, żeby grały podobnie głośno. */
    private const val PEAK = 0.45

    data class Note(val frequency: Double, val durationMs: Int, val gapMs: Int = 70)

    private val DELICATE = listOf(
        Note(523.25, 320),
        Note(659.25, 320),
        Note(783.99, 420),
        Note(659.25, 320)
    )
    private val CLASSIC = listOf(
        Note(880.0, 160, 120),
        Note(880.0, 160, 120),
        Note(880.0, 200, 200),
        Note(880.0, 160, 120),
        Note(880.0, 160, 300)
    )
    private val BELLS = listOf(
        Note(1046.5, 380, 80),
        Note(783.99, 380, 120),
        Note(1046.5, 380, 500)
    )
    private val MORNING = listOf(
        Note(659.25, 260),
        Note(783.99, 260),
        Note(987.77, 260),
        Note(1318.51, 420)
    )
    private val DEEP = listOf(
        Note(392.0, 420, 90),
        Note(440.0, 420, 90),
        Note(392.0, 480, 420)
    )
    private val ENERGETIC = listOf(
        Note(1174.66, 130, 60),
        Note(987.77, 130, 60),
        Note(1174.66, 130, 60),
        Note(987.77, 130, 300)
    )

    val MELODIES: List<List<Note>> = listOf(DELICATE, CLASSIC, BELLS, MORNING, DEEP, ENERGETIC)

    private val SYNTHESIZED: List<(Int) -> ShortArray> = listOf(
        ::birds,
        ::rooster,
        ::cuckoo,
        ::oldPhone
    )

    /** Liczba wszystkich melodii — musi się zgadzać z nazwami w ustawieniach. */
    val COUNT: Int = MELODIES.size + SYNTHESIZED.size

    fun pcm16(style: Int, sampleRate: Int = SAMPLE_RATE): ShortArray {
        val index = style.coerceIn(0, COUNT - 1)
        if (index >= MELODIES.size) return SYNTHESIZED[index - MELODIES.size](sampleRate)
        return notes(MELODIES[index], sampleRate)
    }

    private fun notes(notes: List<Note>, sampleRate: Int): ShortArray {
        val totalMs = notes.sumOf { it.durationMs + it.gapMs }
        val samples = ShortArray((sampleRate.toLong() * totalMs / 1_000L).toInt().coerceAtLeast(1))
        var offset = 0
        notes.forEach { note ->
            val count = (sampleRate.toLong() * note.durationMs / 1_000L).toInt()
            for (index in 0 until count) {
                val position = offset + index
                if (position >= samples.size) break
                val time = index.toDouble() / sampleRate
                val attack = (time / 0.015).coerceAtMost(1.0)
                val decay = exp(-time * 2.2)
                val value = sin(2.0 * PI * note.frequency * time) * attack * decay * PEAK
                samples[position] = toShort(value)
            }
            offset += count + (sampleRate.toLong() * note.gapMs / 1_000L).toInt()
        }
        return samples
    }

    // --- dźwięki syntezowane -------------------------------------------------

    /** Ćwierkanie: dwa ptaki — szybkie świergoty i niższe gwizdy „tiu”. */
    private fun birds(sampleRate: Int): ShortArray {
        val canvas = Canvas(sampleRate, 3.2)
        val random = Random(7)
        val harmonics = doubleArrayOf(1.0, 0.12)

        // Pierwszy ptak: seria krótkich, wysokich świergotów.
        var time = 0.05
        repeat(6) {
            val duration = 0.05 + random.nextDouble() * 0.04
            val from = 3_400.0 + random.nextDouble() * 900.0
            val to = from + 900.0 + random.nextDouble() * 700.0
            canvas.tone(time, duration, harmonics,
                pitch = { p, t -> from + (to - from) * p + 180.0 * sin(2.0 * PI * 45.0 * t) },
                level = { p, _ -> swell(p, 0.15, 0.4) })
            time += duration + 0.035 + random.nextDouble() * 0.03
        }

        // Drugi ptak: dwa opadające gwizdy.
        time += 0.35
        repeat(2) {
            canvas.tone(time, 0.28, harmonics,
                pitch = { p, _ -> 2_900.0 - 1_000.0 * p },
                level = { p, _ -> swell(p, 0.1, 0.5) * 0.8 })
            time += 0.4
        }

        // Pierwszy ptak odpowiada krótszą serią.
        time += 0.25
        repeat(4) {
            val duration = 0.045 + random.nextDouble() * 0.03
            val from = 4_200.0 + random.nextDouble() * 600.0
            canvas.tone(time, duration, harmonics,
                pitch = { p, t -> from - 1_200.0 * p + 150.0 * sin(2.0 * PI * 50.0 * t) },
                level = { p, _ -> swell(p, 0.15, 0.4) * 0.9 })
            time += duration + 0.04
        }
        return canvas.pcm16()
    }

    /** Kogut: „ku-ku-ry-kuuu” z chropowatym, bogatym w alikwoty głosem. */
    private fun rooster(sampleRate: Int): ShortArray {
        val canvas = Canvas(sampleRate, 3.4)
        val voice = doubleArrayOf(1.0, 0.75, 0.55, 0.4, 0.25, 0.16, 0.1, 0.06)
        // Szybkie drganie głośności daje chrypkę jak w prawdziwym pianiu.
        val rasp = { t: Double -> 0.75 + 0.25 * sin(2.0 * PI * 38.0 * t) }

        canvas.tone(0.00, 0.15, voice,
            pitch = { p, _ -> 520.0 + 90.0 * p },
            level = { p, t -> swell(p, 0.2, 0.3) * rasp(t) * 0.8 })
        canvas.tone(0.21, 0.15, voice,
            pitch = { p, _ -> 600.0 + 90.0 * p },
            level = { p, t -> swell(p, 0.2, 0.3) * rasp(t) * 0.85 })
        canvas.tone(0.42, 0.22, voice,
            pitch = { p, _ -> 690.0 + 110.0 * p },
            level = { p, t -> swell(p, 0.15, 0.3) * rasp(t) * 0.9 })
        canvas.tone(0.70, 1.15, voice,
            pitch = { p, t ->
                val held = 830.0 + 35.0 * sin(2.0 * PI * 6.0 * t)
                if (p < 0.75) held else held - (held - 560.0) * ((p - 0.75) / 0.25)
            },
            level = { p, t -> swell(p, 0.05, 0.3) * rasp(t) })
        return canvas.pcm16()
    }

    /** Kukułka: dwa razy „ku-ku”, miękki gwizd opadający o tercję. */
    private fun cuckoo(sampleRate: Int): ShortArray {
        val canvas = Canvas(sampleRate, 2.4)
        val whistle = doubleArrayOf(1.0, 0.08, 0.03)
        listOf(0.0, 1.0).forEach { start ->
            canvas.tone(start, 0.28, whistle,
                pitch = { p, _ -> 670.0 - 20.0 * p },
                level = { p, _ -> swell(p, 0.12, 0.4) })
            canvas.tone(start + 0.38, 0.42, whistle,
                pitch = { p, _ -> 535.0 - 15.0 * p },
                level = { p, _ -> swell(p, 0.1, 0.5) * 0.9 })
        }
        return canvas.pcm16()
    }

    /** Stary telefon z dzwonkiem: młoteczek uderza w dwa dzwonki 25 razy na sekundę. */
    private fun oldPhone(sampleRate: Int): ShortArray {
        val canvas = Canvas(sampleRate, 3.0)
        val bell = doubleArrayOf(1.0, 0.45, 0.3, 0.15)
        val hammer = { t: Double ->
            val stroke = (t * 25.0) % 1.0
            exp(-stroke * 6.0)
        }
        listOf(0.0, 1.1).forEach { start ->
            canvas.tone(start, 0.9, bell,
                pitch = { _, t -> if (((t * 25.0).toInt() % 2) == 0) 1_180.0 else 1_330.0 },
                level = { p, t -> swell(p, 0.02, 0.05) * hammer(t) })
        }
        return canvas.pcm16()
    }

    // --- pykanie o pełnej godzinie ------------------------------------------

    private const val CHIME_RING_SEC = 0.6

    /** Pyknięcie o pełnej godzinie: jedno krótkie, dzwoneczkowe uderzenie. */
    fun chime(sampleRate: Int = SAMPLE_RATE): ShortArray {
        val canvas = Canvas(sampleRate, 0.02 + CHIME_RING_SEC)
        canvas.tone(0.02, CHIME_RING_SEC, doubleArrayOf(1.0, 0.35, 0.18, 0.08),
            pitch = { _, _ -> 1_318.51 },
            level = { _, t -> (t / 0.004).coerceAtMost(1.0) * exp(-t * 9.0) })
        return canvas.pcm16()
    }

    /** Łagodne wejście i wyjście: [attack] i [release] to części czasu dźwięku. */
    private fun swell(progress: Double, attack: Double, release: Double): Double {
        val rise = (progress / attack).coerceAtMost(1.0)
        val fall = ((1.0 - progress) / release).coerceAtMost(1.0)
        return (rise * fall).coerceIn(0.0, 1.0)
    }

    private fun toShort(value: Double): Short = (value * Short.MAX_VALUE)
        .toInt()
        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
        .toShort()

    /** Bufor, do którego dopisuje się dźwięki o zmiennej wysokości. */
    private class Canvas(private val sampleRate: Int, seconds: Double) {

        private val data = DoubleArray((sampleRate * seconds).toInt().coerceAtLeast(1))

        /**
         * Dopisuje dźwięk od [startSec] przez [durationSec]. [pitch] i [level]
         * dostają postęp dźwięku 0..1 i czas od jego początku w sekundach.
         */
        fun tone(
            startSec: Double,
            durationSec: Double,
            harmonics: DoubleArray,
            pitch: (Double, Double) -> Double,
            level: (Double, Double) -> Double
        ) {
            val start = (startSec * sampleRate).toInt()
            val count = (durationSec * sampleRate).toInt().coerceAtLeast(1)
            var phase = 0.0
            for (index in 0 until count) {
                val position = start + index
                if (position >= data.size) break
                val progress = index.toDouble() / count
                val time = index.toDouble() / sampleRate
                phase += 2.0 * PI * pitch(progress, time) / sampleRate
                var value = 0.0
                for (harmonic in harmonics.indices) {
                    value += harmonics[harmonic] * sin(phase * (harmonic + 1))
                }
                data[position] += value * level(progress, time)
            }
        }

        fun pcm16(): ShortArray {
            val peak = data.maxOf { abs(it) }
            val scale = if (peak > 0.0) PEAK / peak else 0.0
            return ShortArray(data.size) { toShort(data[it] * scale) }
        }
    }
}
