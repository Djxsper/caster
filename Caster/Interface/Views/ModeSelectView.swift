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

                PrimaryButton(title: continueTitle, action: advance)
                    .padding(.horizontal, 16)
                    .padding(.bottom, 24)
            }
        }
        .navigationTitle("Game Modes")
        .navigationBarTitleDisplayMode(.inline)
    }

    /// The label names the next screen, because it is not always the game — two
    /// modes need a setup step first.
    private var continueTitle: String {
        switch gameState.currentMode.setupRoute {
        case .playerSetup: return "Add Players"
        case .wheelSetup: return "Edit the Wheel"
        default: return "Play"
        }
    }

    private func advance() {
        environment.hapticEngine.playFeedback(type: .medium)
        let mode = gameState.currentMode
        path.append(mode.setupRoute ?? .game(mode))
    }

    private func modeCard(mode: GameMode, isSelected: Bool) -> some View {
        // One Button for the whole row. A no-op `onTapGesture` on the card
        // itself would swallow the parent's tap and make selection impossible.
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
