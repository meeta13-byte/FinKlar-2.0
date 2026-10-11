#!/usr/bin/env bash
# Verifies Firebase Phone Auth is working end to end.
#
#   1. starts a clean logcat capture
#   2. launches the app
#   3. prints only Firebase/Auth relevant lines
#   4. after you finish entering the code, dumps the stored prefs to confirm the uid
#
# Usage:  ./verify_otp.sh start    # begin capturing + launch app
#         ./verify_otp.sh check    # after sign-in: show Firebase logs + stored uid

set -uo pipefail

ADB="$HOME/Library/Android/sdk/platform-tools/adb"
PKG="com.example.antigravityfinance"
PREF="finance_app_prefs"

case "${1:-start}" in
start)
  "$ADB" devices | grep -qE "device$" || { echo "No device. Start an emulator or plug in a phone."; exit 1; }
  echo ">> clearing old log"
  "$ADB" logcat -c
  echo ">> installing"
  "$ADB" install -r app/build/outputs/apk/debug/app-debug.apk | tail -2
  echo ">> launching"
  "$ADB" shell am start -n "$PKG/.MainActivity" >/dev/null
  echo
  echo "Now enter your phone number and the code from the Firebase console."
  echo "When the app reaches the dashboard, run:  $0 check"
  ;;

check)
  echo "===== Firebase / Auth log lines ====="
  "$ADB" logcat -d -v brief \
    | grep -iE "firebase|phoneauth|PhoneAuthViewModel|Auth|FATAL|AndroidRuntime" \
    | grep -viE "^D/(Gralloc|OpenGLRenderer|EGL|Choreographer)" \
    | tail -60
  echo
  echo "===== Stored prefs (is a uid saved?) ====="
  "$ADB" shell "run-as $PKG cat shared_prefs/$PREF.xml" 2>/dev/null \
    | sed 's/></>\n</g' \
    | grep -E "registered_uid|user_registered|registered_phone" \
    || echo "(no prefs yet - not signed in, or app is not debuggable)"
  ;;

*)
  echo "usage: $0 {start|check}"; exit 1;;
esac