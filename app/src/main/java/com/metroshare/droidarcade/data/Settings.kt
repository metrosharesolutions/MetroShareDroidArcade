package com.metroshare.droidarcade.data

import android.content.SharedPreferences
import com.metroshare.droidarcade.game.Difficulty

/** Player options, persisted in SharedPreferences. */
class Settings(private val prefs: SharedPreferences) {

    var musicVolume: Int
        get() = prefs.getInt(K_MUSIC, 6)
        set(v) = prefs.edit().putInt(K_MUSIC, v.coerceIn(0, 10)).apply()

    var sfxVolume: Int
        get() = prefs.getInt(K_SFX, 8)
        set(v) = prefs.edit().putInt(K_SFX, v.coerceIn(0, 10)).apply()

    var vibration: Boolean
        get() = prefs.getBoolean(K_VIBRATION, true)
        set(v) = prefs.edit().putBoolean(K_VIBRATION, v).apply()

    var difficulty: Difficulty
        get() = Difficulty.entries.getOrElse(prefs.getInt(K_DIFFICULTY, 1)) { Difficulty.NORMAL }
        set(v) = prefs.edit().putInt(K_DIFFICULTY, v.ordinal).apply()

    var startLevel: Int
        get() = prefs.getInt(K_START_LEVEL, 1).coerceIn(1, MAX_START_LEVEL)
        set(v) = prefs.edit().putInt(K_START_LEVEL, v.coerceIn(1, MAX_START_LEVEL)).apply()

    var ghost: Boolean
        get() = prefs.getBoolean(K_GHOST, true)
        set(v) = prefs.edit().putBoolean(K_GHOST, v).apply()

    var scanlines: Boolean
        get() = prefs.getBoolean(K_SCANLINES, true)
        set(v) = prefs.edit().putBoolean(K_SCANLINES, v).apply()

    var leftHanded: Boolean
        get() = prefs.getBoolean(K_LEFT_HANDED, false)
        set(v) = prefs.edit().putBoolean(K_LEFT_HANDED, v).apply()

    var swipe: Boolean
        get() = prefs.getBoolean(K_SWIPE, true)
        set(v) = prefs.edit().putBoolean(K_SWIPE, v).apply()

    var lastInitials: String
        get() = prefs.getString(K_INITIALS, "") ?: ""
        set(v) = prefs.edit().putString(K_INITIALS, v).apply()

    companion object {
        const val MAX_START_LEVEL = 15
        private const val K_MUSIC = "music_volume"
        private const val K_SFX = "sfx_volume"
        private const val K_VIBRATION = "vibration"
        private const val K_DIFFICULTY = "difficulty"
        private const val K_START_LEVEL = "start_level"
        private const val K_GHOST = "ghost"
        private const val K_SCANLINES = "scanlines"
        private const val K_LEFT_HANDED = "left_handed"
        private const val K_SWIPE = "swipe"
        private const val K_INITIALS = "last_initials"
    }
}
