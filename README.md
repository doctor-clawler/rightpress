# RightPress

Mouse button actions and external-keyboard shortcuts for **Android 14+**.

**Latest downloadable beta: 0.6.0-beta.1.** [Release page](https://github.com/doctor-clawler/rightpress/releases/tag/v0.6.0-beta.1). Official beta APKs are attached to releases marked **Pre-release**. Source version 0.6.1-beta.1 prepares Android 16 targeting and a signed app bundle for Google Play internal testing. RightPress is not yet available on Google Play; there is no Play test link yet.

[한국어](README.ko.md) · [Privacy](PRIVACY.md) · [Changelog](CHANGELOG.md) · [Languages](LOCALIZATION.md) · [Contributing](CONTRIBUTING.md)

<p>
  <img src="docs/images/mouse.png" width="320" alt="RightPress mouse action settings on an Android tablet">
  <img src="docs/images/keyboard.png" width="320" alt="RightPress external-keyboard shortcut list on an Android tablet">
</p>

Screenshots: 0.6.0-beta.1 running on the Android emulator in portrait orientation. Accessibility use requires the in-app disclosure and consent step.

## Features

- **Right-click actions:** long-press, Back, Home, Recents or Notifications.
- **Keyboard actions:** Back, Home, Recents, Notifications, Quick Settings, launch an app or toggle mouse conversion.
- Independent mouse and keyboard switches; individual shortcut switches.
- Local settings, no accounts, advertisements, analytics or network permission.
- Native Android interface that adapts to portrait, landscape and narrow screens.
- 30 languages / 31 locales, including Simplified and Traditional Chinese; system fonts only.
- No root, ADB or Shizuku required to use the app.

For long-press, RightPress first requests the target element's accessibility long-click action **when the mouse button is pressed**. If unsupported or rejected, it sends a touch long-press after release. Turn off `Instant action` to always use that fallback. Its duration is 400–1,200 ms, default 650 ms. Other right-click actions run on button down.

## Install the beta

1. Download the APK from [Releases](https://github.com/doctor-clawler/rightpress/releases/tag/v0.6.0-beta.1). Actions artifacts are development builds, not the signed beta.
2. Install it on Android 14 or later. Allow installation from your download app if Android asks.
3. Open RightPress → `Enable accessibility service`, read the accessibility disclosure and agree if you want to use the service.
4. Open Android accessibility settings from the app and enable **RightPress input helper**.
5. Return to RightPress and configure the `Mouse` and `Keyboard` tabs.

Android can restrict accessibility for sideloaded apps. If necessary, open Android **App info → More → Allow restricted settings**, but only for an APK whose source you trust. Menu names vary by device.

**Updating from the official 0.5.0 beta:** install the new signed APK over the existing app. The package and release signing key are unchanged, so settings and shortcuts are retained.

**Earlier private test builds:** the beta uses package `io.github.doctor_clawler.rightpress` and a dedicated release signing key. The former `com.example.rightpress` debug app (0.4.0 or earlier) is a separate app, so this is a **new installation**, not an in-place update. Record your old settings and disable its accessibility service first. Both apps can remain installed, but enable only one RightPress service at a time. Settings do not migrate automatically; recreate them in the beta. Locally built debug APKs also cannot update official beta APKs because their signatures differ.

## Language

[Language picker screenshot](docs/images/languages.png)

Tap **文/A** in the header and select a language by its native name, or choose **System default**. Android’s per-app language settings control the same preference. English is the fallback when no supported language matches. Changing the language preserves shortcut bindings and input settings.

The 30-language selection covers major mobile and internet markets; it is not a measured ranking of RightPress users. These are initial beta translations without native-speaker review. Report wording issues with the language, screen and suggested correction.

Text uses Android’s system fonts and fallback glyphs. No font files, downloads or localization SDK are added. Translations add text resources, so APK size can increase. See [language coverage and contribution steps](LOCALIZATION.md).

## Shortcuts and stopping

| Default shortcut | Action |
| --- | --- |
| `Ctrl+Alt+B` | Back |
| `Ctrl+Alt+H` | Home |
| `Ctrl+Alt+O` | Recents |
| `Ctrl+Alt+N` | Notifications |
| `Ctrl+Alt+R` | Toggle mouse conversion; fixed, cannot be reassigned |

Add a rule with `Keyboard → ＋ Add shortcut → Record key combination`, press Ctrl or Alt with another key, choose an action and save. Shift is optional. Esc cancels recording. Duplicate combinations, including disabled rules, are rejected. Meta/Windows, Sym and Fn combinations are not supported.

- Stop mouse conversion: `Ctrl+Alt+R`, the top-right `Stop mouse` button or the mouse switch.
- Stop configurable shortcuts: turn off `Keyboard shortcuts`. The fixed `Ctrl+Alt+R` toggle remains available.
- Stop all input handling and withdraw access: disable the service in Android accessibility settings.

Closing the settings screen does not stop the service. On/off choices are saved. Android controls service startup and reconnection, so uninterrupted background operation is not guaranteed.

## Compatibility and beta scope

Mouse conversion uses a full-screen transparent accessibility overlay. Left clicks and drags are relayed as touch taps and swipes; the wheel becomes touch scrolling. Hover, precise dragging, text selection and multitouch can differ from normal mouse behavior. Turn off mouse conversion when those differences are disruptive; keyboard shortcuts can stay enabled.

- Immediate long-press depends on the target app. If it reports success without a visible result, turn off `Instant action` and try the release-based fallback.
- System/security dialogs, games, device policies and reserved key combinations can restrict actions.
- App-launch shortcuts support launchable apps visible to RightPress.
- No key-to-key remapping, text expansion, macros or per-app profiles.
- Physical tablets, Bluetooth/USB input devices, Samsung DeX, split screen, external displays and battery impact still need testing. Individual third-party apps are not guaranteed compatible.
- Emulator checks and a GitHub beta release do not establish Google Play approval.

Report reproducible problems through [GitHub Issues](https://github.com/doctor-clawler/rightpress/issues). Include app/Android version, device and input-device models, connection type, screen orientation and steps. Issues are public: remove personal content, credentials and private screenshots. For vulnerabilities, see [SECURITY.md](SECURITY.md).

## Privacy and accessibility

The service receives input events to match shortcuts and inspects the screen's accessibility hierarchy to locate elements and invoke actions. It does not read element text or save typing history, screen content or screenshots. Saved rules, selected app identifiers, settings and consent state remain on the device. Android stores the app-language preference locally. There is no `INTERNET` permission or analytics SDK. See the full [privacy notice](PRIVACY.md).

## Build and test

Requirements: JDK 17, Android SDK Platform 36, Build Tools 35.0.0 and Python 3 for translation checks. Configure `ANDROID_HOME`, or set `sdk.dir` in an untracked `local.properties` file. The app still runs on Android 14 and later (minSdk 34).

```sh
python3 tools/localization.py check
./gradlew :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest
```

The included Gradle 8.11.1 wrapper verifies its distribution SHA-256; Android Gradle Plugin is 8.10.1. Output: `app/build/outputs/apk/debug/app-debug.apk`. Debug builds use a local debug key and do not update the signed public beta. Release signing keys are not in this repository or GitHub Actions.

To build a release APK with **your own** signing key, provide these environment variables through your local secret-management setup, then run `./gradlew :app:assembleRelease`:

- `RIGHTPRESS_SIGNING_STORE_FILE`
- `RIGHTPRESS_SIGNING_STORE_PASSWORD`
- `RIGHTPRESS_SIGNING_KEY_ALIAS`
- `RIGHTPRESS_SIGNING_KEY_PASSWORD`

The `assembleRelease` command fails if signing credentials are missing. Output: `app/build/outputs/apk/release/app-release.apk`. Your signature does not match the official beta signature; keep your key private and retain it for your own future updates.

With the same signing environment, `./gradlew :app:bundleRelease` creates `app/build/outputs/bundle/release/app-release.aab` for a store upload. Language splitting is disabled so all supported languages remain available offline. An AAB build alone does not publish an app or create a tester link. See [Google Play test delivery](docs/google-play-testing.md) for release and signing checks.

**GitHub Actions:** pushes, pull requests and manual runs check translation coverage and format arguments, build the debug app, run lint and compile instrumentation tests. Download the `RightPress-debug-build` artifact. CI does not execute the device tests.

Device tests are in `app/src/androidTest`. Some inject physical key events through an emulator-only `evdev` helper and require a disposable AOSP emulator with root-capable test access; they are not safe as a generic physical-device test command. The helper is excluded from the app APK, and the app itself does not require root. See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

Original RightPress code and artwork: [MIT](LICENSE). Gradle wrapper files retain their upstream license; see [third-party notices](THIRD_PARTY_NOTICES.md).

The trigger-to-action setup flow was informed by Key Mapper's public documentation. RightPress has its own implementation and visual assets; no Key Mapper source or artwork is included.
