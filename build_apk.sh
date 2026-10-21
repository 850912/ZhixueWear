#!/usr/bin/env sh
set -e
chmod +x ./gradlew
./gradlew :app:testDebugUnitTest :app:assembleDebug
echo "APK built at: app/build/outputs/apk/debug/app-debug.apk"
