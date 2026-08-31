import Foundation
import Observation

/// One person at the table. A named type rather than a bare `String` so a
/// `ForEach` row and its text field keep stable identity even when two people
/// share a name.
struct RosterMember: Identifiable, Hashable, Codable {
    var id = UUID()
    var name: String

    init(id: UUID = UUID(), name: String) {
        self.id = id
        self.name = name
    }
}

/// A named group. Keeping several around is the whole point: the flatmates, the
/// five-a-side squad and a birthday party are three different tables, and
/// switching between them beats retyping twelve names.
struct SavedRoster: Identifiable, Hashable, Codable {
    var id = UUID()
    var name: String
    var members: [RosterMember]

    init(id: UUID = UUID(), name: String, members: [RosterMember] = []) {
        self.id = id
        self.name = name
        self.members = members
    }

    var canPlay: Bool { members.count >= PlayerLimits.minimum }
}

/// Every saved roster plus which one is at the table, persisted so nothing is
/// retyped — not between launches, and not between one screen and the next.
///
/// The deliberate twin of `WheelStore`: same shape, same storage strategy, same
/// editing verbs. Names used to live in a screen's `@State`, which meant
/// leaving that screen — on purpose or by a stray back-swipe — silently threw
/// away everything typed into it.
@Observable
@MainActor
final class RosterStore {
    private static let storageKey = "caster.rosters"
    private static let selectionKey = "caster.rosters.selected"

    private(set) var rosters: [SavedRoster] = []
    private(set) var selectedID: UUID?

    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        load()
    }

    // MARK: - The roster at the table

    var selectedRoster: SavedRoster? {
        rosters.first { $0.id == selectedID }
    }

    var selectedName: String {
        selectedRoster?.name ?? "Players"
    }

    var members: [RosterMember] {
        selectedRoster?.members ?? []
    }

    var names: [String] {
        members.map(\.name)
    }

    var canPlay: Bool {
        members.count >= PlayerLimits.minimum
    }

    /// Unlike the wheel, a roster is bounded: the games address people by seat
    /// and `GameState` clamps to `PlayerLimits.maximum` anyway, so the editor
    /// says so up front rather than accepting a name it would later drop.
    var isFull: Bool {
        members.count >= PlayerLimits.maximum
    }

    /// The last roster cannot be deleted — there always has to be one to edit.
    var canDeleteRoster: Bool {
        rosters.count > 1
    }

    // MARK: - Members

    /// - Returns: whether the name was taken. A full roster refuses it.
    @discardableResult
    func add(_ name: String) -> Bool {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, !isFull else { return false }
        updateSelected { $0.members.append(RosterMember(name: trimmed)) }
        return true
    }

    func rename(id: UUID, to name: String) {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        updateSelected { roster in
            guard let index = roster.members.firstIndex(where: { $0.id == id }) else { return }
            // An emptied field removes the row rather than seating a blank name.
            if trimmed.isEmpty {
                roster.members.remove(at: index)
            } else {
                roster.members[index].name = trimmed
            }
        }
    }

    /// Written by hand rather than with `remove(atOffsets:)`: that helper is
    /// declared in SwiftUI, and this layer stays free of the UI framework.
    func remove(at offsets: IndexSet) {
        updateSelected { roster in
            for index in offsets.sorted(by: >) where roster.members.indices.contains(index) {
                roster.members.remove(at: index)
            }
        }
    }

    func remove(id: UUID) {
        updateSelected { $0.members.removeAll { $0.id == id } }
    }

    func move(from source: IndexSet, to destination: Int) {
        updateSelected { roster in
            let sourceIndices: [Int] = source.sorted()
            let moving = sourceIndices.compactMap {
                roster.members.indices.contains($0) ? roster.members[$0] : nil
            }
            guard !moving.isEmpty else { return }

            // Every removed row that sat above the drop point shifts the gap up
            // by one, so the raw destination has to be walked back by that many.
            let removedBefore = sourceIndices.filter { $0 < destination }.count
            for index in sourceIndices.reversed() where roster.members.indices.contains(index) {
                roster.members.remove(at: index)
            }
            let insertion = min(max(0, destination - removedBefore), roster.members.count)
            roster.members.insert(contentsOf: moving, at: insertion)
        }
    }

    func replaceAll(with names: [String]) {
        updateSelected { roster in
            roster.members = names
                .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
                .filter { !$0.isEmpty }
                .prefix(PlayerLimits.maximum)
                .map { RosterMember(name: $0) }
        }
    }

    func resetToDefaults() {
        replaceAll(with: Self.starterNames)
    }

    // MARK: - Rosters

    func select(_ id: UUID) {
        guard rosters.contains(where: { $0.id == id }) else { return }
        selectedID = id
        save()
    }

    @discardableResult
    func createRoster(named name: String) -> UUID {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        let roster = SavedRoster(name: trimmed.isEmpty ? nextDefaultName() : trimmed)
        rosters.append(roster)
        selectedID = roster.id
        save()
        return roster.id
    }

    func renameSelected(to name: String) {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        updateSelected { $0.name = trimmed }
    }

    /// Copies the roster at the table and switches to the copy. Member ids are
    /// minted fresh so the two rosters never share identity.
    func duplicateSelected() {
        guard let roster = selectedRoster else { return }
        let copy = SavedRoster(
            name: "\(roster.name) copy",
            members: roster.members.map { RosterMember(name: $0.name) }
        )
        rosters.append(copy)
        selectedID = copy.id
        save()
    }

    func deleteSelected() {
        guard canDeleteRoster, let index = rosters.firstIndex(where: { $0.id == selectedID }) else {
            return
        }
        rosters.remove(at: index)
        selectedID = rosters[min(index, rosters.count - 1)].id
        save()
    }

    // MARK: - Storage

    private func updateSelected(_ body: (inout SavedRoster) -> Void) {
        guard let index = rosters.firstIndex(where: { $0.id == selectedID }) else { return }
        body(&rosters[index])
        save()
    }

    private func nextDefaultName() -> String {
        var number = rosters.count + 1
        let taken = Set(rosters.map(\.name))
        while taken.contains("Group \(number)") { number += 1 }
        return "Group \(number)"
    }

    /// No legacy migration: before this store existed the names lived in a
    /// view's `@State` and were never written anywhere to migrate from.
    private func load() {
        rosters = decodeStoredRosters() ?? [Self.starterRoster()]

        if let raw = defaults.string(forKey: Self.selectionKey),
           let id = UUID(uuidString: raw),
           rosters.contains(where: { $0.id == id }) {
            selectedID = id
        } else {
            selectedID = rosters.first?.id
        }
    }

    private func decodeStoredRosters() -> [SavedRoster]? {
        guard let data = defaults.data(forKey: Self.storageKey),
              let decoded = try? JSONDecoder().decode([SavedRoster].self, from: data),
              !decoded.isEmpty else { return nil }
        return decoded
    }

    private func save() {
        if let data = try? JSONEncoder().encode(rosters) {
            defaults.set(data, forKey: Self.storageKey)
        }
        defaults.set(selectedID?.uuidString, forKey: Self.selectionKey)
    }

    private static func starterRoster() -> SavedRoster {
        SavedRoster(name: "Players", members: starterNames.map { RosterMember(name: $0) })
    }

    /// The four numbered seats the setup screen used to open on, so a first run
    /// looks exactly as it always did.
    private static let starterNames = (1...4).map { "Player \($0)" }
}
