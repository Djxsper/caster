import SwiftUI
import UIKit

/// Exposes true multi-touch to SwiftUI. SwiftUI's own `DragGesture` reports a
/// single finger, which is useless for a game where eight thumbs are on the
/// glass at once.
struct MultiTouchView: UIViewRepresentable {
    /// Set once at creation; the arena is a reference type, so touch updates
    /// flow through it rather than through `@Binding` write-backs.
    let arena: TouchArena
    /// Turns off the interactive swipe-back gesture while this surface is on
    /// screen. A thumb parked near the left edge otherwise pops the screen
    /// mid-round instead of registering as a player.
    var blocksEdgeSwipe = true

    func makeUIView(context: Context) -> MultiTouchWrapper {
        let wrapper = MultiTouchWrapper()
        wrapper.arena = arena
        wrapper.blocksEdgeSwipe = blocksEdgeSwipe
        return wrapper
    }

    func updateUIView(_ uiView: MultiTouchWrapper, context: Context) {
        uiView.arena = arena
        uiView.blocksEdgeSwipe = blocksEdgeSwipe
    }
}

final class MultiTouchWrapper: UIView {
    weak var arena: TouchArena?
    var blocksEdgeSwipe = true

    /// UIKit recycles `UITouch` instances, so map each live touch to an id we
    /// mint ourselves and drop the mapping the moment the touch ends.
    private var identifiers: [ObjectIdentifier: Int] = [:]
    private var nextIdentifier = 0
    /// Held weakly so the gesture can be handed back even after this view has
    /// left the hierarchy and lost its responder chain.
    private weak var suppressedNavigationController: UINavigationController?

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

    override func didMoveToWindow() {
        super.didMoveToWindow()
        if window == nil {
            restoreEdgeSwipe()
        } else if blocksEdgeSwipe {
            suppressEdgeSwipe()
        }
    }

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        super.touchesBegan(touches, with: event)
        for touch in touches {
            let key = ObjectIdentifier(touch)
            // Reuse the id if this recycled object is somehow still mapped.
            let id = identifiers[key] ?? mintIdentifier()
            identifiers[key] = id
            arena?.touchBegan(
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
            arena?.touchMoved(id: id, location: touch.location(in: self))
        }
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        super.touchesEnded(touches, with: event)
        finish(touches)
    }

    /// Cancellation (an incoming call, a system gesture) has to clean up too —
    /// handling only `ended` leaks a stuck "finger down".
    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
        super.touchesCancelled(touches, with: event)
        finish(touches)
    }

    private func finish(_ touches: Set<UITouch>) {
        for touch in touches {
            let key = ObjectIdentifier(touch)
            guard let id = identifiers.removeValue(forKey: key) else { continue }
            arena?.touchEnded(
                id: id,
                location: touch.location(in: self),
                timestamp: touch.timestamp
            )
        }
    }

    private func mintIdentifier() -> Int {
        defer { nextIdentifier &+= 1 }
        return nextIdentifier
    }

    private func suppressEdgeSwipe() {
        guard suppressedNavigationController == nil,
              let navigationController = enclosingNavigationController() else { return }
        navigationController.interactivePopGestureRecognizer?.isEnabled = false
        suppressedNavigationController = navigationController
    }

    private func restoreEdgeSwipe() {
        suppressedNavigationController?.interactivePopGestureRecognizer?.isEnabled = true
        suppressedNavigationController = nil
    }

    /// Walks the responder chain rather than the view hierarchy: SwiftUI hosts
    /// each screen in its own controller, and the navigation controller is only
    /// reachable from there.
    private func enclosingNavigationController() -> UINavigationController? {
        var responder: UIResponder? = self
        while let current = responder {
            if let navigationController = current as? UINavigationController {
                return navigationController
            }
            if let controller = current as? UIViewController,
               let navigationController = controller.navigationController {
                return navigationController
            }
            responder = current.next
        }
        return nil
    }
}
