#!/usr/bin/env bash
set -euo pipefail
trap 'echo "::error::Upgrade verification failed at line $LINENO"' ERR

# Public signing certificate verified from the APK of installed Build 176.
# No private signing material is stored here.
expected_cert=a5f1727c55a887fd74bbd44eb4a60068670a448bfd60a07ae8b33877f34b1f67
expected_id=org.fossify.sama.debug
readarray -t candidate_apks < <(find "$1" -type f -name '*.apk')
printf 'Candidate APK count: %s\n' "${#candidate_apks[@]}"
test "${#candidate_apks[@]}" -eq 1
candidate="${candidate_apks[0]}"
build_tools=$(find "$ANDROID_HOME/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -1)
apksigner="$build_tools/apksigner"
aapt="$build_tools/aapt"

"$apksigner" verify --print-certs "$candidate" | tee candidate-certificate.txt
# apksigner versions use either 'Signer #1' or 'V2 Signer' as the prefix.
candidate_cert=$(sed -n 's/^.*certificate SHA-256 digest: //p' candidate-certificate.txt | sort -u)
test -n "$candidate_cert"
printf 'Expected certificate SHA-256: %s\nCandidate certificate SHA-256: %s\n' "$expected_cert" "$candidate_cert"
if [ "$expected_cert" != "$candidate_cert" ]; then
  echo '::error::Signing certificate differs from installed Build 176. Do not distribute this APK.'
  exit 1
fi

candidate_package=$("$aapt" dump badging "$candidate" | sed -n '1p')
candidate_id=$(sed -n "s/^package: name='\([^']*\)'.*/\1/p" <<< "$candidate_package")
candidate_version=$(sed -n "s/.*versionCode='\([0-9]*\)'.*/\1/p" <<< "$candidate_package")
test "$expected_id" = "$candidate_id"
test "$candidate_version" -gt 179
printf 'Package: %s\nCandidate version: %s\nUpgrade compatibility with Build 176: PASS\n' "$candidate_id" "$candidate_version"

