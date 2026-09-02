package com.jesperhaafkes.caster.ui.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.util.concurrent.ConcurrentHashMap
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * The cues the games play. Every one is synthesised at start-up rather than
 * shipped as an audio file, so the app carries no media assets and the tones
 * stay tweakable in one place.
 */
enum class Tone {
    /** Uppercut's go-signal: the one sound that has to cut through a room. */
    CUE,

    /** Hot Potato's fuse tick. */
    TICK,

    /** Hot Potato's detonation. */
    BOOM,

    /** A player got out safe. */
    SAFE,

    /** A player missed their window. */
    MISS,

    /** A finger landed on the glass. */
    PLACE,

    /** A round resolved: here is your answer. */
    REVEAL,

    /** Countdown pip. */
    PIP;

    internal val duration: Double
        get() = when (this) {
            CUE -> 0.32
            TICK -> 0.045
            BOOM -> 0.85
            SAFE -> 0.22
            MISS -> 0.30
            PLACE -> 0.06
            REVEAL -> 0.55
            PIP -> 0.10
        }

    /** Start and end frequency in hertz; the tone sweeps between them. */
    internal val sweep: Pair<Double, Double>
        get() = when (this) {
            CUE -> 784.0 to 1175.0
            TICK -> 1500.0 to 1500.0
            BOOM -> 110.0 to 40.0
            SAFE -> 659.0 to 988.0
            MISS -> 392.0 to 165.0
            PLACE -> 1046.0 to 1046.0
            REVEAL -> 523.0 to 1046.0
            PIP -> 880.0 to 880.0
        }

    internal val gain: Double
        get() = when (this) {
            CUE -> 0.95
            TICK -> 0.35
            BOOM -> 1.0
            SAFE -> 0.6
            MISS -> 0.6
            PLACE -> 0.22
            REVEAL -> 0.7
            PIP -> 0.45
        }

    /** Noise-based tones get a burst of white noise mixed over the sweep. */
    internal val noiseMix: Double
        get() = when (this) {
            BOOM -> 0.85
            MISS -> 0.15
            else -> 0.0
        }

    /** How sharply the tail decays. Higher is snappier. */
    internal val decayCurve: Double
        get() = when (this) {
            TICK, PLACE, PIP -> 3.5
            BOOM -> 1.6
            else -> 2.2
        }
}

/**
 * Plays the game cues. Built on [SoundPool] over buffers rendered at start-up:
 * a pre-loaded sample starts on the next mixer pass, which matters when the
 * tone *is* the thing being reacted to.
 *
 * Everything is best-effort — a device that cannot load the pool still plays
 * the games, just silently, so no call site has to handle a failure.
 */
class SoundEngine(context: Context) {

    private val appContext = context.applicationContext
    private var pool: SoundPool? = null
    // Written on the caster-tone-render thread, read on the main thread every
    // time a cue fires, so it cannot be a plain HashMap.
    private val soundIds = ConcurrentHashMap<Tone, Int>()
    private val loaded = HashSet<Int>()

    @Volatile
    private var isRunning = false

    /** Mirrors the user's mute preference. Games check nothing — they just play. */
    var isMuted: Boolean = false

