import SwiftUI

struct ModeSelectView: View {
    @Environment(AppEnvironment.self) private var environment
    @Environment(GameState.self) private var gameState
    @Environment(\.theme) private var theme
    @Binding var path: [Route]

    var body: some View {
        ZStack {
            theme.background
                .ignoresSafeArea()

            VStack(spacing: 0) {
                Text("Pick One")
                    .font(.system(.title, design: .rounded))
                    .fontWeight(.bold)
                    .foregroundStyle(theme.textPrimary)
                    .padding(.top, 16)
                    .padding(.bottom, 12)

                // Scrolls: six cards plus the button overflow a small screen.
                ScrollView {
                    VStack(spacing: 10) {
                        ForEach(GameMode.allCases) { mode in
                            modeCard(mode: mode, isSelected: gameState.currentMode == mode)
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
                }

                Button {
                    environment.hapticEngine.playFeedback(type: .medium)
                    path.append(.playerSetup)
                } label: {
                    Text("Continue")
                        .font(.system(.title3, design: .rounded))
                        .fontWeight(.bold)
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(theme.accent, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                }
                .buttonStyle(.plain)
                .padding(.horizontal, 16)
                .padding(.bottom, 24)
            }
        }
        .navigationTitle("Game Modes")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func modeCard(mode: GameMode, isSelected: Bool) -> some View {
        // One Button for the whole row. The previous version had a no-op
        // `onTapGesture` on the card itself, which swallowed the parent's tap
        // and made selection impossible.
        Button {
            environment.hapticEngine.playFeedback(type: .light)
            withAnimation(.easeInOut(duration: 0.15)) {
                gameState.currentMode = mode
            }
        } label: {
            HStack(spacing: 16) {
                Image(systemName: mode.iconName)
                    .font(.title2)
                    .frame(width: 32)
                    .foregroundStyle(isSelected ? theme.accent : theme.textSecondary)

                VStack(alignment: .leading, spacing: 4) {
                    Text(mode.title)
                        .font(.headline)
                        .foregroundStyle(theme.textPrimary)

                    Text(mode.summary)
                        .font(.caption)
                        .foregroundStyle(theme.textSecondary)
                        .multilineTextAlignment(.leading)
                        .fixedSize(horizontal: false, vertical: true)
                }

                Spacer(minLength: 8)

                Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                    .font(.title3)
                    .foregroundStyle(isSelected ? theme.accent : theme.border)
            }
            .padding(.vertical, 12)
            .padding(.horizontal, 16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .fill(isSelected ? theme.accent.opacity(0.1) : theme.surfaceRaised)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .stroke(isSelected ? theme.accent : theme.border, lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(isSelected ? [.isButton, .isSelected] : [.isButton])
    }
}

#Preview {
    NavigationStack {
        ModeSelectView(path: .constant([]))
            .environment(AppEnvironment())
            .environment(GameState())
    }
}
