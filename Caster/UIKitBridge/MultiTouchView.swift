import SwiftUI
import UIKit

/// Exposes true multi-touch to SwiftUI. SwiftUI's own `DragGesture` reports a
/// single finger, which is useless for a game where up to eight thumbs are on
/// the glass at once.
struct MultiTouchView: UIViewRepresentable {
    /// Set once at creation; the tracker is a reference type, so touch updates
    /// flow through it rather than through `@Binding` write-backs.
    let tracker: TouchTracker

    func makeUIView(context: Context) -> MultiTouchWrapper {
        let wrapper = MultiTouchWrapper()
        wrapper.tracker = tracker
        return wrapper
    }

    func updateUIView(_ uiView: MultiTouchWrapper, context: Context) {
        uiView.tracker = tracker
    }
}

final class MultiTouchWrapper: UIView {
    weak var tracker: TouchTracker?

    /// UIKit recycles `UITouch` instances, so map each live touch to an id we
    /// mint ourselves and drop the mapping the moment the touch ends.
    private var identifiers: [ObjectIdentifier: Int] = [:]
    private var nextIdentifier = 0

    override init(frame: CGRect) {
        super.init(frame: frame)
        isMultipleTouchEnabled = true
        isUserInteractionEnabled = true
        backgroundColor = .clear
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        super.touchesBegan(touches, with: event)
        for touch in touches {
            let key = ObjectIdentifier(touch)
            // Reuse the id if this recycled object is somehow still mapped.
            let id = identifiers[key] ?? mintIdentifier()
            identifiers[key] = id
            tracker?.touchBegan(
                id: id,
                location: touch.location(in: self),
                timestamp: touch.timestamp
            )
        }
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        super.touchesMoved(touches, with: event)
        for touch in touches {
            guard let id = identifiers[ObjectIdentifier(touch)] else { continue }
            tracker?.touchMoved(id: id, location: touch.location(in: self))
        }
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        super.touchesEnded(touches, with: event)
        finish(touches)
    }

    /// Cancellation (an incoming call, a system gesture) has to clean up too —
    /// the original only handled `ended`, leaking a stuck "finger down".
    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
        super.touchesCancelled(touches, with: event)
        finish(touches)
    }

    private func finish(_ touches: Set<UITouch>) {
        for touch in touches {
            let key = ObjectIdentifier(touch)
            guard let id = identifiers.removeValue(forKey: key) else { continue }
            tracker?.touchEnded(id: id, location: touch.location(in: self))
        }
    }

    private func mintIdentifier() -> Int {
        defer { nextIdentifier &+= 1 }
        return nextIdentifier
    }
}
