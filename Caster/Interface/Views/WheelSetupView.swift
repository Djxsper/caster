import SwiftUI

/// The pinwheel's entry editor. Unbounded on purpose — the wheel scales itself
/// to whatever is in the list rather than the list being clamped to fit a
/// fixed number of slices.
struct WheelSetupView: View {
    @Environment(AppEnvironment.self) private var environment
    @Environment(WheelStore.self) private var wheelStore
    @Environment(GameState.self) private var gameState
    @Environment(\.theme) private var theme
    @Binding var path: [Route]

    @State private var draftEntry = ""
    @FocusState private var isAddFieldFocused: Bool

    var body: some View {
        ZStack {
            theme.background
                .ignoresSafeArea()

            VStack(spacing: 12) {
                addRow

                entryList

                PrimaryButton(
                    title: "Spin",
                    isEnabled: wheelStore.canSpin,
                    action: startGame
                )
                .padding(.horizontal, 16)
                .padding(.bottom, 20)
            }
            .padding(.top, 12)
        }
        .navigationTitle("The Wheel")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                EditButton()

                Menu {
                    Button {
                        wheelStore.replaceAll(with: gameState.players.map(\.name))
                    } label: {
                        Label("Use player names", systemImage: "person.2")
                    }
                    .disabled(gameState.players.isEmpty)

                    Button {
                        wheelStore.resetToDefaults()
                    } label: {
                        Label("Reset to sample names", systemImage: "arrow.counterclockwise")
                    }

                    Button(role: .destructive) {
                        wheelStore.replaceAll(with: [])
                    } label: {
                        Label("Remove all", systemImage: "trash")
                    }
                } label: {
                    Image(systemName: "ellipsis.circle")
                }
            }
        }
    }

    private var addRow: some View {
        HStack(spacing: 10) {
            TextField("Add an entry", text: $draftEntry)
                .font(.body)
                .foregroundStyle(theme.textPrimary)
                .textInputAutocapitalization(.words)
                .autocorrectionDisabled()
                .submitLabel(.done)
                .focused($isAddFieldFocused)
                .onSubmit(commitDraft)
                .padding(.vertical, 12)
                .padding(.horizontal, 14)
                .background(
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .fill(theme.surfaceRaised)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .stroke(theme.border, lineWidth: 1)
                )

            Button(action: commitDraft) {
                Image(systemName: "plus")
                    .font(.system(.title3, weight: .bold))
                    .foregroundStyle(.white)
                    .frame(width: 46, height: 46)
                    .background(theme.accent, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
            }
            .buttonStyle(.plain)
            .disabled(draftEntry.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            .opacity(draftEntry.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? 0.45 : 1)
        }
        .padding(.horizontal, 16)
    }

    @ViewBuilder
    private var entryList: some View {
        if wheelStore.entries.isEmpty {
            VStack(spacing: 8) {
                Spacer()
                Image(systemName: "circle.dashed")
                    .font(.system(size: 44))
                    .foregroundStyle(theme.border)
                Text("Add at least two entries to spin.")
                    .font(.callout)
                    .foregroundStyle(theme.textSecondary)
                Spacer()
            }
            .frame(maxWidth: .infinity)
        } else {
            List {
                ForEach(Array(wheelStore.entries.enumerated()), id: \.element.id) { index, entry in
                    entryRow(index: index, entry: entry)
                }
                .onDelete { wheelStore.remove(at: $0) }
                .onMove { wheelStore.move(from: $0, to: $1) }
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
        }
    }

    private func entryRow(index: Int, entry: WheelEntry) -> some View {
        HStack(spacing: 12) {
            Circle()
                .fill(PlayerPalette.spread(index: index, total: wheelStore.entries.count))
                .frame(width: 22, height: 22)

            // Bound through the store so an edit persists on every keystroke,
            // rather than needing a separate save step.
            TextField(
                "Entry",
                text: Binding(
                    get: { entry.label },
                    set: { wheelStore.rename(id: entry.id, to: $0) }
                )
            )
            .font(.body)
            .foregroundStyle(theme.textPrimary)
            .autocorrectionDisabled()
            .submitLabel(.done)
        }
        .listRowBackground(theme.background)
    }

    private func commitDraft() {
        let trimmed = draftEntry.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        wheelStore.add(trimmed)
        draftEntry = ""
        environment.hapticEngine.playFeedback(type: .light)
        // Keep focus so a list can be typed in without reaching for the field.
        isAddFieldFocused = true
    }

    private func startGame() {
        isAddFieldFocused = false
        environment.hapticEngine.playFeedback(type: .heavy)
        path.append(.game(.pinwheel))
    }
}

#Preview {
    NavigationStack {
        WheelSetupView(path: .constant([]))
            .environment(AppEnvironment())
            .environment(GameState())
            .environment(WheelStore())
    }
}
