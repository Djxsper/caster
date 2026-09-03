import Foundation
import Observation

/// One slice of the pinwheel. A named type rather than a bare `String` so a
/// `ForEach` and the spin animation both have stable identity even when two
/// entries carry the same text.
struct WheelEntry: Identifiable, Hashable, Codable {
    var id = UUID()
    var label: String

    init(id: UUID = UUID(), label: String) {
        self.id = id
        self.label = label
    }
}

/// A named wheel. Groups keep several of these around — the flatmates, the
/// five-a-side squad, a list of chores — and switch between them rather than
/// retyping one list into another.
struct SavedWheel: Identifiable, Hashable, Codable {
    var id = UUID()
    var name: String
    var entries: [WheelEntry]

    init(id: UUID = UUID(), name: String, entries: [WheelEntry] = []) {
        self.id = id
        self.name = name
        self.entries = entries
    }

    var canSpin: Bool { entries.count >= 2 }
}

/// Every saved wheel plus which one is in play, persisted so nothing is retyped
/// between launches. Entry counts are deliberately unbounded — the wheel scales
/// its own slices, type size and label density to whatever is on it.
@Observable
@MainActor
final class WheelStore {
    private static let storageKey = "caster.wheels"
    private static let selectionKey = "caster.wheels.selected"
    /// The single-list format this replaced. Read once, then migrated away.
    private static let legacyEntriesKey = "caster.wheel.entries"

