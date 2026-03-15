---
description: Show or set the app version
allowed-tools: Bash, Read, Edit
---

# Version Management

Show or update the Zonik app version.

**Usage:**
- `/version` — show current version
- `/version set 0.2.0` — set specific version
- `/version bump patch|minor|major` — bump version

**Argument:** $ARGUMENTS

## Steps

1. **Read current version** from `app/build.gradle.kts`:
   - Extract `versionName` and `versionCode`

2. **If no argument or empty**: just print the current version and version code, then stop.

3. **If argument starts with `set`**: parse the version string after `set` (e.g., `set 1.2.3`), update both `versionName` and increment `versionCode` by 1 in `app/build.gradle.kts`.

4. **If argument starts with `bump`**: parse the bump type (`patch`, `minor`, `major`), calculate new version, update both `versionName` and increment `versionCode` by 1 in `app/build.gradle.kts`.

5. **Print** the old version → new version.

Do NOT commit or build. Just update the file.
