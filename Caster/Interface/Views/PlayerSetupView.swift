import SwiftUI

struct PlayerSetupView: View {
    @Environment(AppEnvironment.self) private var environment
    @Environment(GameState.self) private var gameState
    @Environment(\.theme) private var theme
    @Binding var path: [Route]

    /// `Slider` needs a floating-point binding, so the count is stored as a
    /// `Double` and rounded on read.
    @State private var playerCountValue = 4.0
    /// Always `PlayerLimits.maximum` long, so indexing never runs off the end when
    /// the slider goes past the number of names that were seeded.
    @State private var playerNames: [String] = (0..<PlayerLimits.maximum).map { "Player \($0 + 1)" }
    @FocusState private var focusedField: Int?

    private var playerCount: Int {
        min(max(Int(playerCountValue.rounded()), PlayerLimits.minimum), PlayerLimits.maximum)
    }

    var body: some View {
        ZStack {
            theme.background
                .ignoresSafeArea()

            VStack(spacing: 16) {
                Text("How many players?")
                    .font(.system(.title2, design: .rounded))
                    .fontWeight(.bold)
                    .foregroundStyle(theme.textPrimary)

                Text("\(playerCount)")
                    .font(.system(.largeTitle, design: .rounded))
                    .fontWeight(.bold)
                    .foregroundStyle(theme.accent)
                    .contentTransition(.numericText())
                    .animation(.snappy, value: playerCount)

                Slider(
                    value: $playerCountValue,
                    in: Double(PlayerLimits.minimum)...Double(PlayerLimits.maximum),
                    step: 1
                ) {
                    Text("Players")
                } minimumValueLabel: {
                    Text("\(PlayerLimits.minimum)")
                } maximumValueLabel: {
                    Text("\(PlayerLimits.maximum)")
                }
                .tint(theme.accent)
                .padding(.horizontal, 16)

                Divider()

                ScrollView {
                    VStack(spacing: 8) {
                        ForEach(0..<playerCount, id: \.self) { index in
                            HStack(spacing: 12) {
                                Circle()
                                    .fill(theme.playerColor(for: index))
                                    .frame(width: 28, height: 28)

                                TextField("Player \(index + 1)", text: $playerNames[index])
                                    .font(.body)
                                    .foregroundStyle(theme.textPrimary)
                                    .textInputAutocapitalization(.words)
                                    .autocorrectionDisabled()
                                    .submitLabel(.done)
                                    .focused($focusedField, equals: index)

                                Spacer(minLength: 0)
                            }
                            .padding(.vertical, 10)
                            .padding(.horizontal, 16)
                            .background(
                                RoundedRectangle(cornerRadius: 10, style: .continuous)
                                    .fill(theme.surfaceRaised)
                            )
                            .overlay(
                                RoundedRectangle(cornerRadius: 10, style: .continuous)
                                    .stroke(theme.border, lineWidth: 1)
                            )
                        }
                    }
                    .padding(.horizontal, 16)
                }

                Button(action: startGame) {
                    Text("Start Game")
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
            .padding(.top, 12)
        }
        .navigationTitle("Setup")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .keyboard) {
                Button("Done") { focusedField = nil }
            }
        }
    }

    private func startGame() {
        focusedField = nil
        // Write into the shared state instead of a throwaway local instance —
        // this is what the draw screen actually reads.
        gameState.configurePlayers(count: playerCount, names: playerNames)
        environment.hapticEngine.playFeedback(type: .heavy)
        path.append(.draw)
    }
}

#Preview {
    NavigationStack {
        PlayerSetupView(path: .constant([]))
            .environment(AppEnvironment())
            .environment(GameState())
    }
}
