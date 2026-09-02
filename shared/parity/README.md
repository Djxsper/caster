# Keeping the two apps in step

Caster is written twice. `Caster/` is SwiftUI with a UIKit multi-touch layer,
Core Haptics and AVFoundation; `android/` is Kotlin with Jetpack Compose. They
share no code, and that is deliberate — see the Android section of the root
README for why the tools that promise otherwise do not survive contact with this
particular app.

The cost of that decision is drift. A window widened on one platform and not the
other makes the same game play differently on the two phones, and nothing fails.
Nobody notices until somebody plays both.

`golden.json` is the fixture that makes drift fail a build. Neither app owns it.

## What is in it

| Section | What it pins |
|---|---|
| `seatColors` | The eight seat colours, in order |
| `spreadRules` | The saturation/brightness/hue-stride rules for wheels past eight |
| `timing` | Every constant that decides how a round feels |
| `draw` | The invariants of the weighted draw — not a fixed sequence, since it is random by design |
| `roster` | Player limits and the blank-name rule |

## How to change a number

1. Change it in `golden.json`.
2. Change it in **both** apps.
3. Both suites go green again.

Do not edit the fixture to make a failing test pass. A red parity test means the
two apps disagree, and the fixture is the referee, not the scoreboard.

## Status

- **Android** reads it in
  `android/app/src/test/java/com/jesperhaafkes/caster/ParityTest.kt`, against
  `domain/GameTuning.kt`. The constants were hoisted out of the individual
  screens specifically so a test could see them.
- **iOS does not read it yet.** The Xcode project has no test target at all — a
  gap worth closing on its own merits, since `Caster/Domain` is pure logic with
  no UI framework anywhere near it. Until then this is one side of a handshake:
  it stops Android drifting from the agreed numbers, but it cannot stop iOS from
  moving.

## What it cannot cover

Feel is not a constant. Haptic character, audio latency and the resolution of a
touch timestamp differ between the platforms as a matter of hardware — Android
reports `MotionEvent` times in whole milliseconds where `UITouch.timestamp` is a
sub-millisecond double, so Uppercut ties are simply more common on Android. Those
are documented differences, not drift, and no fixture will close them.
