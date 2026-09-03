import SwiftUI

/// The roster editor, in the same shape as the pinwheel's: named groups you
/// switch between, and every keystroke written straight to `RosterStore`.
///
/// Deliberately has no navigation chrome of its own beyond a toolbar, so the
/// same editor serves both the setup screen and the sheet a game opens — the
/// list of names is the same list wherever you reach it from.
struct RosterEditor: View {
    /// Which name the alert is collecting. One alert serves both jobs.
    private enum NamePrompt {
        case newRoster
        case renameRoster
    }

    @Environment(AppEnvironment.self) private var environment
    @Environment(RosterStore.self) private var rosterStore
    @Environment(WheelStore.self) private var wheelStore
    @Environment(EntitlementStore.self) private var entitlements
    @Environment(\.theme) private var theme

    @State private var draftName = ""
    @State private var groupNameDraft = ""
    @State private var namePrompt: NamePrompt = .newRoster
    @State private var isNamePromptShown = false
    @State private var isDeleteConfirmShown = false
    @State private var plusPrompt: PlusPrompt?
    @FocusState private var isAddFieldFocused: Bool

    var body: some View {
        VStack(spacing: 12) {
            rosterPicker
            addRow
            memberList
        }
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                EditButton()
                optionsMenu
            }
        }
        .sheet(item: $plusPrompt) { PlusView(prompt: $0) }
        .alert(namePromptTitle, isPresented: $isNamePromptShown) {
            TextField("Group name", text: $groupNameDraft)
                .textInputAutocapitalization(.words)
            Button("Cancel", role: .cancel) { }
            Button("Save", action: commitNamePrompt)
        } message: {
            Text("Groups are kept separately, so one does not overwrite another.")
        }
        .alert("Delete this group?", isPresented: $isDeleteConfirmShown) {
            Button("Cancel", role: .cancel) { }
            Button("Delete", role: .destructive) { rosterStore.deleteSelected() }
        } message: {
            Text("\(rosterStore.selectedName) and its \(rosterStore.members.count) names will be removed.")
        }
    }

    // MARK: - Group switcher

    private var rosterPicker: some View {
        Menu {
            Picker("Saved groups", selection: rosterSelection) {
                ForEach(rosterStore.rosters) { roster in
                    Text("\(roster.name)  ·  \(roster.members.count)")
                        .tag(roster.id)
                }
            }

            Divider()

            Button {
                // Checked before the name prompt, not after: being asked to
                // name a group and only then told it cannot be made is the
                // rudest possible order to do this in.
                if rosterStore.canCreateRoster {
                    promptForName(.newRoster)
                } else {
                    plusPrompt = .rosterLimit
                }
            } label: {
                Label("New group", systemImage: "plus.circle")
            }
        } label: {
            HStack(spacing: 10) {
                Image(systemName: "person.2.fill")
                    .font(.body)
                    .foregroundStyle(theme.accent)

                VStack(alignment: .leading, spacing: 2) {
                    Text(rosterStore.selectedName)
                        .font(.system(.headline, design: .rounded))
                        .foregroundStyle(theme.textPrimary)
                        .lineLimit(1)

                    Text(memberCountLabel)
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
    /// choice — so the app reopens on the group that was last at the table.
    private var rosterSelection: Binding<UUID> {
        Binding(
            get: { rosterStore.selectedID ?? UUID() },
            set: { rosterStore.select($0) }
        )
    }

    private var memberCountLabel: String {
        let count = rosterStore.members.count
        switch count {
        case 0: return "No players yet"
        case 1: return "1 player — needs two to start"
        case PlayerLimits.maximum: return "\(count) players — full"
        default: return "\(count) players"
        }
    }

    private var optionsMenu: some View {
        Menu {
            Section {
                Button {
                    promptForName(.renameRoster)
                } label: {
                    Label("Rename group", systemImage: "pencil")
                }

                Button {
                    if !rosterStore.duplicateSelected() {
                        plusPrompt = .rosterLimit
                    }
                } label: {
                    Label("Duplicate group", systemImage: "plus.square.on.square")
                }

                Button(role: .destructive) {
                    isDeleteConfirmShown = true
                } label: {
                    Label("Delete group", systemImage: "trash")
                }
                .disabled(!rosterStore.canDeleteRoster)
            }

            Section {
                Button {
                    rosterStore.replaceAll(with: wheelStore.labels)
                } label: {
                    Label("Use wheel entries", systemImage: "arrow.triangle.2.circlepath")
                }
                .disabled(wheelStore.labels.isEmpty)

                Button {
                    rosterStore.resetToDefaults()
                } label: {
                    Label("Reset to numbered players", systemImage: "arrow.counterclockwise")
                }

                Button(role: .destructive) {
                    rosterStore.replaceAll(with: [])
                } label: {
                    Label("Remove all names", systemImage: "xmark.circle")
                }
            }
        } label: {
            Image(systemName: "ellipsis.circle")
        }
    }

    // MARK: - Members

    private var addRow: some View {
        HStack(spacing: 10) {
            TextField(addFieldPrompt, text: $draftName)
                .font(.body)
                .foregroundStyle(theme.textPrimary)
                .textInputAutocapitalization(.words)
                .autocorrectionDisabled()
                .submitLabel(.done)
                .focused($isAddFieldFocused)
                .onSubmit(commitDraft)
                .disabled(rosterStore.isFull)
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
            .disabled(!canCommitDraft)
            .opacity(canCommitDraft ? 1 : 0.45)
        }
        .padding(.horizontal, 16)
    }

    @ViewBuilder
    private var memberList: some View {
        if rosterStore.members.isEmpty {
            VStack(spacing: 8) {
                Spacer()
                Image(systemName: "person.badge.plus")
                    .font(.system(size: 44))
                    .foregroundStyle(theme.border)
                Text("Add at least two players to start.")
                    .font(.callout)
                    .foregroundStyle(theme.textSecondary)
                Spacer()
            }
            .frame(maxWidth: .infinity)
        } else {
            List {
                ForEach(rosterStore.members) { member in
                    memberRow(member)
                }
                .onDelete { rosterStore.remove(at: $0) }
                .onMove { rosterStore.move(from: $0, to: $1) }
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
        }
    }

    /// Where this person will actually sit, or nil when they are sitting out.
    ///
    /// Taken from the *active* list rather than from the row's position, because
    /// that is what the games seat. Someone switched off does not take a seat,
    /// and everybody below them moves up a colour — so reading the index from
    /// the full list would make the swatches lie about which ring is whose.
    private func seatIndex(of member: RosterMember) -> Int? {
        rosterStore.activeMembers.firstIndex { $0.id == member.id }
    }

    private func memberRow(_ member: RosterMember) -> some View {
        let seat = seatIndex(of: member)

        return HStack(spacing: 12) {
            // The seat colour the games will actually use, so the list doubles
            // as a key to the rings and the potato. Hollow when they are out.
            Group {
                if let seat {
                    Circle().fill(theme.playerColor(for: seat))
                } else {
                    Circle().stroke(theme.border, lineWidth: 2)
                }
            }
            .frame(width: 22, height: 22)

            // Bound through the store so an edit persists on every keystroke,
            // rather than needing a separate save step.
            TextField(
                "Name",
                text: Binding(
                    get: { member.name },
                    set: { rosterStore.rename(id: member.id, to: $0) }
                )
            )
            .font(.body)
            // Dimmed when they are sitting out. One `foregroundStyle` and not
            // two: the inner modifier wins, so a second one further down the
            // chain would never be seen.
            .foregroundStyle(seat == nil ? theme.textSecondary : theme.textPrimary)
            .textInputAutocapitalization(.words)
            .autocorrectionDisabled()
            .submitLabel(.done)

            activeToggle(for: member, isSeated: seat != nil)
        }
        .listRowBackground(theme.background)
    }

    /// The far-right switch from the notes: turn somebody off for tonight
    /// without deleting them and retyping them next week.
    ///
    /// Shown to everyone rather than hidden behind Plus. A control nobody can
    /// see is a feature nobody knows they are missing, and tapping it explains
    /// itself — which is a fairer way to sell something than a list of bullets.
    private func activeToggle(for member: RosterMember, isSeated: Bool) -> some View {
        Button {
            guard entitlements.hasActiveMemberToggle else {
                plusPrompt = .activeMembers
                return
            }
            environment.hapticEngine.playFeedback(type: .light)
            rosterStore.setActive(id: member.id, !member.isActive)
        } label: {
            Image(systemName: isSeated ? "checkmark.circle.fill" : "circle")
                .font(.title3)
                .foregroundStyle(isSeated ? theme.accent : theme.border)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(isSeated ? "\(member.name) is playing" : "\(member.name) is sitting out")
        .accessibilityAddTraits(isSeated ? [.isButton, .isSelected] : [.isButton])
    }

    // MARK: - Actions

    private var addFieldPrompt: String {
        rosterStore.isFull ? "\(PlayerLimits.maximum) players is the maximum" : "Add a player"
    }

    private var canCommitDraft: Bool {
        !rosterStore.isFull && !draftName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    private var namePromptTitle: String {
        namePrompt == .newRoster ? "New group" : "Rename group"
    }

    private func promptForName(_ prompt: NamePrompt) {
        namePrompt = prompt
        groupNameDraft = prompt == .renameRoster ? rosterStore.selectedName : ""
        isNamePromptShown = true
    }

    private func commitNamePrompt() {
        switch namePrompt {
        case .newRoster:
            if rosterStore.createRoster(named: groupNameDraft) == nil {
                plusPrompt = .rosterLimit
            } else {
                isAddFieldFocused = true
            }
        case .renameRoster:
            rosterStore.renameSelected(to: groupNameDraft)
        }
        groupNameDraft = ""
    }

    private func commitDraft() {
        guard canCommitDraft else { return }
        rosterStore.add(draftName)
        draftName = ""
        environment.hapticEngine.playFeedback(type: .light)
        // Keep focus so twelve names can be typed straight through without
        // reaching back for the field between each one.
        isAddFieldFocused = !rosterStore.isFull
    }
}

/// The roster in a sheet, so names can be fixed from inside a game without
/// backing out of the round to do it.
struct RosterSheet: View {
    @Environment(\.theme) private var theme
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ZStack {
                theme.background
                    .ignoresSafeArea()

                RosterEditor()
                    .padding(.top, 12)
                    .padding(.bottom, 12)
            }
            .navigationTitle("Players")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Done") { dismiss() }
                        .fontWeight(.semibold)
                }
            }
        }
    }
}

/// Opens the roster over a game. Pairs with `.rosterSheet(isPresented:)` on the
/// screen's own content: a `.sheet` attached to a view inside toolbar content
/// does not reliably present, so the presentation lives on the content and only
/// the trigger lives up here.
struct RosterToolbarButton: View {
    @Binding var isPresented: Bool

    var body: some View {
        Button {
            isPresented = true
        } label: {
            Image(systemName: "person.2")
        }
        .accessibilityLabel("Players")
    }
}

extension View {
    /// Presents the roster over this screen. Every edit is already saved by the
    /// time the sheet closes, so dismissing it — deliberately or with a stray
    /// swipe — can no longer cost anybody their names.
    func rosterSheet(isPresented: Binding<Bool>) -> some View {
        sheet(isPresented: isPresented) { RosterSheet() }
    }
}

#Preview {
    NavigationStack {
        RosterEditor()
            .environment(AppEnvironment())
            .environment(RosterStore())
            .environment(WheelStore())
            .environment(EntitlementStore())
            .environment(StoreService(entitlements: EntitlementStore()))
    }
}