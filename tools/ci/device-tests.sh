#!/usr/bin/env bash
# Gerätetests in der CI. Läuft innerhalb von reactivecircus/android-emulator-runner (dort wird das
# Workflow-Skript zeilenweise ausgeführt – deshalb liegt die Logik in dieser Datei).
# Die App-Tests laufen einzeln: fällt der Emulator aus, ist eindeutig, bei welchem Test.
set -u
"$ANDROID_HOME/emulator/emulator" -version 2>&1 | head -3 > emu-version.txt || true
adb shell getprop ro.build.fingerprint >> emu-version.txt 2>&1 || true
adb shell settings put global hide_error_dialogs 1 || true
sleep 30
adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS || true
adb logcat -c || true
adb logcat -v time > full-logcat.txt 2>&1 &
LOGCAT_PID=$!

mkdir -p results-main
timeout 600 ./gradlew :core:database:connectedDebugAndroidTest :core:security:connectedDebugAndroidTest --continue > device.log 2>&1 || echo "TESTS_FAILED core" >> device.log
find core -path '*/outputs/androidTest-results/connected/*' -name '*.xml' -exec cp {} results-main/ \; || true

for c in BackupRestoreTest ReminderInstrumentedTest EndToEndPlanningTest EndToEndProjectTest EndToEndLifeTest EndToEndFinanceTest; do
  echo "=== $c $(date +%T) ===" >> devices.txt
  adb devices >> devices.txt 2>&1
  rm -rf app/build/outputs/androidTest-results/connected
  timeout 600 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class="app.lifeos.$c" > "device-$c.log" 2>&1 || echo "TESTS_FAILED $c" >> device.log
  f=$(find app/build/outputs/androidTest-results/connected -name '*.xml' 2>/dev/null | head -1)
  [ -n "$f" ] && cp "$f" "results-main/app-$c.xml"
done
echo "=== Ende $(date +%T) ===" >> devices.txt
adb devices >> devices.txt 2>&1

timeout 60 adb pull /sdcard/Download/lifeos-shots shots || true
timeout 30 adb logcat -d -b crash > crash.txt 2>&1 || true
kill $LOGCAT_PID 2>/dev/null || true
exit 0
