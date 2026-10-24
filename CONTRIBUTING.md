# Contributing

Thanks for helping improve this unofficial Alexandria 311 companion. This repository is the native Android client; backend changes belong in the web/backend project. The City of Alexandria does not operate this app.

## Start here

- Open an issue for a substantial feature or architecture change before implementing it.
- Fork the repository and work on a branch. Open a pull request against `main`; direct pushes to `main` are blocked.
- Install Java 21, Android SDK Platform 36, and Build Tools 35.0.0, then open the project in Android Studio. No production credentials, Google OAuth configuration, or Firebase JSON are needed for automated tests.
- Keep changes focused, match existing Kotlin/Compose conventions, and add tests for changed behavior and failure paths. Include screenshots for UI changes. Check TalkBack, large fonts, dark mode, and permission-denied behavior where relevant.

## Tests

```sh
./gradlew testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease
# With an emulator or device connected:
./gradlew connectedDebugAndroidTest
# CI helpers (Python standard library only):
python3 -m unittest discover -s scripts/tests -v
```

CI additionally runs instrumentation tests on **API 26 and API 36**. The report workflow tests use a local MockWebServer. Do not replace it with production endpoints or file real City requests to make a test pass. The 180 registry-parity fixtures protect compatibility with the shared backend; update fixtures deliberately when its contract changes.

The app connects to production for normal browsing. Use Account → Developer server in debug builds for development against a local backend; see [Android setup](docs/ANDROID_SETUP.md). Avoid resident information in screenshots, issues, logs, and fixtures.

## Dependencies and security checks

Direct dependencies are in `app/build.gradle.kts`; transitive versions are committed in `app/gradle.lockfile`. After an intentional dependency change:

```sh
./gradlew :app:dependencies --write-locks
./gradlew testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease
python3 scripts/install-ci-tools.py actionlint gitleaks osv-scanner
.ci-tools/actionlint
.ci-tools/gitleaks dir --redact=100 --no-banner .
.ci-tools/osv-scanner scan source --lockfile=app/gradle.lockfile
python3 scripts/dependency-sbom.py
```

The scanner installer supports Linux x86_64 (including WSL) and Apple Silicon macOS. Other contributors can use the pinned tools' official releases or rely on the GitHub checks. Downloads are verified against committed SHA-256 digests. Do not add blanket scanner exclusions or update a digest without verifying its release. Keep Gradle wrapper changes coordinated with AGP/Kotlin and preserve the wrapper distribution checksum.

Dependabot proposes grouped weekly updates. Runtime/toolchain major upgrades are deliberate migrations; bot PRs still need review and must pass all required checks. If a Dependabot PR changes versions without updating Gradle locks, regenerate and commit the locks on that PR before merging.

## Review and public contributions

`main` requires the stable `CI` and `CodeQL (Java/Kotlin)` checks, an up-to-date PR, resolved discussions, and linear history. A second rule requires a maintainer/code-owner approval for community contributions and dismisses stale approvals when new commits are pushed. The sole administrator can explicitly bypass only that second rule through a PR for their own changes; the checks, PR requirement, and force-push/deletion restrictions still apply.

First-time fork contributors may need a maintainer to approve the Actions run. Fork workflows receive a read-only token, no production secrets, and no release credentials. They run on disposable hosted runners. Never use `pull_request_target` to check out or execute contributor code with elevated privileges.

Do not commit `local.properties`, account tokens, signing keys, `google-services.json`, `.env` files, or private contact/location data. Report vulnerabilities through [SECURITY.md](SECURITY.md), rather than in public issues.

## License

This project is licensed under [MIT](LICENSE). Contributions are provided under that license. Preserve notices for third-party code and assets, and only contribute material you have the right to share. Changes are not automatically published to Google Play; signing and store releases remain maintainer actions.
