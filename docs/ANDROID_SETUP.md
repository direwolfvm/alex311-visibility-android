# Android setup and acceptance

## Backend

The app uses the iOS app's existing public `/api/*` endpoints and authenticated `/submit/api/*` endpoints. It trades a tenant Firebase ID token for the `alex311_portal` cookie; it does not send that cookie on public reads or follow redirects with it. Sessions are stored with AES-GCM using Android Keystore. Backups are disabled for private app data.

`/submit/api/registry/full` requires sign-in on production. Its schema-1 JSON and ETag are cached per server origin; hidden and data-only answers are excluded from report payloads. The server remains authoritative for validation and anti-abuse decisions.

## Google sign-in

Email links and six-digit codes use the backend's Firebase REST configuration and need no embedded Firebase key. Google sign-in uses Android Credential Manager and is hidden until configured:

1. Register an Android OAuth client in the existing Firebase/Identity Platform project for package `com.herbertindustries.alex311` and the signing certificate fingerprints. Include the debug key for local development and the Play App Signing key for store builds.
2. Enable Google in the existing Identity Platform tenant.
3. Put the project's **Web application OAuth client ID** in your user `~/.gradle/gradle.properties`:

   ```properties
   googleWebClientId=YOUR_WEB_CLIENT_ID.apps.googleusercontent.com
   ```

4. Rebuild. The Google button requests a Google ID token, exchanges it through Firebase with `tenantId`, then uses the same backend session endpoint as email.

Client IDs are public configuration. Do not reuse the iOS OAuth client ID, add private OAuth client secrets, or commit signing keys. No `google-services.json` is needed. Native Apple sign-in is not implemented on Android; it would require a web OAuth return flow and backend/provider configuration.

## HTTPS app links

The manifest handles `https://alex311visibility.me/r/*` and `/submit*`, plus the app's custom scheme `com.herbertindustries.alex311-reborn`. It deliberately does not claim the City's `alex311://` scheme.

At implementation time the production `/.well-known/assetlinks.json` returned 404. Publish a file like this on that origin, substituting the actual SHA-256 release/Play signing certificate fingerprint:

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

Serve it as JSON over HTTPS without authentication or redirects. Use a separate entry/fingerprint for debug builds only if you deliberately want that developer configuration. Until domain verification is configured, Android may keep HTTPS links in the browser. Email code entry and pasted sign-in links remain available. Mail-provider wrappers are recursively unwrapped with a depth limit.

Test custom-scheme routing with:

```sh
adb shell am start -a android.intent.action.VIEW -d 'com.herbertindustries.alex311-reborn://r/26-00039674'
adb shell am start -a android.intent.action.VIEW -d 'com.herbertindustries.alex311-reborn://report'
adb shell am start -a android.intent.action.VIEW -d 'com.herbertindustries.alex311-reborn://my'
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

Before publishing, test on a real device and a signed-in test account:

- Email code/link and configured Google sign-in; sign-out, revocation, and deletion.
- Precise/approximate/denied location, location services disabled, and outside-city fallback.
- HEIC/rotated/camera-library images from supported devices and process death while filing.
- A deliberately authorized real City submission; confirm the worker's case number and attachments. Automated tests never dispatch City staff.
- Follow/watch/digest/feedback round trips, background notifications with permission on/off, and deep links after certificate association is deployed.
- TalkBack, large fonts, landscape/tablet layouts, offline retry, and current Android devices. Map markers also have a list view for access without gestures.
- Release signing, store artwork/listing/privacy disclosures, and production map-service capacity. Signing keys and store publishing are intentionally outside this repository change.

The Android build targets API 36 in accordance with the [current Play target requirement](https://developer.android.com/google/play/requirements/target-sdk). AGP 8.13.2 / Gradle 8.13 / Java 21 are pinned for a reproducible build.
