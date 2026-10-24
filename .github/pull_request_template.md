## What changes for users?

Describe the problem and resulting behavior. Link any related issue.

## Validation

- [ ] `./gradlew testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease`
- [ ] Device tests pass, or explain what prevented a local run; CI covers API 26 and 36.
- [ ] Tests cover behavior changes, including failure paths.
- [ ] UI changes include a screenshot and accessibility considerations.

## Safety

- [ ] No credentials, signing keys, private resident data, or `local.properties` are included.
- [ ] Tests use a local mock backend and do not file real City requests.
- [ ] Dependency changes include updated lockfiles and a vulnerability audit.

## Notes for review

Call out backend/API, permission, privacy, migration, or compatibility changes.
