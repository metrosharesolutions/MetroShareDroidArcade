package com.metroshare.droidarcade.data

import android.content.SharedPreferences

data class ScoreEntry(val name: String, val score: Long, val level: Int, val difficulty: Char)

/** Top-10 arcade high score table, stored on the device. */
class Leaderboard(private val prefs: SharedPreferences) {

    var entries: List<ScoreEntry> = load()
        private set

    val topScore: Long get() = entries.firstOrNull()?.score ?: 0L

    fun qualifies(score: Long): Boolean =
        score > 0 && (entries.size < SIZE || score > entries.last().score)

    /** Inserts the entry and returns its 0-based rank, or -1 if it did not make the table. */
    fun add(entry: ScoreEntry): Int {
        if (!qualifies(entry.score)) return -1
        val list = entries.toMutableList()
        var rank = list.indexOfFirst { entry.score > it.score }
        if (rank < 0) rank = list.size
        list.add(rank, entry.copy(name = sanitize(entry.name)))
        entries = list.take(SIZE)
        save()
        return rank
    }

    fun reset() {
        entries = DEFAULTS
        save()
    }

    private fun load(): List<ScoreEntry> {
        val raw = prefs.getString(KEY, null) ?: return DEFAULTS
        val parsed = raw.lines().mapNotNull { line ->
            val p = line.split('|')
            if (p.size < 4) return@mapNotNull null
            val score = p[1].toLongOrNull() ?: return@mapNotNull null
            ScoreEntry(sanitize(p[0]), score, p[2].toIntOrNull() ?: 1, p[3].firstOrNull() ?: 'N')
        }
        return parsed.sortedByDescending { it.score }.take(SIZE).ifEmpty { DEFAULTS }
    }

    private fun save() {
        val raw = entries.joinToString("\n") { "${it.name}|${it.score}|${it.level}|${it.difficulty}" }
        prefs.edit().putString(KEY, raw).apply()
    }

    companion object {
        const val SIZE = 10
        private const val KEY = "high_scores"

        fun sanitize(name: String): String {
            val clean = name.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' || it == '.' || it == ' ' }
            return clean.take(3).padEnd(3, ' ')
        }

        /** Classic cabinet-style default table so there is always something to beat. */
        val DEFAULTS = listOf(
            ScoreEntry("MSD", 50_000, 12, 'N'),
            ScoreEntry("ACE", 40_000, 10, 'N'),
            ScoreEntry("RAD", 30_000, 9, 'N'),
            ScoreEntry("GEM", 25_000, 8, 'N'),
            ScoreEntry("ZAP", 20_000, 7, 'N'),
            ScoreEntry("MAX", 15_000, 6, 'N'),
            ScoreEntry("PIX", 10_000, 5, 'N'),
            ScoreEntry("CPU", 7_500, 4, 'N'),
            ScoreEntry("DOT", 5_000, 3, 'N'),
            ScoreEntry("BIT", 2_500, 2, 'N'),
        )
    }
}
