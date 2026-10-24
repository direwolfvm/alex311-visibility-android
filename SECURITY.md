# Security policy

Security fixes target the current `main` branch. The app is under active development; historical builds are not maintained separately.

Report a suspected vulnerability using [GitHub private vulnerability reporting](https://github.com/direwolfvm/alex311-visibility-android/security/advisories/new). Do not post exploitable details, credentials, session cookies, or resident information in a public issue. Include affected versions, reproduction steps, and impact with sensitive information removed. We will acknowledge and triage reports as maintainer availability allows; no response-time guarantee is offered.

Tests must use local mock services. Do not probe the production backend, request sign-in emails for other people, or file real service requests as part of a security test without separate permission. This project does not authorize security testing of the City of Alexandria's systems.

CI runs with read-only permissions for pull-request builds and uses hosted, disposable runners. Signing keys and production credentials are not supplied to contributor workflows. Publishing a dependency graph is limited to trusted `main` builds; it does not deploy an app. Dependency updates require review, and releases are a separate maintainer action.
