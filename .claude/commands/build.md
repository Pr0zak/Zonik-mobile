---
description: Build the debug or release APK
allowed-tools: Bash, Read
---

# Build APK

Build the Zonik Android APK.

**Usage:** `/build [debug|release]` (defaults to `debug` if omitted)

**Argument:** $ARGUMENTS

## Steps

1. **Determine build type**: Parse the argument. Default to `debug` if empty.

2. **Run the build**:
   ```bash
   export JAVA_HOME=$HOME/tools/jdk-17.0.12
   export PATH="$JAVA_HOME/bin:$PATH"
   export ANDROID_HOME=$HOME/tools/android-sdk
   ./gradlew assemble{Debug|Release}
   ```

3. **Report result**:
   - If successful: print APK paths and file sizes
   - If failed: print the error output

APK outputs:
- Phone debug: `app/build/outputs/apk/debug/app-debug.apk`
- Phone release: `app/build/outputs/apk/release/app-release.apk`
- Wear debug: `wear/build/outputs/apk/debug/wear-debug.apk`
- Wear release: `wear/build/outputs/apk/release/wear-release.apk`
