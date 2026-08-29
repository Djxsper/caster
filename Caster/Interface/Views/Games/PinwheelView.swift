import SwiftUI
import UIKit

/// A spin-the-wheel. The entry list is unbounded — the wheel sizes its own
/// slices, type and label density to whatever is on it.
///
/// The winner is drawn uniformly *before* the animation starts and the spin is
/// then aimed at that slice. Letting friction decide would quietly bias the
/// result toward wherever the wheel happened to be resting.
struct PinwheelView: View {
    @Environment(AppEnvironment.self) private var environment
    @Environment(WheelStore.self) private var wheelStore
    @Environment(\.theme) private var theme

    @State private var rotation: Double = 0
    @State private var isSpinning = false
    @State private var winnerIndex: Int?
    @State private var spinTask: Task<Void, Never>?
    @State private var lastTickBoundary = 0

    var body: some View {
        ZStack {
            theme.background
                .ignoresSafeArea()

            VStack(spacing: 16) {
                wheelArea

                StatusLine(text: statusText, emphasis: winnerIndex == nil ? nil : theme.accent)

                controls
                    .padding(.horizontal, 16)
                    .padding(.bottom, 20)
            }
            .padding(.top, 8)
        }
        .navigationTitle(GameMode.pinwheel.title)
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            environment.hapticEngine.startEngine()
            environment.soundEngine.start()
        }
        .onDisappear {
            spinTask?.cancel()
            spinTask = nil
        }
    }

    // MARK: - Wheel

    private var wheelArea: some View {
        GeometryReader { proxy in
            let side = min(proxy.size.width, proxy.size.height)
            let renderer = WheelRenderer(labels: wheelStore.labels)

            ZStack {
                Canvas { context, size in
                    renderer.draw(into: &context, size: size)
                }
                .frame(width: side, height: side)
                .rotationEffect(.degrees(rotation))
                .gesture(flickGesture)

                hubOverlay
                pointer
                    .offset(y: -side / 2 + 2)
            }
            .frame(width: proxy.size.width, height: proxy.size.height)
        }
        .padding(.horizontal, 20)
    }

    private var hubOverlay: some View {
        ZStack {
            Circle()
                .fill(theme.background)
                .frame(width: 62, height: 62)
            Circle()
                .stroke(theme.border, lineWidth: 1)
                .frame(width: 62, height: 62)
            Image(systemName: isSpinning ? "arrow.triangle.2.circlepath" : "hand.draw")
                .font(.system(size: 22, weight: .semibold))
                .foregroundStyle(theme.textSecondary)
        }
        .allowsHitTesting(false)
    }

    /// Drawn outside the rotating canvas so it stays pinned at twelve o'clock.
    private var pointer: some View {
        Triangle()
            .fill(theme.textPrimary)
            .frame(width: 26, height: 22)
            .shadow(color: .black.opacity(0.25), radius: 3, y: 1)
            .allowsHitTesting(false)
    }

    // MARK: - Controls

    @ViewBuilder
    private var controls: some View {
        if wheelStore.canSpin {
            VStack(spacing: 10) {
                PrimaryButton(
                    title: isSpinning ? "Spinning…" : "Spin",
                    isEnabled: !isSpinning,
                    action: { spin(strength: Double.random(in: 0.45...0.9)) }
                )

                if let winnerIndex, !isSpinning, wheelStore.entries.indices.contains(winnerIndex) {
                    SecondaryButton(title: "Remove \(wheelStore.entries[winnerIndex].label)") {
                        removeWinner(at: winnerIndex)
                    }
                }
            }
        } else {
            ResultBanner(
                headline: "The wheel needs two entries",
                detail: "Go back and add some names."
            )
        }
    }

    private var statusText: String {
        if isSpinning { return "…" }
        if let winnerIndex, wheelStore.entries.indices.contains(winnerIndex) {
            return wheelStore.entries[winnerIndex].label
        }
        return "Flick the wheel or press Spin"
    }

    // MARK: - Spin

    private var flickGesture: some Gesture {
        DragGesture(minimumDistance: 12)
            .onEnded { value in
                guard !isSpinning else { return }
                let distance = hypot(value.translation.width, value.translation.height)
                spin(strength: min(1, distance / 260))
            }
    }

    /// - Parameter strength: 0...1. Showmanship only — it sets how long and how
    ///   far the wheel travels, never where it stops.
    private func spin(strength: Double) {
        let entries = wheelStore.entries
        guard entries.count >= 2, !isSpinning else { return }

        spinTask?.cancel()
        winnerIndex = nil
        isSpinning = true

        let winner = Int.random(in: 0..<entries.count)
        let segment = 360.0 / Double(entries.count)
        // Land somewhere inside the slice rather than dead centre every time.
        let jitter = Double.random(in: -0.33...0.33)
        let desired = -((Double(winner) + 0.5 + jitter) * segment)

        var delta = (desired - rotation).truncatingRemainder(dividingBy: 360)
        if delta < 0 { delta += 360 }

        let turns = 4 + Int((strength * 6).rounded())
        let start = rotation
        let span = Double(turns) * 360 + delta
        let duration = 3.2 + strength * 2.4

        lastTickBoundary = Int(floor(start / segment))

        spinTask = Task {
            let began = Date()
            while !Task.isCancelled {
                let elapsed = Date().timeIntervalSince(began)
                let fraction = min(1, elapsed / duration)
                // Cubic ease-out: quick off the mark, long settle at the end.
                let eased = 1 - pow(1 - fraction, 3.1)
                rotation = start + span * eased

                let boundary = Int(floor(rotation / segment))
                if boundary != lastTickBoundary {
                    lastTickBoundary = boundary
                    environment.cue(.light, .tick)
                }

                if fraction >= 1 { break }
                guard (try? await Task.sleep(for: .milliseconds(16))) != nil else { return }
            }
            guard !Task.isCancelled else { return }
            finishSpin(winner: winner)
        }
    }

    private func finishSpin(winner: Int) {
        isSpinning = false
        // Keep the accumulated angle small so the tick maths stays exact.
        rotation = rotation.truncatingRemainder(dividingBy: 360)
        withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) {
            winnerIndex = winner
        }
        environment.cue(.heavy, .reveal)
    }

    private func removeWinner(at index: Int) {
        guard wheelStore.entries.indices.contains(index) else { return }
        let id = wheelStore.entries[index].id
        environment.hapticEngine.playFeedback(type: .medium)
        winnerIndex = nil
        wheelStore.remove(id: id)
    }

}

