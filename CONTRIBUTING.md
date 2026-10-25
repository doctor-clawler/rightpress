# Contributing

Use Issues for reproducible bugs and focused feature proposals. For code changes, keep pull requests small, describe the resulting behavior and state what you tested. Contributions to original RightPress code are under the repository's [MIT license](LICENSE); preserve third-party notices.

## Local build

Use JDK 17, Android SDK Platform 35 and Build Tools 35.0.0. Set `ANDROID_HOME`, or create an untracked `local.properties` with `sdk.dir`.

```sh
./gradlew :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest
```

The wrapper uses Gradle 8.10.2 with a pinned distribution checksum. Do not commit SDK paths, signing keys, passwords, generated APKs or device logs. Release signing is managed outside this repository. CI uses no release-signing secrets.

## Device verification

Tests are under `app/src/androidTest`. Some keyboard tests use an emulator-specific `evdev` helper, gated to the expected AOSP emulator input device, to exercise the accessibility key filter. They need a disposable root-capable AOSP emulator. They are not intended for a user's physical tablet. Ordinary Android key injection does not exercise the same path.

Run device tests only after confirming the selected emulator and isolating it from physical devices. Preserve unrelated apps, data, accessibility services and power settings. Do not weaken an input assertion to make the UI redesign pass. Test helpers must remain outside the installable app APK.

For manual reports, include:

- App and Android versions; tablet/phone and keyboard/mouse models.
- Bluetooth/USB connection, screen orientation and any display/desktop mode.
- Feature settings, reproduction steps, expected and actual behavior.
- Whether disabling mouse conversion changes the result.

Remove private content from screenshots and logs. Use the [security reporting process](SECURITY.md) for vulnerabilities.

## UI and input behavior

Keep labels concise. Check portrait, landscape, a narrow 360 dp screen, scrolling and enlarged text. Mouse and keyboard switches must remain independent. Preserve the fixed `Ctrl+Alt+R` escape/toggle and explain accessibility access before it is enabled.

Do not add network access, analytics or new data collection without an explicit design discussion and a matching privacy/disclosure update. Add no runtime permission solely to make tests easier.
