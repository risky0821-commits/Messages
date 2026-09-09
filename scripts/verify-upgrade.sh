#!/usr/bin/env bash
set -euo pipefail
trap 'echo "::error::Upgrade verification failed at line $LINENO"' ERR

readarray -t reference_apks < <(find "$1" -type f -name '*.apk')
readarray -t candidate_apks < <(find "$2" -type f -name '*.apk')
printf 'Reference APK count: %s; candidate APK count: %s\n' "${#reference_apks[@]}" "${#candidate_apks[@]}"
test "${#reference_apks[@]}" -eq 1
test "${#candidate_apks[@]}" -eq 1
reference="${reference_apks[0]}"
candidate="${candidate_apks[0]}"
build_tools=$(find "$ANDROID_HOME/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -1)
apksigner="$build_tools/apksigner"
aapt="$build_tools/aapt"

"$apksigner" verify --print-certs "$reference" | tee reference-certificate.txt
"$apksigner" verify --print-certs "$candidate" | tee candidate-certificate.txt
reference_cert=$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' reference-certificate.txt)
candidate_cert=$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' candidate-certificate.txt)
test -n "$reference_cert"
test -n "$candidate_cert"
printf 'Reference certificate SHA-256: %s\nCandidate certificate SHA-256: %s\n' "$reference_cert" "$candidate_cert"
if [ "$reference_cert" != "$candidate_cert" ]; then
  echo '::error::Signing certificate differs from installed Build 176. Do not distribute this APK.'
  exit 1
fi

reference_package=$("$aapt" dump badging "$reference" | sed -n '1p')
candidate_package=$("$aapt" dump badging "$candidate" | sed -n '1p')
reference_id=$(sed -n "s/^package: name='\([^']*\)'.*/\1/p" <<< "$reference_package")
candidate_id=$(sed -n "s/^package: name='\([^']*\)'.*/\1/p" <<< "$candidate_package")
reference_version=$(sed -n "s/.*versionCode='\([0-9]*\)'.*/\1/p" <<< "$reference_package")
candidate_version=$(sed -n "s/.*versionCode='\([0-9]*\)'.*/\1/p" <<< "$candidate_package")
test -n "$reference_id"
test "$reference_id" = "$candidate_id"
test "$candidate_version" -gt "$reference_version"
printf 'Package: %s\nReference version: %s\nCandidate version: %s\nUpgrade compatibility: PASS\n' "$candidate_id" "$reference_version" "$candidate_version"

