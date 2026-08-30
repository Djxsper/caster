<div align="center">

<img src="docs/icon.png" width="110" alt="Caster app icon">

# Caster

**Six ways to let a phone decide.**

Pass-the-phone party games for settling who goes first, who buys the next round,
and who is doing the washing up. One device, everybody's fingers on the glass,
no accounts and no network.

[![Build](https://github.com/Djxsper/caster/actions/workflows/ios-simulator.yml/badge.svg)](https://github.com/Djxsper/caster/actions/workflows/ios-simulator.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-iOS%2017%2B-lightgrey.svg)](#install-on-iphone-or-ipad)

<img src="docs/screenshots/mode-select.png" width="200" alt="Game mode list">
<img src="docs/screenshots/pinwheel.png" width="200" alt="Pinwheel">
<img src="docs/screenshots/hot-potato.png" width="200" alt="Hot Potato">
<img src="docs/screenshots/tap-frenzy.png" width="200" alt="Tap Frenzy">

</div>

---

## The games

| | Mode | How it goes |
|---|---|---|
| 👆 | **Finger Picker** | Everyone holds a finger down. After three still seconds the app picks somebody — or splits the table into teams, or puts everyone in an order. |
| 🎡 | **Pinwheel** | Spin a wheel of names, chores, or anything else you type in. Unlimited entries, and wheels are saved and named so you can keep several. |
| ⏲️ | **Hot Potato** | A hidden fuse burns while the phone goes round. Tap to pass. Whoever is holding it when it blows loses. |
| ⚡ | **Uppercut** | Hold a finger. When the light flips and the tone hits, lift. Fastest wins, slowest loses, and going early ends it on the spot. |
| 🔨 | **Tap Frenzy** | Claim a circle and hammer it for five seconds to lean the draw your way — towards you or away from you, your choice. |
| 🔥 | **Chicken** | Circles light up one at a time. Let go in time and you are out safe. The window starts at 100 ms and eases until somebody can beat it. Last one still holding loses. |

<div align="center">
<img src="docs/screenshots/finger-picker.png" width="185" alt="Finger Picker">
<img src="docs/screenshots/wheel-setup.png" width="185" alt="Wheel editor">
<img src="docs/screenshots/uppercut.png" width="185" alt="Uppercut">
<img src="docs/screenshots/chicken.png" width="185" alt="Chicken">
</div>

---

## Install on iPhone or iPad

**Requirements:** iOS 17.0 or newer. Works on iPhone and iPad.

Caster is not on the App Store, so there are two ways in. Sideloading is easier;
building from source is free forever and needs a Mac.

### Option A — Sideload the IPA (no Mac needed)

The build published here is **unsigned**, which means you re-sign it with your own
free Apple ID at install time. [AltStore](https://altstore.io) and
[SideStore](https://sidestore.io) both do this for you.

1. Install **AltStore** (needs a PC or Mac running AltServer once) or **SideStore**
   (on-device, no computer after setup). Follow their own setup guide first —
   it is the fiddly part, and it is a one-time thing.
2. On your iPhone, open [**Releases**](https://github.com/Djxsper/caster/releases)
   and download `Caster-<version>.ipa`.
3. Open the downloaded file and **Share → AltStore** (or SideStore). It will ask
   for your Apple ID so it can sign the app with it.
4. Wait for it to finish, then launch Caster from your home screen.

> **Free Apple ID limits.** Apple gives free accounts a 7-day signing certificate
> and a cap of three sideloaded apps. After a week the app stops opening until you
> refresh it — AltStore/SideStore can do that automatically over Wi-Fi. A paid
> Apple Developer account ($99/yr) stretches this to a year.

### Option B — Build it yourself (free, needs a Mac)

1. Install **Xcode 16 or newer** from the Mac App Store. (The project uses
   file-system synchronised groups, which older Xcode cannot open.)
2. Clone and open it:
   ```sh
   git clone https://github.com/Djxsper/caster.git
   cd caster
   open Caster.xcodeproj
   ```
3. Plug in your iPhone and pick it as the run destination.
4. Select the **Caster** target → **Signing & Capabilities** → tick *Automatically
   manage signing* and choose your own Apple ID team. Change the bundle identifier
   from `com.johndoe.Caster` to something of your own, e.g. `com.yourname.Caster`.
5. Press **Run**. On first launch the iPhone will refuse to open it until you
   trust the certificate: **Settings → General → VPN & Device Management → your
   Apple ID → Trust**.

---

## Android

**There is no Android version, and this repository cannot produce one.**

Caster is a native Apple app: the interface is SwiftUI, the multi-touch layer is
UIKit, the buzzes are Core Haptics and the tones are AVFoundation. None of those
frameworks exist on Android, and there is no compatibility layer or converter that
changes that. Anyone offering you a `.apk` of this app made it themselves, and it
is not from here.

Roughly a tenth of the code — the game rules in [`Caster/Domain`](Caster/Domain),
which is deliberately free of any UI framework — would port. Everything else
would be a rewrite in Kotlin with Jetpack Compose, or in something cross-platform
like Flutter. That is a real project, not a build setting. Contributions welcome
if anybody fancies it.

---

## Building and hacking on it

No package manager, no dependencies, no generated files — clone and open.

```
Caster/
├── App/            Entry point, shared services, CI deep-link support
├── Domain/         Game modes, players, saved wheels, the weighted draw
├── Interface/
│   ├── Audio/      Cue tones, synthesised at launch (no audio assets ship)
│   ├── Components/ Buttons, finger rings, the shared touch surface
│   ├── Haptics/    Core Haptics with a UIKit fallback
│   ├── Themes/     Light and dark palettes
│   └── Views/      Launch, setup and one screen per game
└── UIKitBridge/    True multi-touch, and the slot tracking built on it
```

A few things worth knowing before you change something:

- **`TouchArena` is where the multi-touch lives.** SwiftUI's own gestures report
  one finger, which is useless here, so touches come through a `UIView`. Each
  finger is mapped to a *slot* — either sequentially (Finger Picker frees a colour
  the moment a finger lifts) or *stickily* (the reaction games let a finger lift
  and land again without stealing a neighbour's seat).
- **Reaction times come from `UITouch.timestamp`,** never from a `Date()` read
  inside a callback. Chicken's window opens at 100 ms; measuring that in a
  callback would be measuring the main thread, not the player.
- **Draws are decided before the animation.** Pinwheel picks its winner uniformly
  and *then* aims the spin at it — letting friction decide would quietly bias the
  result toward wherever the wheel was resting. Tap Frenzy does the same behind
  its spotlight sweep.
- **Tapping never buys certainty.** In Tap Frenzy everybody keeps a floor in the
  draw whatever they do, so the gap between the hardest tapper and the laziest is
  capped at five to one, in either direction.

### CI

Every push builds on a macOS runner, boots a simulator, screenshots every screen
and packages an unsigned IPA — see
[`.github/workflows/ios-simulator.yml`](.github/workflows/ios-simulator.yml).
Artifacts land on the run page.

Pushing a `v*` tag additionally publishes a GitHub Release with the IPA attached
([`release.yml`](.github/workflows/release.yml)):

```sh
git tag v1.0.0
git push origin v1.0.0
```

Note that CI can only prove the app *compiles* and that each screen renders. The
simulator has no real multi-touch, so the finger tracking, reaction timing and
audio are only ever verified on a device.

---

## License

[MIT](LICENSE) — do what you like with it, just keep the copyright notice.
