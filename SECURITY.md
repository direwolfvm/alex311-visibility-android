# Security policy

Security fixes target the current `main` branch. The app is under active development; historical builds are not maintained separately.

Report a suspected vulnerability using [GitHub private vulnerability reporting](https://github.com/direwolfvm/alex311-visibility-android/security/advisories/new). Do not post exploitable details, credentials, session cookies, or resident information in a public issue. Include affected versions, reproduction steps, and impact with sensitive information removed. We will acknowledge and triage reports as maintainer availability allows; no response-time guarantee is offered.

Tests must use local mock services. Do not probe the production backend, request sign-in emails for other people, or file real service requests as part of a security test without separate permission. This project does not authorize security testing of the City of Alexandria's systems.

CI runs with read-only permissions for pull-request builds and uses hosted, disposable runners. Signing keys and production credentials are not supplied to contributor workflows. Publishing a dependency graph is limited to trusted `main` builds; it does not deploy an app. Dependency updates require review, and releases are a separate maintainer action.

## Transport security

Production requests use HTTPS with the platform's certificate trust and hostname verification. The app does not install a permissive trust manager or hostname verifier. Release builds prohibit cleartext traffic; debug builds allow it only for local mock servers on `localhost` and `10.0.2.2`.

The OpenStreetMap tile client uses a separate OkHttp client without session cookies or authorization headers. It deliberately uses normal HTTPS validation rather than certificate pinning: this project does not control the tile provider's certificates or their rotation. [Android's TLS guidance](https://developer.android.com/privacy-and-security/security-ssl#Pinning) recommends against certificate pinning because certificate or CA changes can otherwise break installed apps. The CodeQL missing-pinning advisory for this public tile request is a documented design decision; no global scanning suppression is applied.
