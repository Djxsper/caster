import Foundation
import AVFoundation

/// The cues the games play. Every one is synthesised at start-up rather than
/// shipped as an audio file, so the app carries no media assets and the tones
/// stay tweakable in one place.
enum Tone: String, CaseIterable {
    /// Uppercut's go-signal: the one sound that has to cut through a room.
    case cue
    /// Hot Potato's fuse tick.
    case tick
    /// Hot Potato's detonation.
    case boom
    /// A player got out safe.
    case safe
    /// A player missed their window.
    case miss
    /// A finger landed on the glass.
    case place
    /// A round resolved: here is your answer.
    case reveal
    /// Countdown pip.
    case pip

    fileprivate var duration: Double {
        switch self {
        case .cue: return 0.32
        case .tick: return 0.045
        case .boom: return 0.85
        case .safe: return 0.22
        case .miss: return 0.30
        case .place: return 0.06
        case .reveal: return 0.55
        case .pip: return 0.10
        }
    }

    /// Start and end frequency in hertz; the tone sweeps between them.
    fileprivate var sweep: (start: Double, end: Double) {
        switch self {
        case .cue: return (784, 1175)
        case .tick: return (1500, 1500)
        case .boom: return (110, 40)
        case .safe: return (659, 988)
        case .miss: return (392, 165)
        case .place: return (1046, 1046)
        case .reveal: return (523, 1046)
        case .pip: return (880, 880)
        }
    }

    fileprivate var gain: Double {
        switch self {
        case .cue: return 0.95
        case .tick: return 0.35
        case .boom: return 1.0
        case .safe: return 0.6
        case .miss: return 0.6
        case .place: return 0.22
        case .reveal: return 0.7
        case .pip: return 0.45
        }
    }

    /// Noise-based tones get a burst of white noise mixed over the sweep.
    fileprivate var noiseMix: Double {
        switch self {
        case .boom: return 0.85
        case .miss: return 0.15
        default: return 0
        }
    }

    /// How sharply the tail decays. Higher is snappier.
    fileprivate var decayCurve: Double {
        switch self {
        case .tick, .place, .pip: return 3.5
        case .boom: return 1.6
        default: return 2.2
        }
    }
}

/// Plays the game cues. Built on `AVAudioEngine` with pre-rendered buffers: a
/// scheduled buffer starts on the next render quantum, which matters when the
/// tone *is* the thing being reacted to.
///
/// Everything is best-effort — a device that cannot start an audio engine still
/// plays the games, just silently, so no call site has to handle a failure.
@MainActor
final class SoundEngine {
    static let shared = SoundEngine()

    private let engine = AVAudioEngine()
    private var voices: [AVAudioPlayerNode] = []
    private var buffers: [Tone: AVAudioPCMBuffer] = [:]
    private var nextVoice = 0
    private var isRunning = false

    /// Mirrors the user's mute preference. Games check nothing — they just play.
    var isMuted = false

    private init() {}

    func start() {
        guard !isRunning else { return }

        configureSession()
        guard prepareGraph() else { return }

        do {
            try engine.start()
            for voice in voices { voice.play() }
            isRunning = true
        } catch {
            isRunning = false
        }
    }

    func stop() {
        guard isRunning else { return }
        for voice in voices { voice.stop() }
        engine.pause()
        isRunning = false
    }

    func play(_ tone: Tone) {
        guard !isMuted else { return }
        if !isRunning { start() }
        guard isRunning, !voices.isEmpty, let buffer = buffers[tone] else { return }

        // Round-robin across a small pool so overlapping cues layer instead of
        // cutting each other off.
        let voice = voices[nextVoice % voices.count]
        nextVoice = (nextVoice + 1) % voices.count
        voice.scheduleBuffer(buffer, at: nil, options: [.interrupts], completionHandler: nil)
    }

    // MARK: - Setup

    private func configureSession() {
        do {
            let session = AVAudioSession.sharedInstance()
            // `.playback` keeps the cues audible with the ring switch flipped —
            // a party game that goes silent in someone's pocket is broken.
            // `.mixWithOthers` leaves whatever music is on in the room alone.
            try session.setCategory(.playback, mode: .default, options: [.mixWithOthers])
            try session.setActive(true, options: [])
        } catch {
            // A blocked session still lets the engine render into silence.
        }
    }

    private func prepareGraph() -> Bool {
        guard voices.isEmpty else { return true }

        let sampleRate = engine.outputNode.outputFormat(forBus: 0).sampleRate
        let resolvedRate = sampleRate > 0 ? sampleRate : 44_100
        guard let format = AVAudioFormat(standardFormatWithSampleRate: resolvedRate, channels: 1) else {
            return false
        }

        for _ in 0..<4 {
            let voice = AVAudioPlayerNode()
            engine.attach(voice)
            engine.connect(voice, to: engine.mainMixerNode, format: format)
            voices.append(voice)
        }

        for tone in Tone.allCases {
            buffers[tone] = Self.render(tone, format: format)
        }
        return true
    }

    /// Renders one tone into a PCM buffer: a frequency sweep plus a second
    /// harmonic, optionally mixed with noise, under a fast-attack envelope.
    private static func render(_ tone: Tone, format: AVAudioFormat) -> AVAudioPCMBuffer? {
        let sampleRate = format.sampleRate
        let frameCount = AVAudioFrameCount(sampleRate * tone.duration)
        guard frameCount > 0,
              let buffer = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: frameCount),
              let channel = buffer.floatChannelData?[0] else { return nil }

        buffer.frameLength = frameCount

        let sweep = tone.sweep
        let noiseMix = tone.noiseMix
        let toneMix = 1 - noiseMix
        var phase = 0.0
        var noise = 0.0

        for frame in 0..<Int(frameCount) {
            let progress = Double(frame) / Double(frameCount)
            let frequency = sweep.start + (sweep.end - sweep.start) * progress
            phase += 2 * Double.pi * frequency / sampleRate

            var sample = (sin(phase) * 0.82 + sin(phase * 2) * 0.18) * toneMix
            if noiseMix > 0 {
                // One-pole low-passed noise: raw white noise reads as a hiss,
                // this reads as a thump.
                noise = noise * 0.72 + Double.random(in: -1...1) * 0.28
                sample += noise * 3.2 * noiseMix
            }

            let value = sample * envelope(at: progress, curve: tone.decayCurve) * tone.gain
            channel[frame] = Float(max(-1, min(1, value)))
        }

        return buffer
    }

    private static func envelope(at progress: Double, curve: Double) -> Double {
        let attack = 0.02
        if progress < attack { return progress / attack }
        let tail = (progress - attack) / (1 - attack)
        return pow(max(0, 1 - tail), curve)
    }
}
