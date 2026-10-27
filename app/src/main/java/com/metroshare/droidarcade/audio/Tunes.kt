package com.metroshare.droidarcade.audio

import com.metroshare.droidarcade.audio.Synth.Wave
import com.metroshare.droidarcade.audio.Synth.midi
import com.metroshare.droidarcade.audio.Synth.note

/** Sound effects, built from Synth primitives. */
object Sfx {
    fun move() = Synth.buffer(0.05f).also {
        note(it, 0f, 0.035f, 540f, 480f, Wave.PULSE25, 0.22f, release = 0.01f)
    }

    fun rotate() = Synth.buffer(0.08f).also {
        note(it, 0f, 0.07f, 700f, 1150f, Wave.PULSE25, 0.2f, decay = 12f)
    }

    fun land() = Synth.buffer(0.14f).also {
        note(it, 0f, 0.12f, 190f, 65f, Wave.TRIANGLE, 0.7f, decay = 18f)
        note(it, 0f, 0.05f, 2200f, 900f, Wave.NOISE, 0.12f, decay = 40f)
    }

    fun drop() = Synth.buffer(0.2f).also {
        note(it, 0f, 0.17f, 950f, 150f, Wave.SQUARE, 0.2f, decay = 7f)
        note(it, 0.02f, 0.14f, 5000f, 400f, Wave.NOISE, 0.12f, decay = 18f)
    }

    fun clear() = Synth.buffer(0.32f).also {
        val notes = intArrayOf(72, 76, 79, 84)
        for ((k, n) in notes.withIndex()) {
            note(it, k * 0.045f, 0.12f, midi(n), wave = Wave.PULSE25, vol = 0.2f, decay = 14f)
        }
        note(it, 0.12f, 0.18f, 9000f, 6000f, Wave.NOISE, 0.05f, decay = 20f)
    }

    fun magic() = Synth.buffer(0.9f).also {
        var t = 0f
        for (oct in 0..2) for (n in intArrayOf(0, 4, 7, 11)) {
            note(it, t, 0.15f, midi(64 + oct * 12 + n), wave = Wave.PULSE12, vol = 0.18f, decay = 10f)
            t += 0.045f
        }
        note(it, 0f, 0.85f, 3000f, 9000f, Wave.NOISE, 0.05f, decay = 3f)
        note(it, 0.5f, 0.35f, midi(100), wave = Wave.SINE, vol = 0.15f, decay = 6f, vibrato = 0.6f)
    }

    fun levelUp() = Synth.buffer(1.0f).also {
        val notes = intArrayOf(67, 72, 76, 79)
        for ((k, n) in notes.withIndex()) {
            note(it, k * 0.09f, 0.1f, midi(n), wave = Wave.SQUARE, vol = 0.16f)
            note(it, k * 0.09f, 0.1f, midi(n - 12), wave = Wave.TRIANGLE, vol = 0.3f)
        }
        note(it, 0.36f, 0.55f, midi(84), wave = Wave.SQUARE, vol = 0.16f, decay = 2f, vibrato = 0.3f)
        note(it, 0.36f, 0.55f, midi(88), wave = Wave.PULSE25, vol = 0.12f, decay = 2f, vibrato = 0.3f)
        note(it, 0.36f, 0.55f, midi(60), wave = Wave.TRIANGLE, vol = 0.35f, decay = 2f)
    }

    fun gameOver() = Synth.buffer(1.7f).also {
        val notes = intArrayOf(67, 66, 65)
        for ((k, n) in notes.withIndex()) {
            note(it, k * 0.28f, 0.26f, midi(n), wave = Wave.SQUARE, vol = 0.18f, decay = 3f)
            note(it, k * 0.28f, 0.26f, midi(n - 24), wave = Wave.TRIANGLE, vol = 0.35f)
        }
        note(it, 0.84f, 0.8f, midi(64), midi(62), Wave.SQUARE, 0.18f, decay = 2f, vibrato = 0.5f)
        note(it, 0.84f, 0.8f, midi(40), midi(38), Wave.TRIANGLE, 0.4f, decay = 2f)
    }

    /** The classic "coin" blip. */
    fun select() = Synth.buffer(0.36f).also {
        note(it, 0f, 0.06f, midi(83), wave = Wave.SQUARE, vol = 0.16f, release = 0.005f)
        note(it, 0.06f, 0.28f, midi(88), wave = Wave.SQUARE, vol = 0.16f, decay = 9f)
    }

