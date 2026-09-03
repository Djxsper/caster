# Shipping Caster to the App Store

Caster is currently sideloaded, and a sideloaded app **cannot complete a
StoreKit purchase** — Apple only serves in-app purchases to apps it distributed
itself. So none of the code in `Caster/App/Store/` or `Caster/Interface/Ads/`
earns anything until the app is on the store. That is step zero, not a polish
item.

The monetization contract both platforms read is
[`shared/monetization/offering.json`](../shared/monetization/offering.json).

---

## Phase 0 — accounts and paperwork (no code, do this first)

Nothing else unblocks revenue, and the banking review takes days.

- [ ] Enrol in the **Apple Developer Program** (~€99/yr incl. VAT).
- [ ] Sign the **Paid Applications Agreement** in App Store Connect and complete
      the Dutch banking and tax forms. **Until this is active, purchases fail.**
- [ ] Create the App Store Connect record for `com.jesperhaafkes.Caster`.
- [ ] Create the in-app purchase: **non-consumable**, product ID
      `com.jesperhaafkes.Caster.plus`, price tier ≈ €3.99. The ID must match
      `StoreProduct.plus`; `OfferingParityTests` checks it against the contract.
- [ ] Publish a **privacy policy** and link it. `SettingsView` currently points
      at `https://djxsper.github.io/caster/privacy` — create that page or change
      the constant.
- [ ] Age rating: the app references buying rounds. Answer the alcohol question
      honestly and **stay out of the Kids category** — it forbids the ad setup
      below and adds review burden.
- [ ] Fill in App Privacy ("Data Not Collected" is accurate until ads ship).

---

## Phase 1 — ship it free

Submit the app as it stands, once CI is green and you have played a test IPA on
your own phone. Review is easiest against the smallest surface, and this starts
ratings and ASO while Plus is finished. The code already builds with no ads and
no SDK linked.

---

## Testing it before you pay Apple

You do not need a Mac and you do not need the Developer Program to test almost
all of this. The macOS runners in GitHub Actions are the Mac.

### What runs automatically on every push

`.github/workflows/ios-simulator.yml` builds the app, runs the `CasterTests`
suite on a booted simulator, then screenshots every screen. That covers:

- the ad pacing rules, every clause, proved directly (`AdPacingTests`)
- the caps, grandfathering and revocation (`EntitlementTests`)
- the Swift constants against `offering.json` (`OfferingParityTests`)
- **buying, losing and restoring Plus** against a simulated storefront
  (`PurchaseFlowTests`, via `SKTestSession` and `Caster.storekit`) — no Apple
  account, no sandbox tester, no money

### Putting a build on your own phone

Run **Actions → Build a test IPA → Run workflow**
(`.github/workflows/test-ipa.yml`). It produces an unsigned *Debug* device IPA as
a run artifact — not a GitHub Release — which you install through AltStore the
usual way.

A Debug build is what you want here, because these exist only in Debug and are
compiled out of the App Store binary:

| | |
|---|---|
| **TEST BUILD badge** | on the launch screen, so you never confuse builds |
| **Fake interstitial** | a stand-in ad obeying the identical `AdPacing` rules |
| **Pretend Plus is bought** | a switch in Settings → Test build |
| **Ad status readout** | why an ad is or is not coming, and when the next is due |
| **Reset ad counters** | so the "quiet first two sessions" rule can be felt twice |

The workflow greps the built binary for those strings and fails if they are
missing, so a configuration mistake cannot hand you an artifact that looks right
and tests nothing.

> The test IPA shares the bundle id, so it **replaces** an installed Caster and
> inherits its saved wheels and groups.

---

## The one thing that still needs setting up: real ads

Everything is behind a seam; nothing changes until both conditions in
`AdPresenterFactory` are true.

1. Add the package:
   `https://github.com/googleads/swift-package-manager-google-mobile-ads`
2. Build Settings → **Active Compilation Conditions** → add `ADS_ENABLED` to the
   *Release* configuration only. Never to Debug — Debug is where the stand-in
   lives.
3. Put the real `GADApplicationIdentifier` in the generated Info.plist settings
   and the real unit IDs in `AdMobPresenter.Unit`. They currently point at
   Google's public test units on purpose: running live unit IDs against
   development traffic is an AdMob policy violation.
4. Add `NSUserTrackingUsageDescription` and present the ATT prompt.
5. Integrate the **Google UMP** consent SDK and call
   `AdMobPresenter.grantConsent(_:)` with the result. Mandatory in the EU.
6. Update [`PrivacyInfo.xcprivacy`](../Caster/Resources/PrivacyInfo.xcprivacy) —
   the file lists exactly which three things change.

Steps 1 and 2 edit `project.pbxproj` and can be done without a Mac; CI verifies
the package resolves. **Ship the app free first** — none of this is needed for a
first submission.

---

## Uploading from Windows

Submitting a build needs macOS tooling, so the upload runs in CI too. This is the
real cost of not having a Mac, and it is a proper piece of work rather than a
step:

- A workflow that archives, signs and uploads to App Store Connect.
- Signing via an **App Store Connect API key** with `-allowProvisioningUpdates`,
  so CI creates and fetches the certificate and profile itself. No manual
  certificate wrangling, no Keychain juggling.
- The `.p8` key file goes in GitHub secrets; the Issuer ID and Key ID go with it.

Generate the key under **Users and Access → Integrations** in App Store Connect.

---

## Verifying the money code

**Purchases, without App Store Connect.** Select `Caster.storekit` as the
scheme's StoreKit configuration, then in the simulator:

- Buy Plus → the caps lift, the palettes unlock, `SettingsView` shows Unlocked.
- Kill and relaunch → still Plus (the entitlement is cached on disk).
- Debug → StoreKit → Manage Transactions → **refund** → Plus revokes, and a Plus
  palette falls back to System *without* forgetting which was chosen.
- Delete the app, reinstall, **Restore purchase** → Plus returns.

**The cap.** Fresh install → make wheels until the fourth is refused → the Plus
sheet names the reason rather than showing a wall → buy → the fourth is created.

**Grandfathering.** Seed `UserDefaults` with five wheels *before* first launch of
this build; the legacy flag should grant unlimited saves and *not* remove ads.

**Offline.** Airplane mode as a Plus user: palettes and unlimited saves still
work, no spinner, no error. This is the whole reason the entitlement is cached.

**Ad pacing.** Do not try to observe this by playing — `AdPacing` is a pure
function and `AdPacingTests` proves every clause directly. What is worth checking
by hand is the placement: play each of the six games start to finish and confirm
nothing appears mid-round, on a result, or on the way *into* the app. The only
legal moment is arriving back at the mode list from a game.

**Screenshots.** CI shoots every screen including Settings
(`-screen settings`); the workflows already have it.
