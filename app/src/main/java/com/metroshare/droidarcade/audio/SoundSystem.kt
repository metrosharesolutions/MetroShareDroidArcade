package com.metroshare.droidarcade.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.SoundPool
import android.util.Log
import com.metroshare.droidarcade.data.Settings
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

enum class Sound(val make: () -> FloatArray) {
    MOVE(Sfx::move),
    ROTATE(Sfx::rotate),
    LAND(Sfx::land),
    DROP(Sfx::drop),
    CLEAR(Sfx::clear),
    MAGIC(Sfx::magic),
    LEVEL_UP(Sfx::levelUp),
    GAME_OVER(Sfx::gameOver),
    SELECT(Sfx::select),
    BACK(Sfx::back),
    START(Sfx::start),
    ALL_CLEAR(Sfx::allClear),
    TICK(Sfx::tick),
    DANGER(Sfx::danger),
    PAUSE(Sfx::pause),
}

enum class Song { MENU, GAME }

/**
 * Plays generated chiptune effects through a SoundPool and streams the looping music on its own
 * thread. Synthesis happens in the background at start-up; anything requested before it is ready
 * is silently skipped.
 */
class SoundSystem(context: Context, private val settings: Settings) {

    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private val pool = SoundPool.Builder().setMaxStreams(10).setAudioAttributes(attrs).build()
    private val ids = IntArray(Sound.entries.size)
    private val loadedIds: MutableSet<Int> = ConcurrentHashMap.newKeySet()
    private val cacheDir = File(context.cacheDir, "sfx")

    @Volatile private var songs: Map<Song, ShortArray> = emptyMap()
    @Volatile private var wanted: Song? = null
    @Volatile private var paused = false
    @Volatile private var quit = false
    private var musicThread: Thread? = null

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) loadedIds.add(sampleId)
        }
        Thread({ generate() }, "synth").apply { priority = Thread.MIN_PRIORITY }.start()
    }

    private fun generate() {
        try {
            cacheDir.mkdirs()
            for (s in Sound.entries) {
                val f = File(cacheDir, "${s.name.lowercase()}_v$CACHE_VERSION.wav")
                if (!f.exists()) f.writeBytes(Synth.toWav(Synth.toPcm(s.make())))
                ids[s.ordinal] = pool.load(f.absolutePath, 1)
            }
            songs = mapOf(Song.MENU to Tunes.menu(), Song.GAME to Tunes.game())
            ensureMusicThread()
        } catch (e: Exception) {
            Log.w(TAG, "Audio generation failed", e)
        }
    }

    private fun volumeCurve(v: Int) = (v / 10f).pow(1.6f)

    fun play(sound: Sound, rate: Float = 1f, gain: Float = 1f) {
        val id = ids[sound.ordinal]
        if (id == 0 || id !in loadedIds) return
        val vol = (volumeCurve(settings.sfxVolume) * gain).coerceIn(0f, 1f)
        if (vol <= 0f) return
        pool.play(id, vol, vol, 1, 0, rate.coerceIn(0.5f, 2f))
    }

    fun playSong(song: Song?) {
        wanted = song
        ensureMusicThread()
    }

    fun setPaused(p: Boolean) {
        paused = p
        if (p) pool.autoPause() else pool.autoResume()
    }

    fun release() {
        quit = true
        musicThread?.interrupt()
        pool.release()
    }

    @Synchronized
    private fun ensureMusicThread() {
        if (musicThread != null || songs.isEmpty() || quit) return
        musicThread = Thread({ musicLoop() }, "music").apply { start() }
    }

    private fun musicLoop() {
        val minBuf = AudioTrack.getMinBufferSize(Synth.RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(Synth.RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(max(minBuf, CHUNK * 4))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (e: Exception) {
            Log.w(TAG, "No music track", e)
            return
        }
        var current: ShortArray? = null
        var pos = 0
        try {
            while (!quit) {
                val song = wanted
                val data = if (paused || song == null) null else songs[song]
                val vol = volumeCurve(settings.musicVolume)
                if (data == null || vol <= 0f) {
                    if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        track.pause()
                        track.flush()
                    }
                    if (data == null) current = null
                    Thread.sleep(40)
                    continue
                }
                if (data !== current) {
                    current = data
                    pos = 0
                    track.pause()
                    track.flush()
                }
                track.setVolume(vol)
                if (track.playState != AudioTrack.PLAYSTATE_PLAYING) track.play()
                val n = min(CHUNK, data.size - pos)
                val written = track.write(data, pos, n)
                if (written < 0) break
                pos += written
                if (pos >= data.size) pos = 0
            }
        } catch (_: InterruptedException) {
        } finally {
            try {
                track.stop()
            } catch (_: Exception) {
            }
            track.release()
        }
    }

    companion object {
        private const val TAG = "SoundSystem"
        private const val CHUNK = 1024
        private const val CACHE_VERSION = 1
    }
}