    fun back() = Synth.buffer(0.14f).also {
        note(it, 0f, 0.06f, midi(76), wave = Wave.PULSE25, vol = 0.18f)
        note(it, 0.06f, 0.07f, midi(71), wave = Wave.PULSE25, vol = 0.18f, decay = 10f)
    }

    fun start() = Synth.buffer(0.7f).also {
        note(it, 0f, 0.14f, 260f, 1100f, Wave.SQUARE, 0.14f)
        note(it, 0.15f, 0.14f, 520f, 2200f, Wave.SQUARE, 0.14f)
        note(it, 0.32f, 0.06f, midi(83), wave = Wave.SQUARE, vol = 0.16f)
        note(it, 0.38f, 0.3f, midi(88), wave = Wave.SQUARE, vol = 0.16f, decay = 8f)
    }

    fun allClear() = Synth.buffer(1.2f).also {
        val up = intArrayOf(60, 64, 67, 72, 76, 79, 84, 88, 91, 96)
        for ((k, n) in up.withIndex()) {
            note(it, k * 0.05f, 0.14f, midi(n), wave = Wave.PULSE25, vol = 0.16f, decay = 8f)
        }
        note(it, 0.5f, 0.65f, midi(84), wave = Wave.SQUARE, vol = 0.14f, decay = 3f, vibrato = 0.4f)
        note(it, 0.5f, 0.65f, midi(48), wave = Wave.TRIANGLE, vol = 0.35f, decay = 3f)
    }

    fun tick() = Synth.buffer(0.03f).also {
        note(it, 0f, 0.02f, 1500f, wave = Wave.SQUARE, vol = 0.12f, release = 0.005f)
    }

    fun danger() = Synth.buffer(0.3f).also {
        note(it, 0f, 0.1f, 880f, wave = Wave.PULSE25, vol = 0.12f)
        note(it, 0.14f, 0.1f, 660f, wave = Wave.PULSE25, vol = 0.12f)
    }

    fun pause() = Synth.buffer(0.25f).also {
        note(it, 0f, 0.08f, midi(84), wave = Wave.SQUARE, vol = 0.14f)
        note(it, 0.1f, 0.12f, midi(79), wave = Wave.SQUARE, vol = 0.14f, decay = 8f)
    }
}

/** Looping background music, composed as data and rendered by the synth. */
object Tunes {
    private class Melody(vararg val notes: IntArray) // each: [bar, step, midi, lengthInSteps]

    private fun n(bar: Int, step: Int, midi: Int, len: Int) = intArrayOf(bar, step, midi, len)

    /** Upbeat in-game loop: Am - F - C - G in A minor at 140 BPM. */
    fun game(): ShortArray {
        val bpm = 140f
        val roots = intArrayOf(45, 41, 48, 43, 45, 41, 48, 43)
        val minor = booleanArrayOf(true, false, false, false, true, false, false, false)
        val lead = Melody(
            n(0, 0, 69, 2), n(0, 2, 72, 2), n(0, 4, 76, 4), n(0, 8, 74, 2), n(0, 10, 72, 2), n(0, 12, 74, 4),
            n(1, 0, 72, 2), n(1, 2, 69, 2), n(1, 4, 72, 4), n(1, 8, 77, 4), n(1, 12, 76, 4),
            n(2, 0, 76, 2), n(2, 2, 79, 2), n(2, 4, 76, 2), n(2, 6, 74, 2), n(2, 8, 72, 4), n(2, 12, 67, 4),
            n(3, 0, 71, 4), n(3, 4, 74, 4), n(3, 8, 79, 7),
            n(4, 0, 81, 2), n(4, 2, 79, 2), n(4, 4, 76, 4), n(4, 8, 72, 2), n(4, 10, 74, 2), n(4, 12, 76, 4),
            n(5, 0, 77, 4), n(5, 4, 76, 2), n(5, 6, 74, 2), n(5, 8, 72, 8),
            n(6, 0, 72, 2), n(6, 2, 74, 2), n(6, 4, 76, 2), n(6, 6, 79, 2), n(6, 8, 84, 4), n(6, 12, 79, 4),
            n(7, 0, 83, 4), n(7, 4, 79, 4), n(7, 8, 74, 4), n(7, 12, 71, 4),
        )
        return render(bpm, roots, minor, lead, drums = true, arpPattern = intArrayOf(0, 1, 2, 3), arpWave = Wave.PULSE12)
    }

