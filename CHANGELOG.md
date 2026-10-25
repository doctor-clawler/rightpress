# Changelog

## 0.6.0-beta.1

- Add 30 languages / 31 locales, including Simplified and Traditional Chinese.
- Follow the system language by default, with an in-app native-name picker and Android per-app language settings.
- Extract interface text, accessibility descriptions, validation messages and consent/help text into native Android string resources.
- Support RTL layouts and use system fonts without bundled font files or a localization SDK.
- Generate the language catalog from resource folders; add a standard-library Python tool to scaffold translations and check completeness and format arguments in CI.
- Keep the selected language after accessibility service reconnection and size the overlay stop control for translated text and screen readers.
- Keep the public package, release signing key, saved shortcuts and action identifiers unchanged. The signed APK updates the official 0.5.0 beta in place.

Translations are an initial beta and have not received native-speaker review. The market-based language selection is not a measured user ranking. See [LOCALIZATION.md](LOCALIZATION.md) for coverage and contribution steps; the [release page](https://github.com/doctor-clawler/rightpress/releases/tag/v0.6.0-beta.1) records build-specific verification and APK size when published.

## 0.5.0-beta.1

First public GitHub beta. Android 14 or later. Not a Google Play release.

- Publish the original RightPress source under MIT, with Gradle third-party notices.
- Use package `io.github.doctor_clawler.rightpress` and a dedicated signing key for public beta APKs.
- Add accessibility-use disclosure and explicit consent before activating input handling.
- Add a local-data privacy notice and public reporting instructions.
- Retain the Console interface, five mouse actions and seven keyboard actions.
- Add pinned GitHub Actions builds for debug APKs, lint and instrumentation-test compilation.

The beta installs separately from the former `com.example.rightpress` debug app. Disable its accessibility service and record settings before switching; there is no automatic settings migration. Release notes describe build-specific verification.

## 0.4.0-test

Private prototype.

- Apply the Console design: graphite background, blue accents, native mouse illustration and thin dividers.
- Adapt navigation to tablet side rail and narrow-screen bottom tabs.
- Preserve mouse/keyboard actions and stored settings.
- Check tablet portrait/landscape, narrow screens and larger text on an Android 15 emulator.

## 0.3.x-test

Private prototypes.

- Add configurable mouse actions and independent external-keyboard shortcuts.
- Add shortcut recording, app-launch targets and individual rule switches.
- Improve portrait layout and shorten Korean labels.

## 0.2.x-test

Private prototypes.

- Persist mouse-conversion state across service reconnection.
- Request accessibility long-click on right-button down, with a release-based touch fallback.
