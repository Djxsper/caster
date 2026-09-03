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

Its commercial twin is [`../monetization/offering.json`](../monetization/offering.json),
which pins the free limits, the ad pacing and the product identifier. Same
reasoning, different failure: the two apps quietly offering different deals.

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
- **iOS** now has a test target — `CasterTests`, run by
  `.github/workflows/ios-simulator.yml` on every push. It reads the
  monetization fixture in
  [`OfferingParityTests`](../../CasterTests/OfferingParityTests.swift), via
  `#filePath` rather than a bundled copy, so there is one file in the repository
  and no build step that could let the two drift apart again.
- **`golden.json` itself is still read only by Android.** The handshake is half
  made: iOS has somewhere to put the test now, but the tuning constants in
  `Caster/Domain` have not been hoisted the way `GameTuning.kt` was. Until they
  are, this fixture stops Android drifting from the agreed numbers and cannot
  stop iOS from moving.

## What it cannot cover

Feel is not a constant. Haptic character, audio latency and the resolution of a
touch timestamp differ between the platforms as a matter of hardware — Android
reports `MotionEvent` times in whole milliseconds where `UITouch.timestamp` is a
sub-millisecond double, so Uppercut ties are simply more common on Android. Those
are documented differences, not drift, and no fixture will close them.
