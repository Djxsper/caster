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

/// The pinwheel's entry list, persisted so a group does not retype their names
/// every launch. There is deliberately no upper bound on the count — the wheel
/// scales its own type size and label density to fit whatever is in here.
@Observable
@MainActor
final class WheelStore {
    private static let storageKey = "caster.wheel.entries"

    private(set) var entries: [WheelEntry] = []

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        load()
    }

    private let defaults: UserDefaults

    var labels: [String] { entries.map(\.label) }
    var canSpin: Bool { entries.count >= 2 }

    func add(_ label: String) {
        let trimmed = label.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        entries.append(WheelEntry(label: trimmed))
        save()
    }

    func rename(id: UUID, to label: String) {
        let trimmed = label.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let index = entries.firstIndex(where: { $0.id == id }) else { return }
        // An emptied field removes the row rather than leaving a blank slice.
        if trimmed.isEmpty {
            entries.remove(at: index)
        } else {
            entries[index].label = trimmed
        }
        save()
    }

    /// Written by hand rather than with `remove(atOffsets:)`: that helper is
    /// declared in SwiftUI, and this layer stays free of the UI framework.
    func remove(at offsets: IndexSet) {
        for index in offsets.sorted(by: >) where entries.indices.contains(index) {
            entries.remove(at: index)
        }
        save()
    }

    func remove(id: UUID) {
        entries.removeAll { $0.id == id }
        save()
    }

    func move(from source: IndexSet, to destination: Int) {
        let sourceIndices: [Int] = source.sorted()
        let moving = sourceIndices.compactMap { entries.indices.contains($0) ? entries[$0] : nil }
        guard !moving.isEmpty else { return }

        // Every removed row that sat above the drop point shifts the gap up by
        // one, so the raw destination has to be walked back by that many.
        let removedBefore = sourceIndices.filter { $0 < destination }.count
        for index in sourceIndices.reversed() where entries.indices.contains(index) {
            entries.remove(at: index)
        }
        let insertion = min(max(0, destination - removedBefore), entries.count)
        entries.insert(contentsOf: moving, at: insertion)
        save()
    }

    func replaceAll(with labels: [String]) {
        entries = labels
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
            .map { WheelEntry(label: $0) }
        save()
    }

    func resetToDefaults() {
        replaceAll(with: Self.starterLabels)
    }

    // MARK: - Persistence

    private func load() {
        guard let data = defaults.data(forKey: Self.storageKey),
              let decoded = try? JSONDecoder().decode([WheelEntry].self, from: data),
              !decoded.isEmpty else {
            entries = Self.starterLabels.map { WheelEntry(label: $0) }
            return
        }
        entries = decoded
    }

    private func save() {
        guard let data = try? JSONEncoder().encode(entries) else { return }
        defaults.set(data, forKey: Self.storageKey)
    }

    private static let starterLabels = ["Alex", "Bo", "Cleo", "Dax", "Eve", "Fin"]
}
