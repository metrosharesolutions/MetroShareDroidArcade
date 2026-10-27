package com.metroshare.droidarcade.audio

import java.io.ByteArrayOutputStream
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/**
 * A tiny chiptune synthesiser. Everything the game plays is generated here at start-up from
 * square, pulse, triangle and noise channels, so the APK ships with no audio files at all.
 */
object Synth {
    const val RATE = 22050

    enum class Wave { SQUARE, PULSE25, PULSE12, TRIANGLE, SAW, SINE, NOISE }

    fun midi(n: Float): Float = 440f * 2f.pow((n - 69f) / 12f)
    fun midi(n: Int): Float = midi(n.toFloat())

    fun buffer(seconds: Float) = FloatArray((seconds * RATE).toInt().coerceAtLeast(1))

    private var noiseSeed = 0x1234567

    private fun nextNoise(): Float {
        // xorshift: cheap, deterministic white noise
        var x = noiseSeed
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        noiseSeed = x
        return (x and 0xFFFF) / 32768f - 1f
    }

    /**
     * Mixes one note into [buf].
     * @param f0 start frequency, [f1] end frequency (pitch sweep), [decay] exponential fade rate
     * @param vibrato depth in semitones (6 Hz)
     */
    fun note(
        buf: FloatArray,
        start: Float,
        dur: Float,
        f0: Float,
        f1: Float = f0,
        wave: Wave = Wave.SQUARE,
        vol: Float = 0.3f,
        attack: Float = 0.004f,
        release: Float = 0.03f,
        decay: Float = 0f,
        vibrato: Float = 0f,
    ) {
        val s0 = (start * RATE).toInt()
        val n = (dur * RATE).toInt()
        var phase = 0f
        var held = 0f
        for (i in 0 until n) {
            val idx = s0 + i
            if (idx < 0) continue
            if (idx >= buf.size) break
            val t = i.toFloat() / RATE
            val p = t / dur
            var f = if (f1 == f0) f0 else f0 * (f1 / f0).pow(p)
            if (vibrato > 0f && t > 0.08f) f *= 2f.pow(vibrato * sin(2f * PI.toFloat() * 6f * t) / 12f)
            val prev = phase
            phase += f / RATE
            if (phase >= 1f) phase -= phase.toInt()
            val s = when (wave) {
                Wave.SQUARE -> if (phase < 0.5f) 1f else -1f
                Wave.PULSE25 -> if (phase < 0.25f) 1f else -1f
                Wave.PULSE12 -> if (phase < 0.125f) 1f else -1f
                Wave.TRIANGLE -> 4f * abs(phase - 0.5f) - 1f
                Wave.SAW -> 2f * phase - 1f
                Wave.SINE -> sin(2f * PI.toFloat() * phase)
                Wave.NOISE -> {
                    if (phase < prev) held = nextNoise()
                    held
                }
            }
            var env = 1f
            if (t < attack) env = t / attack
            val tr = dur - t
            if (tr < release) env *= (tr / release).coerceAtLeast(0f)
            if (decay > 0f) env *= exp(-decay * t)
            buf[idx] += s * vol * env
        }
    }

    /** Soft-clips and converts to 16-bit PCM. */
    fun toPcm(buf: FloatArray, gain: Float = 1f): ShortArray {
        val out = ShortArray(buf.size)
        for (i in buf.indices) {
            var x = buf[i] * gain
            // gentle saturation keeps loud chords from wrapping
            x = if (x > 1f) 1f else if (x < -1f) -1f else x - x * x * x / 3f * 0.3f
            out[i] = (x * 32000f).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    fun toWav(pcm: ShortArray): ByteArray {
        val dataLen = pcm.size * 2
        val out = ByteArrayOutputStream(44 + dataLen)
        fun i32(v: Int) {
            out.write(v and 0xFF); out.write((v shr 8) and 0xFF)
            out.write((v shr 16) and 0xFF); out.write((v shr 24) and 0xFF)
        }
        fun i16(v: Int) {
            out.write(v and 0xFF); out.write((v shr 8) and 0xFF)
        }
        out.write("RIFF".toByteArray()); i32(36 + dataLen)
        out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray()); i32(16); i16(1); i16(1)
        i32(RATE); i32(RATE * 2); i16(2); i16(16)
        out.write("data".toByteArray()); i32(dataLen)
        val bytes = ByteArray(dataLen)
        for (i in pcm.indices) {
            val v = pcm[i].toInt()
            bytes[i * 2] = (v and 0xFF).toByte()
            bytes[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
        }
        out.write(bytes)
        return out.toByteArray()
    }
}