    /** Dreamy title loop: F - G - Em - Am at 112 BPM. */
    fun menu(): ShortArray {
        val bpm = 112f
        val roots = intArrayOf(41, 43, 40, 45, 41, 43, 40, 45)
        val minor = booleanArrayOf(false, false, true, true, false, false, true, true)
        val lead = Melody(
            n(0, 0, 72, 4), n(0, 4, 76, 4), n(0, 8, 77, 8),
            n(1, 0, 79, 6), n(1, 6, 77, 2), n(1, 8, 76, 4), n(1, 12, 74, 4),
            n(2, 0, 76, 4), n(2, 4, 71, 4), n(2, 8, 72, 4), n(2, 12, 74, 4),
            n(3, 0, 76, 12),
            n(4, 0, 72, 4), n(4, 4, 76, 4), n(4, 8, 81, 8),
            n(5, 0, 79, 4), n(5, 4, 77, 4), n(5, 8, 76, 4), n(5, 12, 74, 4),
            n(6, 0, 76, 4), n(6, 4, 79, 4), n(6, 8, 83, 8),
            n(7, 0, 81, 14),
        )
        return render(bpm, roots, minor, lead, drums = false, arpPattern = intArrayOf(0, 1, 2, 3, 2, 1), arpWave = Wave.PULSE25)
    }

    private fun render(
        bpm: Float,
        roots: IntArray,
        minor: BooleanArray,
        lead: Melody,
        drums: Boolean,
        arpPattern: IntArray,
        arpWave: Wave,
    ): ShortArray {
        val step = 60f / bpm / 4f
        val bars = roots.size
        val length = bars * 16 * step
        val tail = 1.0f
        val buf = Synth.buffer(length + tail)

        for (bar in 0 until bars) {
            val root = roots[bar]
            val third = if (minor[bar]) 3 else 4
            val tones = intArrayOf(0, third, 7, 12)
            val barStart = bar * 16 * step

            // Bass: driving octave eighths on the triangle channel.
            for (e in 0 until 8) {
                val m = root + if (e % 2 == 0) 0 else 12
                note(buf, barStart + e * 2 * step, step * 1.7f, midi(m), wave = Wave.TRIANGLE, vol = 0.42f, release = 0.02f)
            }
            // Arpeggio: sixteenth notes walking the chord.
            for (s in 0 until 16) {
                val tone = tones[arpPattern[s % arpPattern.size]]
                note(buf, barStart + s * step, step * 0.9f, midi(root + 24 + tone), wave = arpWave, vol = 0.07f, decay = 9f)
            }
            if (drums) {
                for (s in 0 until 16) {
                    val t = barStart + s * step
                    if (s == 0 || s == 8 || (s == 6 && bar % 2 == 1) || s == 10) {
                        note(buf, t, 0.13f, 160f, 42f, Wave.SINE, 0.75f, attack = 0.001f, decay = 16f)
                    }
                    if (s == 4 || s == 12) {
                        note(buf, t, 0.15f, 3200f, 2200f, Wave.NOISE, 0.26f, attack = 0.001f, decay = 20f)
                        note(buf, t, 0.07f, 230f, 140f, Wave.TRIANGLE, 0.3f, attack = 0.001f, decay = 25f)
                    }
                    if (s % 2 == 1) {
                        note(buf, t, 0.03f, 9000f, wave = Wave.NOISE, vol = 0.06f, attack = 0.001f, decay = 70f)
                    }
                }
            } else {
                for (s in intArrayOf(0, 8)) {
                    note(buf, barStart + s * step, 0.12f, 120f, 45f, Wave.SINE, 0.45f, attack = 0.001f, decay = 18f)
                }
                for (s in intArrayOf(4, 12)) {
                    note(buf, barStart + s * step, 0.05f, 8000f, wave = Wave.NOISE, vol = 0.05f, attack = 0.001f, decay = 50f)
                }
            }
        }
        for (ev in lead.notes) {
            val start = (ev[0] * 16 + ev[1]) * step
            val dur = ev[3] * step * 0.95f
            note(buf, start, dur, midi(ev[2]), wave = Wave.SQUARE, vol = 0.12f, release = 0.04f, decay = 0.6f, vibrato = 0.25f)
            // A quiet echo an eighth later gives it that big cabinet sound.
            note(buf, start + step * 3, dur, midi(ev[2]), wave = Wave.PULSE25, vol = 0.035f, release = 0.04f, decay = 1.2f)
        }

        // Fold the tail back onto the start so the loop is seamless.
        val loopLen = (length * Synth.RATE).toInt()
        for (i in loopLen until buf.size) {
            val j = i - loopLen
            if (j < loopLen) buf[j] += buf[i]
        }
        return Synth.toPcm(buf.copyOf(loopLen), gain = 0.9f)
    }
}
