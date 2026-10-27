package com.metroshare.droidarcade.data

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Short rumble effects, respecting the vibration setting. */
class Haptics(context: Context, private val settings: Settings) {

    private val vibrator: Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (_: Exception) {
        null
    }

    fun buzz(ms: Long, strength: Int = VibrationEffect.DEFAULT_AMPLITUDE) {
        if (!settings.vibration) return
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        try {
            val amp = if (v.hasAmplitudeControl()) strength else VibrationEffect.DEFAULT_AMPLITUDE
            v.vibrate(VibrationEffect.createOneShot(ms, amp))
        } catch (_: Exception) {
        }
    }

    fun tick() = buzz(8, 60)
    fun bump() = buzz(14, 110)
    fun thump(chain: Int) = buzz((18L + chain * 10L).coerceAtMost(70L), (120 + chain * 30).coerceAtMost(255))
    fun crash() = buzz(240, 255)
}