    fun start() {
        if (isRunning) return
        isRunning = true

        val attributes = AudioAttributes.Builder()
            // GAME rather than MUSIC: the cues stay audible over whatever is
            // playing in the room instead of ducking or pausing it.
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val created = SoundPool.Builder()
            // Room for every overlapping cue a round can produce at once.
            .setMaxStreams(8)
            .setAudioAttributes(attributes)
            .build()

        created.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) synchronized(loaded) { loaded.add(sampleId) }
        }
        pool = created

        // Rendering eight buffers is a few milliseconds of maths and a handful
        // of small file writes; keep both off the frame the screen is drawing.
        Thread({ loadTones(created) }, "caster-tone-render").start()
    }

    fun stop() {
        // Deliberately not a release: the screens come and go constantly, and
        // rebuilding the pool each time would cost the first cue of every round.
        pool?.autoPause()
    }

    fun resume() {
        pool?.autoResume()
    }

    fun release() {
        pool?.release()
        pool = null
        soundIds.clear()
        synchronized(loaded) { loaded.clear() }
        isRunning = false
    }

    fun play(tone: Tone) {
        if (isMuted) return
        if (!isRunning) start()
        val activePool = pool ?: return
        val id = soundIds[tone] ?: return
        val isReady = synchronized(loaded) { id in loaded }
        if (!isReady) return
        activePool.play(id, 1f, 1f, 1, 0, 1f)
    }

    // region Setup

    private fun loadTones(target: SoundPool) {
        // Keyed by the tone definitions, not just "tones": these WAVs survive an
        // app update, so without this a retuned Tone would keep playing the old
        // sound forever on any device that had already cached it.
        val directory = File(appContext.cacheDir, "tones-v$TONE_CACHE_VERSION").apply { mkdirs() }
        for (tone in Tone.entries) {
            val file = File(directory, "${tone.name.lowercase()}.wav")
            val written = runCatching {
                if (!file.exists() || file.length() == 0L) {
                    writeWav(file, render(tone))
                }
                true
            }.getOrDefault(false)
            if (!written) continue
            val id = runCatching { target.load(file.absolutePath, 1) }.getOrNull() ?: continue
            if (id != 0) soundIds[tone] = id
        }
    }

    /**
     * Renders one tone: a frequency sweep plus a second harmonic, optionally
     * mixed with noise, under a fast-attack envelope.
     */
    private fun render(tone: Tone): ShortArray {
        val frameCount = (SAMPLE_RATE * tone.duration).toInt()
        if (frameCount <= 0) return ShortArray(0)

        val samples = ShortArray(frameCount)
        val (sweepStart, sweepEnd) = tone.sweep
        val noiseMix = tone.noiseMix
        val toneMix = 1 - noiseMix
        var phase = 0.0
        var noise = 0.0

        for (frame in 0 until frameCount) {
            val progress = frame.toDouble() / frameCount.toDouble()
            val frequency = sweepStart + (sweepEnd - sweepStart) * progress
            phase += 2 * PI * frequency / SAMPLE_RATE

            var sample = (sin(phase) * 0.82 + sin(phase * 2) * 0.18) * toneMix
            if (noiseMix > 0) {
                // One-pole low-passed noise: raw white noise reads as a hiss,
                // this reads as a thump.
                noise = noise * 0.72 + Random.nextDouble(-1.0, 1.0) * 0.28
                sample += noise * 3.2 * noiseMix
            }

            val value = sample * envelope(progress, tone.decayCurve) * tone.gain
            samples[frame] = (value.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }
        return samples
    }

    private fun envelope(progress: Double, curve: Double): Double {
        val attack = 0.02
        if (progress < attack) return progress / attack
        val tail = (progress - attack) / (1 - attack)
        return maxOf(0.0, 1 - tail).pow(curve)
    }

    /** A 16-bit mono PCM WAV, which is all SoundPool needs to load a sample. */
    private fun writeWav(file: File, samples: ShortArray) {
        val dataBytes = samples.size * 2
        RandomAccessFile(file, "rw").use { out ->
            out.setLength(0)
            out.writeBytes("RIFF")
            out.writeIntLE(36 + dataBytes)
            out.writeBytes("WAVE")
            out.writeBytes("fmt ")
            out.writeIntLE(16)              // PCM header size
            out.writeShortLE(1)             // format: PCM
            out.writeShortLE(1)             // channels: mono
            out.writeIntLE(SAMPLE_RATE)
            out.writeIntLE(SAMPLE_RATE * 2) // byte rate
            out.writeShortLE(2)             // block align
            out.writeShortLE(16)            // bits per sample
            out.writeBytes("data")
            out.writeIntLE(dataBytes)

            val bytes = ByteArray(dataBytes)
            for ((index, sample) in samples.withIndex()) {
                bytes[index * 2] = (sample.toInt() and 0xFF).toByte()
                bytes[index * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
            }
            out.write(bytes)
        }
    }

    private fun RandomAccessFile.writeIntLE(value: Int) {
        write(
            byteArrayOf(
                (value and 0xFF).toByte(),
                ((value shr 8) and 0xFF).toByte(),
                ((value shr 16) and 0xFF).toByte(),
                ((value shr 24) and 0xFF).toByte(),
            )
        )
    }

    private fun RandomAccessFile.writeShortLE(value: Int) {
        write(byteArrayOf((value and 0xFF).toByte(), ((value shr 8) and 0xFF).toByte()))
    }

    // endregion

    private companion object {
        /** Bump when any Tone's shape changes, so cached WAVs are re-rendered. */
        const val TONE_CACHE_VERSION = 1
        const val SAMPLE_RATE = 44_100
    }
}
