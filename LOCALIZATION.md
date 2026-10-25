# Localization

RightPress 0.6.0-beta.1 provides **30 languages / 31 locales**. Chinese has separate Simplified and Traditional resources. English is the fallback. These are initial beta translations; native-speaker review has not been completed.

## Language selection

Open **文/A** in the app header and choose a language shown in its native name. **System default** follows Android’s language preferences. Android’s per-app language settings and the in-app picker update the same preference, which Android stores locally.

Language changes affect display text. They do not change saved key combinations, action identifiers, launch targets, consent or mouse/keyboard settings.

## Coverage

The selection considers major mobile and internet markets across Asia, Europe and the Americas. It is a curated coverage choice, not a strict ranking of app users or measured RightPress usage. The references consulted on 2026-10-25 were [DataReportal’s Digital 2026 overview](https://datareportal.com/reports/digital-2026-global-overview-report) for internet/mobile adoption context and [W3Techs website content languages](https://w3techs.com/technologies/overview/content_language) for web-language context. W3Techs counts website content languages, not app users.

Resource folders below are relative to `app/src/main/res/`; each contains `strings.xml`.

| Language | Locale tag | Resource folder |
| --- | --- | --- |
| English | `en` | `values` |
| Korean | `ko` | `values-ko` |
| Chinese, Simplified / Traditional | `zh-Hans` / `zh-Hant` | `values-b+zh+Hans` / `values-b+zh+Hant` |
| Hindi | `hi` | `values-hi` |
| Spanish | `es` | `values-es` |
| Arabic | `ar` | `values-ar` |
| Portuguese | `pt` | `values-pt` |
| Indonesian | `id` | `values-in` |
| French | `fr` | `values-fr` |
| Japanese | `ja` | `values-ja` |
| Russian | `ru` | `values-ru` |
| Bengali | `bn` | `values-bn` |
| German | `de` | `values-de` |
| Vietnamese | `vi` | `values-vi` |
| Turkish | `tr` | `values-tr` |
| Urdu | `ur` | `values-ur` |
| Italian | `it` | `values-it` |
| Thai | `th` | `values-th` |
| Persian | `fa` | `values-fa` |
| Polish | `pl` | `values-pl` |
| Filipino | `fil` | `values-fil` |
| Malay | `ms` | `values-ms` |
| Ukrainian | `uk` | `values-uk` |
| Dutch | `nl` | `values-nl` |
| Romanian | `ro` | `values-ro` |
| Czech | `cs` | `values-cs` |
| Swedish | `sv` | `values-sv` |
| Tamil | `ta` | `values-ta` |
| Telugu | `te` | `values-te` |
| Marathi | `mr` | `values-mr` |

Portuguese uses terminology familiar in Brazil. Region-specific variants can be added as separate locales when needed.

## Structure

- **Source text:** `app/src/main/res/values/strings.xml`. All user-facing labels, errors, accessibility descriptions, consent and help text use Android string resources.
- **Translations:** the corresponding `values-*/strings.xml` files. Resource names are stable keys; displayed wording can change independently of input behavior.
- **Default locale:** `app/src/main/res/resources.properties` declares `unqualifiedResLocale=en`.
- **Language catalog:** `androidResources.generateLocaleConfig` in `app/build.gradle` discovers resource locales. `AppLanguage.java` reads that generated catalog and uses Android `LocaleManager`; there is no second hard-coded language list to edit.
- **Checks and scaffolding:** `tools/localization.py`, using only the Python standard library. GitHub Actions runs its coverage/format check before building.

The UI uses Android’s system font families and system glyph fallback, including CJK and Indic scripts. It does not bundle or download fonts or add a localization SDK. Language splits are disabled for app bundles so every listed language remains available offline. Font appearance and glyph availability can vary by device. Translation text increases resource size; compare the signed APK sizes in release notes rather than assuming no size change.

## Add a language

From the repository root, scaffold a BCP-47 locale, for example Finnish:

```sh
python3 tools/localization.py add fi
```

This creates `app/src/main/res/values-fi/strings.xml`. For script or region variants, use a tag such as `zh-Hant` or `pt-BR`; the tool chooses the matching Android folder name. Existing locales are not overwritten.

Translate every `TODO:` entry, including consent, privacy-related explanations, error messages and accessibility labels. The template deliberately fails validation until all entries are translated. Keep the source meaning; do not remove limitations or privacy disclosures to make a label shorter.

- Preserve resource names and positional arguments such as `%1$s`, `%2$s` and `%1$d`. Their order may change in the sentence; their identities and types must not.
- Preserve `\n` paragraph and list structure where needed. Escape XML characters such as `&amp;` and Android straight apostrophes as `\'`.
- Leave the brand, literal key combinations such as `Ctrl+Alt+R`, package names and internal action identifiers unchanged. Translate only display text. `translatable="false"` resources stay in the default file.
- Use concise labels and natural local terminology. Do not copy English across entries to satisfy coverage checks. Shared terms such as `OK` and units may legitimately match.
- Add no font files or font download resources. Missing device glyphs need a compatibility report, not an unreviewed bundled font.

Run the checks and build:

```sh
python3 tools/localization.py list
python3 tools/localization.py check
./gradlew :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest
```

The checker rejects missing or unknown keys, duplicate keys, empty or `TODO` values, mismatched format arguments, substantially copied English templates and font resources. Android’s build checks resource syntax. Neither check establishes translation quality.

Open the app in the new language and inspect the picker, both tabs, shortcut editor, app chooser, consent/help dialogs and overlay stop control. Check portrait, landscape, narrow screens and larger text. For RTL languages, check reading direction while keeping key combinations readable. Confirm language switching preserves existing shortcuts and settings. Add the locale to the coverage table and describe the review performed in your pull request. The app and Android language menus discover the resource automatically after rebuilding; no Java edit is needed.

When adding a new source string, update every supported locale in the same change. For wording corrections, include the locale, resource key, context and suggested text in an issue or pull request. Do not attach private input history or sensitive screenshots.
