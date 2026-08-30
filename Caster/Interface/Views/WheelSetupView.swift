import SwiftUI

/// The pinwheel's entry editor. Wheels are saved and named, so a group can keep
/// the flatmates, the five-a-side squad and the chore list side by side instead
/// of retyping one over the other. Entries are unbounded on purpose — the wheel
/// scales itself to whatever is in the list.
struct WheelSetupView: View {
    /// Which name the alert is collecting. One alert serves both jobs.
    private enum NamePrompt {
        case newWheel
        case renameWheel
    }

    @Environment(AppEnvironment.self) private var environment
    @Environment(WheelStore.self) private var wheelStore
    @Environment(GameState.self) private var gameState
    @Environment(\.theme) private var theme
    @Binding var path: [Route]

    @State private var draftEntry = ""
    @State private var nameDraft = ""
    @State private var namePrompt: NamePrompt = .newWheel
    @State private var isNamePromptShown = false
    @State private var isDeleteConfirmShown = false
    @FocusState private var isAddFieldFocused: Bool

    var body: some View {
        ZStack {
            theme.background
                .ignoresSafeArea()

            VStack(spacing: 12) {
                wheelPicker
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
                optionsMenu
            }
        }
        .alert(namePromptTitle, isPresented: $isNamePromptShown) {
            TextField("Wheel name", text: $nameDraft)
                .textInputAutocapitalization(.words)
            Button("Cancel", role: .cancel) { }
            Button("Save", action: commitNamePrompt)
        }
        .alert("Delete this wheel?", isPresented: $isDeleteConfirmShown) {
            Button("Cancel", role: .cancel) { }
            Button("Delete", role: .destructive) { wheelStore.deleteSelected() }
        } message: {
            Text("\(wheelStore.selectedName) and its \(wheelStore.entries.count) entries will be removed.")
        }
    }

    // MARK: - Wheel switcher

    private var wheelPicker: some View {
        Menu {
            Picker("Saved wheels", selection: wheelSelection) {
                ForEach(wheelStore.wheels) { wheel in
                    Text("\(wheel.name)  ·  \(wheel.entries.count)")
                        .tag(wheel.id)
                }
            }

            Divider()

            Button {
                promptForName(.newWheel)
            } label: {
                Label("New wheel", systemImage: "plus.circle")
            }
        } label: {
            HStack(spacing: 10) {
                Image(systemName: "square.stack.3d.up")
                    .font(.body)
                    .foregroundStyle(theme.accent)

                VStack(alignment: .leading, spacing: 2) {
                    Text(wheelStore.selectedName)
                        .font(.system(.headline, design: .rounded))
                        .foregroundStyle(theme.textPrimary)
                        .lineLimit(1)

                    Text(entryCountLabel)
                        .font(.caption)
                        .foregroundStyle(theme.textSecondary)
                }

                Spacer(minLength: 8)

                Image(systemName: "chevron.up.chevron.down")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(theme.textSecondary)
            }
            .padding(.vertical, 12)
            .padding(.horizontal, 14)
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
        .padding(.horizontal, 16)
    }

    /// The picker writes straight through to the store, which persists the
    /// choice — so the app reopens on the wheel that was last in play.
    private var wheelSelection: Binding<UUID> {
        Binding(
            get: { wheelStore.selectedID ?? UUID() },
            set: { wheelStore.select($0) }
        )
    }

    private var entryCountLabel: String {
        let count = wheelStore.entries.count
        switch count {
        case 0: return "No entries yet"
        case 1: return "1 entry — needs two to spin"
        default: return "\(count) entries"
        }
    }

    private var optionsMenu: some View {
        Menu {
            Section {
                Button {
                    promptForName(.renameWheel)
                } label: {
                    Label("Rename wheel", systemImage: "pencil")
                }

                Button {
                    wheelStore.duplicateSelected()
                } label: {
                    Label("Duplicate wheel", systemImage: "plus.square.on.square")
                }

                Button(role: .destructive) {
                    isDeleteConfirmShown = true
                } label: {
                    Label("Delete wheel", systemImage: "trash")
                }
                .disabled(!wheelStore.canDeleteWheel)
            }

            Section {
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
                    Label("Remove all entries", systemImage: "xmark.circle")
                }
            }
        } label: {
            Image(systemName: "ellipsis.circle")
        }
    }

    // MARK: - Entries

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
            .disabled(isDraftEmpty)
            .opacity(isDraftEmpty ? 0.45 : 1)
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

    // MARK: - Actions

    private var isDraftEmpty: Bool {
        draftEntry.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    private var namePromptTitle: String {
        namePrompt == .newWheel ? "New wheel" : "Rename wheel"
    }

    private func promptForName(_ prompt: NamePrompt) {
        namePrompt = prompt
        nameDraft = prompt == .renameWheel ? wheelStore.selectedName : ""
        isNamePromptShown = true
    }

    private func commitNamePrompt() {
        switch namePrompt {
        case .newWheel:
            wheelStore.createWheel(named: nameDraft)
            isAddFieldFocused = true
        case .renameWheel:
            wheelStore.renameSelected(to: nameDraft)
        }
        nameDraft = ""
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