    private(set) var wheels: [SavedWheel] = []
    private(set) var selectedID: UUID?

    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        load()
    }

    // MARK: - The wheel in play

    var selectedWheel: SavedWheel? {
        wheels.first { $0.id == selectedID }
    }

    var selectedName: String {
        selectedWheel?.name ?? "Wheel"
    }

    var entries: [WheelEntry] {
        selectedWheel?.entries ?? []
    }

    var labels: [String] {
        entries.map(\.label)
    }

    var canSpin: Bool {
        entries.count >= 2
    }

    /// The last wheel cannot be deleted — there always has to be one to edit.
    var canDeleteWheel: Bool {
        wheels.count > 1
    }

    /// How many wheels may be saved. Set from `EntitlementStore` at launch and
    /// raised to `.max` by Plus; this layer knows nothing about purchases, in
    /// the same way it knows nothing about SwiftUI.
    ///
    /// It caps *creating* a wheel and nothing else. A library already over the
    /// line — from a build that predates the cap — is never hidden, trimmed or
    /// deleted, only frozen at its current size.
    var capacity: Int = FreeLimits.savedWheels

    var canCreateWheel: Bool {
        wheels.count < capacity
    }

    // MARK: - Entries

    func add(_ label: String) {
        let trimmed = label.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        updateSelected { $0.entries.append(WheelEntry(label: trimmed)) }
    }

    func rename(id: UUID, to label: String) {
        let trimmed = label.trimmingCharacters(in: .whitespacesAndNewlines)
        updateSelected { wheel in
            guard let index = wheel.entries.firstIndex(where: { $0.id == id }) else { return }
            // An emptied field removes the row rather than leaving a blank slice.
            if trimmed.isEmpty {
                wheel.entries.remove(at: index)
            } else {
                wheel.entries[index].label = trimmed
            }
        }
    }

    /// Written by hand rather than with `remove(atOffsets:)`: that helper is
    /// declared in SwiftUI, and this layer stays free of the UI framework.
    func remove(at offsets: IndexSet) {
        updateSelected { wheel in
            for index in offsets.sorted(by: >) where wheel.entries.indices.contains(index) {
                wheel.entries.remove(at: index)
            }
        }
    }

    func remove(id: UUID) {
        updateSelected { $0.entries.removeAll { $0.id == id } }
    }

    func move(from source: IndexSet, to destination: Int) {
        updateSelected { wheel in
            let sourceIndices: [Int] = source.sorted()
            let moving = sourceIndices.compactMap {
                wheel.entries.indices.contains($0) ? wheel.entries[$0] : nil
            }
            guard !moving.isEmpty else { return }

            // Every removed row that sat above the drop point shifts the gap up
            // by one, so the raw destination has to be walked back by that many.
            let removedBefore = sourceIndices.filter { $0 < destination }.count
            for index in sourceIndices.reversed() where wheel.entries.indices.contains(index) {
                wheel.entries.remove(at: index)
            }
            let insertion = min(max(0, destination - removedBefore), wheel.entries.count)
            wheel.entries.insert(contentsOf: moving, at: insertion)
        }
    }

    func replaceAll(with labels: [String]) {
        updateSelected { wheel in
            wheel.entries = labels
                .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
                .filter { !$0.isEmpty }
                .map { WheelEntry(label: $0) }
        }
    }

    func resetToDefaults() {
        replaceAll(with: Self.starterLabels)
    }

    // MARK: - Wheels

    func select(_ id: UUID) {
        guard wheels.contains(where: { $0.id == id }) else { return }
        selectedID = id
        save()
    }

    /// - Returns: the new wheel's id, or nil when the library is full. The
    ///   caller turns nil into the Plus sheet; the store only ever says no.
    @discardableResult
    func createWheel(named name: String) -> UUID? {
        guard canCreateWheel else { return nil }
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        let wheel = SavedWheel(name: trimmed.isEmpty ? nextDefaultName() : trimmed)
        wheels.append(wheel)
        selectedID = wheel.id
        save()
        return wheel.id
    }

    func renameSelected(to name: String) {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        updateSelected { $0.name = trimmed }
    }

    /// Copies the wheel in play and switches to the copy. Entry ids are minted
    /// fresh so the two wheels never share identity.
    @discardableResult
    func duplicateSelected() -> Bool {
        guard canCreateWheel, let wheel = selectedWheel else { return false }
        let copy = SavedWheel(
            name: "\(wheel.name) copy",
            entries: wheel.entries.map { WheelEntry(label: $0.label) }
        )
        wheels.append(copy)
        selectedID = copy.id
        save()
        return true
    }

    func deleteSelected() {
        guard canDeleteWheel, let index = wheels.firstIndex(where: { $0.id == selectedID }) else {
            return
        }
        wheels.remove(at: index)
        selectedID = wheels[min(index, wheels.count - 1)].id
        save()
    }

    // MARK: - Storage

    private func updateSelected(_ body: (inout SavedWheel) -> Void) {
        guard let index = wheels.firstIndex(where: { $0.id == selectedID }) else { return }
        body(&wheels[index])
        save()
    }

    private func nextDefaultName() -> String {
        var number = wheels.count + 1
        let taken = Set(wheels.map(\.name))
        while taken.contains("Wheel \(number)") { number += 1 }
        return "Wheel \(number)"
    }

    private func load() {
        wheels = decodeStoredWheels() ?? migratedLegacyWheels() ?? [Self.starterWheel()]

        if let raw = defaults.string(forKey: Self.selectionKey),
           let id = UUID(uuidString: raw),
           wheels.contains(where: { $0.id == id }) {
            selectedID = id
        } else {
            selectedID = wheels.first?.id
        }
    }

    private func decodeStoredWheels() -> [SavedWheel]? {
        guard let data = defaults.data(forKey: Self.storageKey),
              let decoded = try? JSONDecoder().decode([SavedWheel].self, from: data),
              !decoded.isEmpty else { return nil }
        return decoded
    }

    /// Carries a list saved by the single-wheel build into a named wheel, so an
    /// upgrade does not silently drop what someone already typed in.
    private func migratedLegacyWheels() -> [SavedWheel]? {
        guard let data = defaults.data(forKey: Self.legacyEntriesKey),
              let entries = try? JSONDecoder().decode([WheelEntry].self, from: data),
              !entries.isEmpty else { return nil }

        defaults.removeObject(forKey: Self.legacyEntriesKey)
        return [SavedWheel(name: "My Wheel", entries: entries)]
    }

    private func save() {
        if let data = try? JSONEncoder().encode(wheels) {
            defaults.set(data, forKey: Self.storageKey)
        }
        defaults.set(selectedID?.uuidString, forKey: Self.selectionKey)
    }

    private static func starterWheel() -> SavedWheel {
        SavedWheel(name: "Party", entries: starterLabels.map { WheelEntry(label: $0) })
    }

    private static let starterLabels = ["Alex", "Bo", "Cleo", "Dax", "Eve", "Fin"]
}