/// The wheel's drawing, deliberately kept out of the `View`: a `Canvas` render
/// closure is not main-actor isolated, so it must not reach back into one.
/// This gets a plain array of labels and owns every decision about slice
/// colour, type size and truncation.
private struct WheelRenderer {
    let labels: [String]

    func draw(into context: inout GraphicsContext, size: CGSize) {
        let count = labels.count
        guard count > 0 else { return }

        let radius = min(size.width, size.height) / 2
        let center = CGPoint(x: size.width / 2, y: size.height / 2)
        let segment = 2 * Double.pi / Double(count)

        for index in 0..<count {
            let start = -Double.pi / 2 + Double(index) * segment
            var path = Path()
            path.move(to: center)
            path.addArc(
                center: center,
                radius: radius,
                startAngle: .radians(start),
                endAngle: .radians(start + segment),
                clockwise: false
            )
            path.closeSubpath()

            let palette = Self.sliceColors(index: index, total: count)
            context.fill(path, with: .color(palette.fill))
            context.stroke(path, with: .color(.white.opacity(0.22)), lineWidth: 1)

            drawLabel(
                labels[index],
                into: &context,
                center: center,
                radius: radius,
                midAngle: start + segment / 2,
                segment: segment,
                ink: palette.ink
            )
        }
    }

    private func drawLabel(
        _ label: String,
        into context: inout GraphicsContext,
        center: CGPoint,
        radius: CGFloat,
        midAngle: Double,
        segment: Double,
        ink: Color
    ) {
        // Tangential room where the text sits, which is what actually limits
        // the type size once the wheel gets busy.
        let labelRadius = radius * 0.58
        let tangential = segment * labelRadius
        // Below this a label is unreadable anyway, so the slice colour and the
        // result line carry the identity instead of a smear of pixels.
        guard tangential >= 11 else { return }

        let fontSize = max(9, min(19, min(tangential * 0.72, radius * 0.14)))
        let available = radius * 0.74
        let maximumCharacters = max(2, Int(available / (fontSize * 0.52)))
        let text = label.count > maximumCharacters
            ? String(label.prefix(max(1, maximumCharacters - 1))) + "…"
            : label

        let resolved = context.resolve(
            Text(text)
                .font(.system(size: fontSize, weight: .semibold, design: .rounded))
                .foregroundStyle(ink)
        )

        // A slice on the left half puts its label past vertical, where the
        // text ends up upside down. Turn the layer the other way and draw down
        // the opposite radius, which lands in the same place the right way up.
        let isFlipped = cos(midAngle) < 0

        var layer = context
        layer.translateBy(x: center.x, y: center.y)
        layer.rotate(by: .radians(isFlipped ? midAngle + .pi : midAngle))
        layer.draw(
            resolved,
            at: CGPoint(x: isFlipped ? -labelRadius : labelRadius, y: 0),
            anchor: .center
        )
    }

    /// Slice fill plus an ink colour that stays legible on it, picked from the
    /// fill's luminance so a pale amber slice does not get white type.
    private static func sliceColors(index: Int, total: Int) -> (fill: Color, ink: Color) {
        let fill = PlayerPalette.spread(index: index, total: total)

        var red: CGFloat = 0
        var green: CGFloat = 0
        var blue: CGFloat = 0
        var alpha: CGFloat = 0
        guard UIColor(fill).getRed(&red, green: &green, blue: &blue, alpha: &alpha) else {
            return (fill, .white)
        }

        let luminance = 0.299 * red + 0.587 * green + 0.114 * blue
        return (fill, luminance > 0.6 ? Color.black.opacity(0.82) : Color.white)
    }
}

/// The wheel's pointer.
private struct Triangle: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: rect.midX, y: rect.maxY))
        path.addLine(to: CGPoint(x: rect.minX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.minY))
        path.closeSubpath()
        return path
    }
}

#Preview {
    NavigationStack {
        PinwheelView()
            .environment(AppEnvironment())
            .environment(WheelStore())
    }
}
