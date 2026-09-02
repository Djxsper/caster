# Getting Caster onto Google Play

Everything in the repository is ready for this. What is left is the parts that
involve secrets and a Play Console account, which cannot live in a git history.

## 1. Before anything else: play it on a phone

CI proves the app compiles, passes its unit tests and lints clean. It cannot
prove the app is any good, because the thing this app does — several fingers on
the glass at once, judged to the millisecond — is exactly what an emulator
cannot produce. Synthetic events give you one finger.

Install a debug build on a real device and play all six games with at least two
people before you ship anything:

```sh
cd android
./gradlew installDebug
```

Or take the `caster-debug-apk` artifact from any Android CI run.

## 2. Create the upload key

Once, and never commit it:

```sh
keytool -genkey -v -keystore caster-upload.jks \
  -keyalg RSA -keysize 2048 -validity 10000 -alias caster
```

Keep the file and its passwords somewhere you will still have them in five
years. Losing the upload key is recoverable through Play support; losing it
*before* enrolling in Play App Signing is not.

## 3. Give it to CI

`app/build.gradle.kts` reads the signing config from the environment, so nothing
about the keystore appears in the tree. Set four repository secrets:

| Secret | What it holds |
|---|---|
| `CASTER_KEYSTORE_BASE64` | `base64 -w0 caster-upload.jks` |
| `CASTER_KEYSTORE_PASSWORD` | store password |
| `CASTER_KEY_ALIAS` | `caster` |
| `CASTER_KEY_PASSWORD` | key password |

A release job decodes the first to a file and exports `CASTER_KEYSTORE` as its
path, along with the other three. With those unset — every local build, and the
current CI — `assembleRelease` still produces an unsigned APK, exactly as it does
today.

`versionCode` and `versionName` come from `CASTER_VERSION_CODE` and
`CASTER_VERSION_NAME`. Play rejects a duplicate `versionCode`, so derive it from
the tag or the run number rather than editing a file each time.

## 4. Build the bundle

Play wants an AAB, not an APK:

```sh
./gradlew bundleRelease
```

R8 and resource shrinking are already on, which is why the release build is about
1.3 MB against 12 MB for debug. Test the *minified* build on a device before
uploading: R8 rewrites reflection sites, and anything it strips wrongly fails at
runtime in release only. If something breaks, the keep rule goes in
`app/proguard-rules.pro` and the mapping file to decode the stack trace is at
`app/build/outputs/mapping/release/mapping.txt`.

Upload that mapping file to Play alongside the bundle, or every crash report you
get back will be unreadable.

## 5. The console paperwork

- **Target API.** `targetSdk` is 37, comfortably past the API 36 floor Play
  applied on 31 August 2026.
- **Store listing.** Needs a 512×512 icon, a 1024×500 feature graphic, and phone
  screenshots. The thirteen PNGs in `android/screenshots/` are raw device
  captures at 1080×2400 — usable as a starting point, but they are not sized or
  framed for the listing.
- **Privacy policy.** Required, and it is a short one: the app has no `INTERNET`
  permission, no analytics and no network code of any kind. Names and wheel
  entries are written to `SharedPreferences` on the device and go nowhere else.
- **Data safety form.** Answer "no data collected". The one thing to declare
  deliberately is `android:allowBackup="true"` in the manifest, which lets
  Android's own backup carry those `SharedPreferences` to the user's cloud
  backup. Either say so on the form, or set it to `false`.
- **Content rating.** A party game with no chat, no user-generated content
  visible to anyone else, and no ads.

## Known limitations to declare or fix first

- **Orientation.** The app asks for portrait, matching iOS. Android 16 ignores
  fixed orientation on large screens, so on a tablet or an unfolded foldable it
  will rotate into a landscape layout that nobody has designed. Either support
  landscape or expect it to look wrong there.
- **Haptics.** Most Android actuators are weaker and slower than an iPhone's
  Taptic Engine. `HapticEngine` already probes for composition primitives and
  falls back sensibly, but the cues will not feel identical, and no amount of
  code closes that gap.
