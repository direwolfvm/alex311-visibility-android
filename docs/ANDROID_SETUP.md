# Android setup and acceptance

## Backend

The canonical production origin is `https://alex311-reborn.com`. The former `https://alex311visibility.me` host still serves API requests directly but redirects website pages to the canonical host. Both hosts are accepted for incoming App Links so older inbox links keep working. This configuration follows the web repository's `docs/android-app.md` answers dated 2026-10-24.

The app uses the iOS app's existing public `/api/*` endpoints and authenticated `/submit/api/*` endpoints. It trades a tenant Firebase ID token for the `alex311_portal` cookie; it does not send that cookie on public reads or follow redirects with it. Sessions are stored with AES-GCM using Android Keystore. Backups are disabled for private app data.

`/submit/api/registry/full` requires sign-in on production. Its schema-1 JSON and ETag are cached per server origin; hidden and data-only answers are excluded from report payloads. The server remains authoritative for validation and anti-abuse decisions.

## Google sign-in

Email links and six-digit codes use the backend's Firebase REST configuration and need no embedded Firebase key. Google sign-in uses Android Credential Manager. The web-side answers identify project `permitting-ai-helper`, its existing tenant `alex311-qfnem`, and this **Web application OAuth client ID**, which is now the build default:

```text
650621702399-f23j3l0mv6uv63b19d1chst41pv4shem.apps.googleusercontent.com
```

Google is already enabled in that tenant. Do not create another tenant. The outstanding provider step is registering an **Android OAuth client** in the same project for package `com.herbertindustries.alex311` and each signing certificate's SHA-1 fingerprint: local debug, upload, and Play app-signing as applicable. Google sign-in will not work for an unregistered package/certificate combination. Obtain SHA-1 and SHA-256 fingerprints with `./gradlew signingReport`; release fingerprints require the actual release/Play certificate.

The default client ID is public configuration. A contributor using a separate backend can override it in their user `~/.gradle/gradle.properties`:

   ```properties
   googleWebClientId=YOUR_WEB_CLIENT_ID.apps.googleusercontent.com
   ```

An empty `googleWebClientId` override hides the Google button. After registering the Android certificate, rebuild and test with a test account. The Google button requests a Google ID token, exchanges it through Firebase with the server-provided `tenantId`, then uses the same backend session endpoint as email. Automated tests do not call Google or require a registered certificate.

Client IDs are public configuration. Do not reuse the iOS OAuth client ID, add private OAuth client secrets, or commit signing keys. No `google-services.json` is needed. Native Apple sign-in is not implemented on Android; it would require a web OAuth return flow and backend/provider configuration.

## HTTPS app links

The manifest handles these HTTPS paths on **both** `alex311-reborn.com` and `alex311visibility.me`: the `/r/` prefix and the exact `/submit`, `/submit/login`, and `/submit/my` paths. Digest confirmations/unsubscribe links, account/admin pages, and `/api` or `/submit/api` paths stay in the browser. Router validation applies before extracting a sign-in code, so an unrelated path or host cannot trigger sign-in through an explicit activity intent. Pasted email links still support mail-provider wrappers with a depth limit.

The website now serves `/.well-known/assetlinks.json` on both hosts when its Cloud Run environment has `ANDROID_PACKAGE=com.herbertindustries.alex311` and `ANDROID_SHA256_CERT_FINGERPRINTS` set to comma-separated SHA-256 certificate fingerprints. According to the web-side answers, these are not yet configured and the endpoint returns 404 until they are. With Play App Signing, use the **app-signing** certificate for installed store builds. The upload certificate alone does not associate Play-signed builds. The server generates a statement equivalent to:

```json
[
  {
    "relation": ["delegate_permission/common.handle_all_urls"],
    "target": {
      "namespace": "android_app",
      "package_name": "com.herbertindustries.alex311",
      "sha256_cert_fingerprints": ["ACTUAL:SHA256:FINGERPRINT"]
    }
  }
]
```

