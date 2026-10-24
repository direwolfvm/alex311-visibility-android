# GitHub checks and repository protection

The CI hardening is adapted from the operator repository's CI/CD and Dependabot playbooks. Android Gradle builds and emulator tests replace web/npm/database/Playwright commands; this repository has no web deployment workflow. Existing rollout approvals for other repositories were not treated as authorization to auto-merge or deploy this app.

## Required checks

| Check | What it enforces |
| --- | --- |
| `CI` | All Android CI jobs must pass. A cancelled/failed required job fails the gate; dependency review is skipped only outside PR events. |
| `CodeQL (Java/Kotlin)` | Kotlin-aware CodeQL extraction through an actual Gradle build, then security-and-quality analysis. |

The aggregate `CI` job depends on:

- **Android checks:** unit tests, debug/release Android lint, debug build, minified unsigned release build, and a CycloneDX SBOM of locked direct/transitive dependencies.
- **Device tests:** instrumentation on the minimum supported API 26 and target API 36, with a local mock backend.
- **Secrets and workflows:** full-history gitleaks, actionlint, and security tests for checksum verification/archive extraction and dependency-inventory generation.
- **Dependency audit:** OSV scan of the committed Gradle lockfile. It works on forks without needing a write token or uploaded graph.
- **Dependency review:** GitHub's additional PR dependency comparison, failing on high/critical advisories known to its graph.

CodeQL runs on PRs, `main` pushes, manual dispatch, and weekly. Dependency review alone does not provide a complete Gradle transitive audit: fork PRs cannot publish dependency snapshots, which is why the lockfile/OSV audit is required independently. A separate `Dependency graph` workflow publishes Gradle metadata only from trusted `main` builds, for Dependabot alerts. It receives `contents: write` only in that job and never runs PR code.

All Actions use full commit SHAs with version comments. Checkout never persists credentials. CI has read-only permissions by default, timeouts, cancellation of superseded runs, short-lived artifacts, and read-only Gradle caches on PRs. The Gradle cache provider is explicitly the basic open-source provider. The Gradle distribution and downloaded scanners have SHA-256 pins. No workflow signs APKs, deploys, approves PRs, or merges dependency updates.

## Rulesets

The intended settings are versioned in:

- [.github/rulesets/main-checks.json](../.github/rulesets/main-checks.json): no bypass actors, PRs required, `CI` and `CodeQL (Java/Kotlin)` from the GitHub Actions integration required with strict up-to-date checks, conversations resolved, linear history, and blocked deletion/force pushes.
- [.github/rulesets/main-review.json](../.github/rulesets/main-review.json): one code-owner approval and dismissal of stale reviews; administrators have a PR-only bypass of this review rule. It does not bypass the separate checks ruleset or permit direct pushes.

These files document the policy; committing them alone does **not** activate protection. A repository administrator must apply/update the matching GitHub rulesets. Inspect live rulesets in Settings → Rules → Rulesets. Keep check names synchronized with these JSON files when renaming jobs. The sole maintainer cannot approve their own PR, hence the narrow review-only exception rather than disabling enforcement for admins globally.

Repository settings should keep squash merges enabled, merge commits/rebase merges disabled, merged branches automatically deleted, automatic dependency merging disabled, Actions default permissions read-only, Actions PR approval disabled, first-time fork contributor approval required, Dependabot alerts/security updates enabled, and private vulnerability reporting enabled. GitHub secret scanning and push protection should remain enabled.

## Dependency maintenance

Dependabot checks Gradle and GitHub Actions weekly. Gradle minor/patch updates are grouped and held for five days; security updates are not delayed. Toolchain/runtime majors are ignored by routine version updates and need a coordinated migration. Action upgrades are grouped, including major updates, and remain SHA-pinned. Scanner versions/checksums in `config/ci-tools.json` are reviewed manually.

Review the lockfile and SBOM with dependency changes. Fix vulnerable packages or document a narrowly scoped, justified exception; do not disable the audit merely to obtain a green check. No automated exception has been added.

## Publishing this initial setup

The initial pull request contains both the Android port and its CI because the remote repository began with only a README. The workflows run on that PR. After it is reviewed and merged, `main` gains the contributor files and its trusted dependency-graph workflow. Repository-level rulesets are independent of the PR's merge and can already protect the existing branch.
