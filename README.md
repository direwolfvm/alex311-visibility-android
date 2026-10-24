# Alex311 Reborn for Android

Native Kotlin / Jetpack Compose port of the sibling [iOS app](../alex311-visibilit-ios). An unofficial companion to Alexandria, Virginia's Alex311 service, using the existing backend at https://alex311-reborn.com. Links on the former `alex311visibility.me` host remain supported.

## Run

Open this directory in Android Studio, let Gradle sync, choose the `app` configuration and an Android device or emulator, then Run.

- Android 8.0+ (API 26); compile/target API 36.
- Java **21** for Gradle. `gradle/gradle-daemon-jvm.properties` pins the daemon to 21, including when Studio bundles a newer Java version. Install a JDK 21 if Gradle cannot find one; Studio's Gradle JDK download picker also works.
- Android SDK Platform 36 and Build Tools 35.0.0. Studio or Gradle installs these when SDK licenses are accepted.
- Gradle wrapper is included; no global Gradle install is needed.
- No Firebase JSON or map API key is required for email sign-in or public browsing.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug
# With an emulator/device connected:
./gradlew connectedDebugAndroidTest
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. CI runs unit tests, debug/release lint and builds, API 26/36 emulator tests, CodeQL, secret scanning, workflow lint, and dependency audits. See [CI and protection](docs/CI.md).

## Ported features

| Area | Android behavior |
| --- | --- |
| Near Me | Permission-aware device location, explicit Old Town fallback, 150/400/800 m radius, 30/90/365 day windows, open-only filter, nearest/newest ordering, map/list, pagination, report-here shortcut |
| Report | Cached schema-checked registry, type search, address suggestions and map pin, reverse lookup, nearby duplicate suggestions, conditional City questions, hard stops/advisories, local draft recovery, up to three photos, server validation/precheck, contact details, explicit send confirmation, queueing and resumable filing status |
| My Issues | Sent/followed cases, unseen status changes, City refresh, category/address/area watches, new-match feed, mark seen |
| Explore | City map, searchable/paginated list, status/category/date/area filters, map colors by status/type/age, charts and aggregate metrics |
| Request detail | Photos with pinch zoom, City response/closure, timeline, related cases, sharing, follow/unfollow, private notes/public scores, watch shortcuts, report-similar shortcut |
| Account | Email-link/code sign-in, optional Google sign-in, display name, email digest, sign out/all devices, delete account with confirmation, tour, debug server selection |
| Android integration | Keystore-encrypted session/contact/draft storage, system Photo Picker, EXIF orientation/scaling/metadata removal, deep-link routing, local status notifications through WorkManager |

The report pipeline preserves the shared backend contract: `/validate` → `/precheck` → raw JPEG uploads to the prepared attempt → `/queue` → `/status/{id}`. The attempt ID is persisted before queueing; relaunching polls that attempt instead of silently filing another request. Contact details are persisted only when "Remember on this phone" is selected. Photos remain in private app storage until sending and are deleted when filing succeeds or the draft is cleared.

Unit tests replay all **180 validation fixtures across 30 real services** copied from iOS and check payload parity, geometry, date/time zones, routing, safe-link unwrapping, decoding, cookies, errors, registry ETags, and raw photo uploads. Instrumentation tests exercise the Android report workflow against a local MockWebServer, including blocked reports, stale validations, photo upload ordering, attempt recovery, photo processing, and Keystore storage. They do not submit real City requests.

## Android-specific setup and remaining release work

See [Android setup and parity notes](docs/ANDROID_SETUP.md) for Google OAuth, verified links, push-notification differences, and the remaining device/account acceptance checks.

The production server remains unchanged. Public browsing can be used immediately. The web application's public Google OAuth client ID is configured; Android signing-certificate registration and website App Link association still need completion. The authenticated production flow needs an actual user sign-in for final acceptance; automated filing tests use a local server. Apple sign-in is not ported to a native Android provider button. Email and Google are the Android sign-in methods.

Maps use a native tile view with visible-tile fetching, bounded concurrent requests, an identifiable User-Agent, HTTP disk caching, attribution, pan/pinch/zoom, and clustered markers. OpenStreetMap's public tile service is suitable for development under its [tile usage policy](https://operations.osmfoundation.org/policies/tiles/); assess a production tile service before scaling distribution. There is no offline map download feature.

## Contributing and license

Community contributions are welcome under the [MIT license](LICENSE). Start with [CONTRIBUTING.md](CONTRIBUTING.md), use a pull request, and follow [SECURITY.md](SECURITY.md) for private vulnerability reports. Main is protected by required tests/security checks and maintainer review; releases remain separate maintainer actions.
