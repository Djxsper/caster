import SwiftUI

/// The filled pill every screen uses for its main action.
struct PrimaryButton: View {
    @Environment(\.theme) private var theme

    let title: String
    var tint: Color?
    var isEnabled = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.system(.title3, design: .rounded))
                .fontWeight(.bold)
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity)
                .padding()
                .background(
                    tint ?? theme.accent,
                    in: RoundedRectangle(cornerRadius: 12, style: .continuous)
                )
        }
        .buttonStyle(.plain)
        .disabled(!isEnabled)
        .opacity(isEnabled ? 1 : 0.45)
    }
}

/// A quieter, outlined button for the secondary action next to a `PrimaryButton`.
struct SecondaryButton: View {
    @Environment(\.theme) private var theme

    let title: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.system(.callout, design: .rounded))
                .fontWeight(.semibold)
                .foregroundStyle(theme.textPrimary)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
                .background(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(theme.surfaceRaised)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .stroke(theme.border, lineWidth: 1)
                )
        }
        .buttonStyle(.plain)
    }
}

/// The one-line prompt every game keeps under its play area.
struct StatusLine: View {
    @Environment(\.theme) private var theme

    let text: String
    var emphasis: Color?

    var body: some View {
        Text(text)
            .font(.system(.callout, design: .rounded))
            .fontWeight(.medium)
            .foregroundStyle(emphasis ?? theme.textSecondary)
            .multilineTextAlignment(.center)
            .lineLimit(2)
            .minimumScaleFactor(0.75)
            .frame(maxWidth: .infinity)
            .padding(.horizontal, 24)
            .animation(.easeInOut(duration: 0.2), value: text)
    }
}

/// A ring drawn under someone's finger. Shared by every touch game so a finger
/// looks and behaves the same wherever it lands.
struct FingerRing: View {
    let color: Color
    var diameter: CGFloat = 84
    /// Fill of the outer countdown arc, 0...1. Hidden at 0.
    var progress: Double = 0
    /// Rotation of the dashed halo, in degrees.
    var spin: Double = 0
    var isHighlighted = false
    var isDimmed = false
    var badge: String?

    private var haloDiameter: CGFloat { diameter + 34 }
    private var arcDiameter: CGFloat { diameter + 18 }

    var body: some View {
        ZStack {
            Circle()
                .fill(color.opacity(0.24))
                .frame(width: diameter, height: diameter)

            Circle()
                .stroke(color, lineWidth: 5)
                .frame(width: diameter, height: diameter)

            Circle()
                .trim(from: 0, to: progress)
                .stroke(
                    color.opacity(0.95),
                    style: StrokeStyle(lineWidth: 7, lineCap: .round)
                )
                .frame(width: arcDiameter, height: arcDiameter)
                .rotationEffect(.degrees(-90))

            Circle()
                .stroke(
                    color.opacity(0.45),
                    style: StrokeStyle(lineWidth: 3, dash: [9, 13])
                )
                .frame(width: haloDiameter, height: haloDiameter)
                .rotationEffect(.degrees(spin))

            if let badge {
                Text(badge)
                    .font(.system(size: diameter * 0.42, weight: .heavy, design: .rounded))
                    .foregroundStyle(color)
                    .minimumScaleFactor(0.4)
                    .lineLimit(1)
                    .frame(width: diameter * 0.9)
            }
        }
        .frame(width: haloDiameter, height: haloDiameter)
        .scaleEffect(isHighlighted ? 1.16 : 1)
        .opacity(isDimmed ? 0.16 : 1)
        .animation(.spring(response: 0.3, dampingFraction: 0.7), value: isHighlighted)
        .animation(.easeInOut(duration: 0.25), value: isDimmed)
    }
}

/// Pairs a full-screen multi-touch surface with an overlay drawn in the same
/// coordinate space, so a ring rendered at a touch point lands under the finger.
struct TouchSurface<Overlay: View>: View {
    let arena: TouchArena
    @ViewBuilder var overlay: (CGSize) -> Overlay

    var body: some View {
        GeometryReader { proxy in
            ZStack {
                MultiTouchView(arena: arena)
                    .frame(width: proxy.size.width, height: proxy.size.height)

                overlay(proxy.size)
                    .frame(width: proxy.size.width, height: proxy.size.height)
                    .allowsHitTesting(false)
            }
        }
        .ignoresSafeArea()
    }
}

/// The result card that slides in when a round resolves.
struct ResultBanner: View {
    @Environment(\.theme) private var theme

    let headline: String
    var detail: String?
    var tint: Color?

    var body: some View {
        VStack(spacing: 6) {
            Text(headline)
                .font(.system(.title2, design: .rounded))
                .fontWeight(.heavy)
                .foregroundStyle(tint ?? theme.textPrimary)
                .multilineTextAlignment(.center)
                .minimumScaleFactor(0.6)
                .lineLimit(2)

            if let detail {
                Text(detail)
                    .font(.system(.footnote, design: .rounded))
                    .foregroundStyle(theme.textSecondary)
                    .multilineTextAlignment(.center)
            }
        }
        .padding(.vertical, 16)
        .padding(.horizontal, 24)
        .frame(maxWidth: .infinity)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(theme.surfaceRaised)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke((tint ?? theme.border).opacity(0.5), lineWidth: 1)
        )
    }
}
