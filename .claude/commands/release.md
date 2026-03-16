---
description: Build APK, bump version, create GitHub release
allowed-tools: Bash, Read, Edit, Glob, Grep, Agent
---

# Release Process

Perform a full release of the Zonik Android app. The argument specifies the version bump type.

**Usage:** `/release [patch|minor|major]` (defaults to `patch` if omitted)

**Argument:** $ARGUMENTS

## Steps

1. **Determine version bump**: Parse the argument (`patch`, `minor`, or `major`). Default to `patch` if empty.

2. **Read current version** from `app/build.gradle.kts`:
   - Find `versionName` (e.g., `"0.1.0"`)
   - Find `versionCode` (e.g., `1`)

3. **Calculate new version**:
   - `patch`: 0.1.0 → 0.1.1
   - `minor`: 0.1.0 → 0.2.0
   - `major`: 0.1.0 → 1.0.0
   - Increment `versionCode` by 1

4. **Update version** in `app/build.gradle.kts`:
   - Update `versionCode`
   - Update `versionName`

5. **Build the debug APK**:
   ```bash
   export JAVA_HOME=$HOME/tools/jdk-17.0.12
   export PATH="$JAVA_HOME/bin:$PATH"
   export ANDROID_HOME=$HOME/tools/android-sdk
   ./gradlew assembleDebug
   ```
   If the build fails, stop and report the error. Do NOT continue.

6. **Copy APK to release directory**:
   ```bash
   mkdir -p release
   cp app/build/outputs/apk/debug/app-debug.apk release/zonik-v{NEW_VERSION}-debug.apk
   ```

7. **Git commit and push**:
   - Stage: `app/build.gradle.kts` and `release/zonik-v{NEW_VERSION}-debug.apk`
   - Remove old APK from release/ if version changed
   - Commit message: `Release v{NEW_VERSION}`
   - Push to origin

8. **Create or update GitHub release**:
   - First try to create a new release:
     ```bash
     gh release create v{NEW_VERSION} release/zonik-v{NEW_VERSION}-debug.apk \
       --title "v{NEW_VERSION}" \
       --generate-notes
     ```
   - If the release tag already exists, update the existing release APK instead:
     ```bash
     gh release upload v{NEW_VERSION} release/zonik-v{NEW_VERSION}-debug.apk --clobber
     ```

9. **Report**: Print the release URL and new version number.