Both hosts must serve identical JSON over HTTPS without authentication or redirects. The Android manifest narrows the server's `handle_all_urls` grant. Add a developer debug fingerprint only as a deliberate maintainer choice; CI runners and contributors have their own debug certificates. No signing key or certificate association is published by this repository's CI.

After the web maintainer sets the association configuration, verify it with:

```sh
curl -i https://alex311-reborn.com/.well-known/assetlinks.json
curl -i https://alex311visibility.me/.well-known/assetlinks.json
adb shell pm verify-app-links --re-verify com.herbertindustries.alex311
adb shell pm get-app-links com.herbertindustries.alex311
```

The `pm` verification commands above require Android 12+. Until domain verification is configured, Android may keep HTTPS links in the browser. Email code entry, pasted links, and the website's **Open in the app** button remain available. The website button uses `com.herbertindustries.alex311-reborn://signin` with the original encoded query; the router decodes the sign-in code once. The custom scheme also handles the case, report, and My Issues routes and does not claim the City's `alex311://` scheme.

Test custom-scheme routing with:

```sh
adb shell am start -a android.intent.action.VIEW -d 'com.herbertindustries.alex311-reborn://r/26-00039674'
adb shell am start -a android.intent.action.VIEW -d 'com.herbertindustries.alex311-reborn://report'
adb shell am start -a android.intent.action.VIEW -d 'com.herbertindustries.alex311-reborn://my'
adb shell am start -a android.intent.action.VIEW -d 'com.herbertindustries.alex311-reborn://signin'
```

## Notifications

The backend's device registration is APNs-specific. This Android app uses **WorkManager polling**, every three hours subject to Android scheduling, with a local notification on a known followed case's status change. It requests notification permission in context. It creates no background location service and does not request background location access.

These are not immediate FCM push notifications. To add those later, extend the backend's device schema and sender for FCM, configure an Android Firebase app and service credentials on the server, then implement client token registration/rotation/unregistration. Do not send an FCM token to the current APNs-only endpoint.

## Local backend

In a debug build, open Account → Developer server and set `http://10.0.2.2:8311` for the standard emulator, or an HTTPS development origin. The change signs out the old session and applies on relaunch. Debug cleartext is allowed only for `localhost` and `10.0.2.2`; release requires HTTPS. Server-specific registry caches are separate. Release always uses production.

## Release acceptance

Already automated:

- Server form-rule and payload parity for the iOS fixture set.
- API cookie isolation, unauthorized/rate-limit handling, ETags, point decoding, photo upload contract, and redirect rejection.
- Device tests with a local mock backend for submission order, blocking, stale validation, recovery without requeueing, JPEG processing, and private storage.
- Routing tests for both website hosts, encoded sign-in handoff, and browser-only paths, plus installed-manifest matching on the emulator matrix. These do not prove live domain association or provider registration.

Before publishing, test on a real device and a signed-in test account:

- Email code/link and configured Google sign-in; sign-out, revocation, and deletion.
- Precise/approximate/denied location, location services disabled, and outside-city fallback.
- HEIC/rotated/camera-library images from supported devices and process death while filing.
- A deliberately authorized real City submission; confirm the worker's case number and attachments. Automated tests never dispatch City staff.
- Follow/watch/digest/feedback round trips, background notifications with permission on/off, and deep links after certificate association is deployed.
- TalkBack, large fonts, landscape/tablet layouts, offline retry, and current Android devices. Map markers also have a list view for access without gestures.
- Release signing, store artwork/listing/privacy disclosures, and production map-service capacity. Signing keys and store publishing are intentionally outside this repository change.

The Android build targets API 36 in accordance with the [current Play target requirement](https://developer.android.com/google/play/requirements/target-sdk). AGP 8.13.2 / Gradle 8.13 / Java 21 are pinned for a reproducible build.
