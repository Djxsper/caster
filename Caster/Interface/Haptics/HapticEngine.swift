import Foundation
import CoreHaptics
import UIKit

/// Wraps Core Haptics with a UIKit feedback-generator fallback.
///
/// Core Haptics is unavailable on the Simulator and on devices without a Taptic
/// Engine, so every path degrades to `UIFeedbackGenerator`, which is a no-op
/// rather than an error where hardware is missing.
@MainActor
final class HapticEngine {
    static let shared = HapticEngine()

    /// Strongly held: the engine is deallocated the moment nothing references it.
    private var engine: CHHapticEngine?
    private var isEngineRunning = false

    private let lightGenerator = UIImpactFeedbackGenerator(style: .light)
    private let mediumGenerator = UIImpactFeedbackGenerator(style: .medium)
    private let heavyGenerator = UIImpactFeedbackGenerator(style: .heavy)
    private let selectionGenerator = UISelectionFeedbackGenerator()

    private var supportsHaptics: Bool {
        CHHapticEngine.capabilitiesForHardware().supportsHaptics
    }

    private init() {}

    func startEngine() {
        prepareGenerators()

        guard supportsHaptics else { return }
        guard engine == nil else {
            restartIfNeeded()
            return
        }

        do {
            let engine = try CHHapticEngine()
            engine.isAutoShutdownEnabled = true
            engine.stoppedHandler = { [weak self] _ in
                Task { @MainActor in self?.isEngineRunning = false }
            }
            // The system can reset the engine at any time; recreate the player state.
            engine.resetHandler = { [weak self] in
                Task { @MainActor in
                    self?.isEngineRunning = false
                    self?.restartIfNeeded()
                }
            }
            try engine.start()
            self.engine = engine
            isEngineRunning = true
        } catch {
            print("Haptic engine failed to start: \(error)")
            engine = nil
            isEngineRunning = false
        }
    }

    func endEngine() {
        engine?.stop()
        isEngineRunning = false
    }

    func playFeedback(type: FeedbackType) {
        guard isEngineRunning, let engine else {
            fallbackFeedback(type)
            return
        }

        do {
            let event = CHHapticEvent(
                eventType: .hapticTransient,
                parameters: [
                    CHHapticEventParameter(parameterID: .hapticIntensity, value: type.intensity),
                    CHHapticEventParameter(parameterID: .hapticSharpness, value: type.sharpness)
                ],
                relativeTime: 0
            )
            let pattern = try CHHapticPattern(events: [event], parameters: [])
            let player = try engine.makePlayer(with: pattern)
            try player.start(atTime: CHHapticTimeImmediate)
        } catch {
            fallbackFeedback(type)
        }
    }

    /// Warms up the generators so the first tap is not delayed by hardware spin-up.
    func prepareGenerators() {
        lightGenerator.prepare()
        mediumGenerator.prepare()
        heavyGenerator.prepare()
        selectionGenerator.prepare()
    }

    private func restartIfNeeded() {
        guard let engine, !isEngineRunning else { return }
        do {
            try engine.start()
            isEngineRunning = true
        } catch {
            isEngineRunning = false
        }
    }

    private func fallbackFeedback(_ type: FeedbackType) {
        switch type {
        case .light:
            lightGenerator.impactOccurred()
            lightGenerator.prepare()
        case .medium:
            mediumGenerator.impactOccurred()
            mediumGenerator.prepare()
        case .heavy:
            heavyGenerator.impactOccurred()
            heavyGenerator.prepare()
        case .sharp:
            selectionGenerator.selectionChanged()
            selectionGenerator.prepare()
        }
    }
}

enum FeedbackType: String, CaseIterable, Identifiable {
    case light
    case medium
    case heavy
    case sharp

    var id: String { rawValue }

    var intensity: Float {
        switch self {
        case .light: return 0.3
        case .medium: return 0.5
        case .heavy: return 0.8
        case .sharp: return 0.4
        }
    }

    var sharpness: Float {
        switch self {
        case .light: return 0.3
        case .medium: return 0.5
        case .heavy: return 0.6
        case .sharp: return 1.0
        }
    }
}
