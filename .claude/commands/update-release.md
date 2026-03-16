---
description: Rebuild APK and update the current GitHub release
allowed-tools: Bash, Read, Glob
---

# Update Release

Rebuild the APK and update the current GitHub release without bumping the version. Use this for hotfixes.

**Usage:** `/update-release`

## Steps

1. **Read current version** from `app/build.gradle.kts` — extract `versionName`.

2. **Build the debug APK**:
   ```bash
   export JAVA_HOME=$HOME/tools/jdk-17.0.12
   export PATH="$JAVA_HOME/bin:$PATH"
   export ANDROID_HOME=$HOME/tools/android-sdk
   ./gradlew assembleDebug
   ```
   If the build fails, stop and report the error.

3. **Copy APK to release directory**:
   ```bash
   cp app/build/outputs/apk/debug/app-debug.apk release/zonik-v{VERSION}-debug.apk
   ```

4. **Commit and push** the updated APK (only if changed):
   ```bash
   git add release/
   git commit -m "Update APK for v{VERSION}"
   git push
   ```

5. **Update GitHub release** with the new APK:
   ```bash
   gh release upload v{VERSION} release/zonik-v{VERSION}-debug.apk --clobber
   ```

6. **Report**: Print confirmation with release URL.
